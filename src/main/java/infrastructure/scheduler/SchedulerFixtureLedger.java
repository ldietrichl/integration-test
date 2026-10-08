package infrastructure.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import steps.rest.scheduler.SchedulerSteps;

/** Per-thread registration BEFORE writes; persistent non-secret recovery evidence survives normal failures. */
public final class SchedulerFixtureLedger {
    private static final ThreadLocal<Map<String, Entry>> ENTRIES = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final String RUN_ID = "SCHRUN" + UUID.randomUUID().toString().replace("-", "");
    private SchedulerFixtureLedger() { }

    public static final class Entry {
        public final String owner;
        public final long objectId;
        private final Path journal;
        private final String environment;
        private final Set<Long> responseIds = new TreeSet<>();
        private final Set<Long> observedIds = new TreeSet<>();
        private final String registeredAt = Instant.now().toString();
        private Entry(String owner, long objectId, String environment) {
            this.owner = owner;
            this.objectId = objectId;
            this.environment = environment;
            journal = Path.of(System.getProperty("scheduler.fixture.journal.directory",
                    "build/scheduler-fixture-journal")).toAbsolutePath().normalize()
                    .resolve(environment).resolve(owner + ".json");
        }
        public boolean reservedObject(Object value) {
            if (!(value instanceof Number)) return false;
            long id = ((Number) value).longValue();
            return id == objectId || id == objectId + 1;
        }
        public boolean ownsAction(Map<String, Object> row) {
            Object name = row.get("object_name");
            if (name instanceof String text && text.startsWith(owner)) return true;
            // A missing name is accepted only with TWO independent witnesses:
            // the create response task id AND our pre-reserved request object id.
            return (name == null || name.toString().isBlank())
                    && responseIds.contains(number(row.get("id")))
                    && reservedObject(row.get("object_id"));
        }
        /** Response IDs remain visible even when a service job already removed every action. */
        public Set<Long> knownTaskIds() {
            Set<Long> ids = new TreeSet<>(responseIds);
            ids.addAll(observedIds);
            return Collections.unmodifiableSet(ids);
        }
        public enum Disposition { OWNED, FOREIGN, UNCONFIRMED }
        /** A returned ID is an observation, not proof that an otherwise unmarked parent is ours. */
        public Disposition disposition(long taskId, List<Map<String, Object>> rows) {
            if (taskId <= 0 || rows.isEmpty() || rows.stream().anyMatch(row -> number(row.get("id")) != taskId))
                throw new IllegalArgumentException("A nonempty, single-task observation is required");
            boolean owned = rows.stream().allMatch(row -> row.get("action_id") == null
                    ? observedIds.contains(taskId) : ownsAction(row));
            if (owned) return Disposition.OWNED;
            boolean suspicious = knownTaskIds().contains(taskId) || rows.stream().anyMatch(row ->
                    String.valueOf(row.get("object_name")).startsWith(owner)
                    || (row.get("object_name") == null || row.get("object_name").toString().isBlank())
                    && reservedObject(row.get("object_id")));
            return suspicious ? Disposition.UNCONFIRMED : Disposition.FOREIGN;
        }
        public Set<Long> blockingIds(Map<Long, List<Map<String, Object>>> groups) {
            Set<Long> result = new TreeSet<>();
            groups.forEach((id, rows) -> { if (disposition(id, rows) != Disposition.FOREIGN) result.add(id); });
            return Collections.unmodifiableSet(result);
        }
        /** Unknown orphans never enter this statement. Mixed-owner tasks are preserved as a whole. */
        public String cleanupSql() {
            Set<Long> known = knownTaskIds();
            if (known.isEmpty()) throw new IllegalStateException("No proven task IDs to clean");
            String predicate = ownershipSql("a");
            String orphanProof = observedIds.isEmpty() ? "false" : "l.id IN (" + ids(observedIds) + ")";
            return "WITH locked AS (SELECT id FROM scheduler.task WHERE id IN (" + ids(known) + ") FOR UPDATE),"
                    + " eligible AS (SELECT l.id FROM locked l WHERE (" + orphanProof
                    + " OR EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=l.id AND " + predicate + "))"
                    + " AND NOT EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=l.id AND NOT " + predicate + ")),"
                    + " removed AS (DELETE FROM scheduler.task_action a USING eligible e"
                    + " WHERE a.task_id=e.id RETURNING a.task_id)"
                    + " DELETE FROM scheduler.task t USING eligible e WHERE t.id=e.id"
                    + " AND (SELECT count(*) FROM removed)>=0";
        }
        public void rememberIds(Collection<Long> ids) {
            Set<Long> next = new TreeSet<>(observedIds);
            if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0))
                throw new IllegalArgumentException("Positive confirmed IDs required");
            next.addAll(ids);
            if (next.size() > 10010) throw new IllegalArgumentException("Fixture journal size limit");
            observedIds.addAll(next);
            save("OBSERVED", Map.of());
        }
        public void save(String state, Map<String, ?> details) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("owner", owner);
            report.put("runId", RUN_ID);
            report.put("environment", environment);
            report.put("journalFormatVersion", 2);
            report.put("objectIds", List.of(objectId, objectId + 1));
            report.put("registeredAt", registeredAt);
            report.put("updatedAt", Instant.now().toString());
            report.put("state", state);
            report.put("responseTaskIds", responseIds);
            report.put("observedTaskIds", observedIds);
            report.put("details", details);
            try {
                Files.createDirectories(journal.getParent());
                Path temp = Files.createTempFile(journal.getParent(), owner, ".tmp");
                try {
                    Files.writeString(temp, SchedulerSteps.JSON.writeValueAsString(report));
                    try { Files.move(temp, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException unsupported) {
                        Files.move(temp, journal, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temp); }
            } catch (IOException failure) {
                throw new IllegalStateException("FIXTURE_JOURNAL_WRITE_FAILED: " + journal, failure);
            }
        }
        public String ownershipSql(String alias) {
            if (!Set.of("a", "x").contains(alias)) throw new IllegalArgumentException("Fixed SQL alias required");
            String marked = "COALESCE(" + alias + ".object_name LIKE '" + owner + "%',false)";
            if (responseIds.isEmpty()) return "(" + marked + ")";
            return "(" + marked + " OR ((" + alias + ".object_name IS NULL OR btrim(" + alias
                    + ".object_name)='') AND " + alias + ".task_id IN (" + ids(responseIds) + ") AND "
                    + alias + ".object_id IN (" + objectId + "," + (objectId + 1) + ")))";
        }
    }
    public static String root(String marker) {
        if (marker == null || !marker.matches("SCHIT[0-9a-f]{32}[A-Za-z0-9]*"))
            throw new IllegalArgumentException("Exact unique scheduler owner marker required");
        return marker.substring(0, 37);
    }
    public static Entry register(String owner, long objectId, String environment) {
        if (!owner.equals(root(owner)) || objectId <= 0 || objectId == Long.MAX_VALUE
                || !Set.of("dev", "ift", "lt").contains(environment))
            throw new IllegalArgumentException("Bounded owner/object/environment required");
        if (ENTRIES.get().containsKey(owner)) throw new IllegalStateException("FIXTURE_ALREADY_REGISTERED");
        if (ENTRIES.get().values().stream().anyMatch(existing ->
                existing.reservedObject(objectId) || existing.reservedObject(objectId + 1)))
            throw new IllegalStateException("FIXTURE_OBJECT_RANGE_OVERLAP");
        Entry entry = new Entry(owner, objectId, environment);
        entry.save("REGISTERED", Map.of("writesStarted", false));
        ENTRIES.get().put(owner, entry);
        return entry;
    }
    public static Entry require(String owner, long objectId) {
        Entry entry = ENTRIES.get().get(root(owner));
        if (entry == null || entry.objectId != objectId)
            throw new IllegalStateException("FIXTURE_NOT_REGISTERED: no cleanup or fixture mutation allowed");
        return entry;
    }
    public static Entry requireMarker(String marker) {
        Entry entry = ENTRIES.get().get(root(marker));
        if (entry == null) throw new IllegalStateException("FIXTURE_NOT_REGISTERED");
        return entry;
    }
    public static void observeCreate(Object request, long taskId) {
        if (taskId <= 0 || ENTRIES.get().isEmpty()) return;
        JsonNode body = SchedulerSteps.JSON.valueToTree(request);
        List<JsonNode> actions = new ArrayList<>();
        if (body.path("actions").isArray()) body.path("actions").forEach(actions::add);
        else if (body.path("action").isObject()) actions.add(body.path("action"));
        for (Entry entry : ENTRIES.get().values()) {
            if (actions.stream().anyMatch(a -> (a.path("objectId").isIntegralNumber()
                    && entry.reservedObject(a.path("objectId").longValue()))
                    || a.path("objectName").asText("").startsWith(entry.owner))) {
                entry.responseIds.add(taskId);
                entry.save("CREATE_RESPONSE_OBSERVED", Map.of());
            }
        }
    }
    public static String ids(Collection<Long> values) {
        if (values.isEmpty() || values.size() > 10010 || values.stream().anyMatch(v -> v == null || v <= 0))
            throw new IllegalArgumentException("Bounded positive task ids required");
        return values.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    private static long number(Object value) { return value instanceof Number ? ((Number) value).longValue() : -1; }
    public static void clear() { ENTRIES.remove(); }
}
