package steps.db.scheduler;

import ru.sber.qa.services.db.DatabaseClient;
import steps.db.experiments.v2.StatusChange2972DbSteps;
import java.sql.Timestamp;
import java.util.*;
import static io.qameta.allure.Allure.step;

/** Only owned scheduler fixtures can be inserted/deleted; observations reuse Platform V AT DB. */
public final class SchedulerDbSteps {
    private final DatabaseClient client;
    public SchedulerDbSteps(DatabaseClient client) { this.client = client; }
    public List<Map<String,Object>> query(String sql, Object... args) {
        if (!sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT ")) throw new IllegalArgumentException("SELECT only");
        return step("Scheduler: read database", () -> {
            List<Map<String,Object>> rows = new ArrayList<>();
            for (Map<String,Object> source : client.executeSelect(StatusChange2972DbSteps.bind(sql,args)).toSimpleTable()) {
                Map<String,Object> row = new LinkedHashMap<>();
                // Scheduler persists UTC wall time in PostgreSQL timestamp WITHOUT time zone.
                // JDBC interprets it in the test JVM zone; recover the stored wall time first.
                source.forEach((k,v) -> row.put(k.toLowerCase(Locale.ROOT), v instanceof Timestamp
                        ? ((Timestamp)v).toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() : v));
                rows.add(row);
            }
            return rows;
        });
    }
    /** Bounded persisted-value oracle; retains the shared explab connection and SELECT-only path. */
    public Map<Long, Long> registryTaskNumbers(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 20
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Expected 1..20 distinct positive registry ids");
        }
        return step("Scheduler: read persisted task numbers for observed V2 ids", () -> {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            List<Map<String, Object>> rows = query(
                    "SELECT id,task_number FROM scheduler.task WHERE version=1 AND id IN (" + placeholders + ")",
                    ids.toArray());
            Map<Long, Long> numbers = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                long id = new java.math.BigDecimal(row.get("id").toString()).longValueExact();
                if (!ids.contains(id) || numbers.containsKey(id))
                    throw new AssertionError("Unexpected/duplicate id in framework DB result");
                Object value = row.get("task_number");
                numbers.put(id, value == null ? null
                        : new java.math.BigDecimal(value.toString()).longValueExact());
            }
            return Collections.unmodifiableMap(numbers);
        });
    }

    public List<Map<String,Object>> tasks(String owner) {
        checkOwner(owner);
        return query("SELECT t.*,a.object_type,a.object_id,a.object_name,a.action,a.id AS action_id FROM scheduler.task t JOIN scheduler.task_action a ON a.task_id=t.id WHERE a.object_name LIKE ? ORDER BY t.id,a.id",owner+"%");
    }
    public Map<String,Object> task(long id) {
        List<Map<String,Object>> rows = query("SELECT * FROM scheduler.task WHERE id=?",id);
        if (rows.size()!=1) throw new AssertionError("Expected one task " + id + ", got " + rows.size());
        return rows.get(0);
    }
    public long seed(String owner, Integer version, String status, long objectId, String type, String action,
                     long user, long planned, String point) {
        checkOwner(owner);
        infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner);
        if (!Set.of("PLANNED","IN_PROGRESS","COMPLETED","ERROR","NOT_STARTED").contains(status)) throw new IllegalArgumentException("status");
        String sql = "WITH inserted AS (INSERT INTO scheduler.task (version,status,schedule_datetime,created_at,updated_at,created_by,updated_by,splitting_point) VALUES ("+
                (version==null?"NULL":version)+",?,(to_timestamp("+planned+"/1000.0) AT TIME ZONE 'UTC'),(now() AT TIME ZONE 'UTC'),(now() AT TIME ZONE 'UTC'),?,?,?) RETURNING id) " +
                "INSERT INTO scheduler.task_action(task_id,object_type,object_id,object_name,action) SELECT id,?,?,?,? FROM inserted";
        step("Scheduler: create owned DB fixture", () -> client.executeUpdate(StatusChange2972DbSteps.bind(sql,status,user,user,point,type,objectId,owner,action)));
        List<Map<String,Object>> rows = query("SELECT task_id FROM scheduler.task_action WHERE object_name=? ORDER BY id DESC",owner);
        if (rows.isEmpty()) throw new AssertionError("Fixture insertion returned no task");
        long id = ((Number)rows.get(0).get("task_id")).longValue();
        infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner).rememberIds(List.of(id));
        return id;
    }
    public void seedBatch(String owner,int count,Integer version,String status,long objectId,long user,long planned,String point) {
        checkOwner(owner);
        infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner);
        if(count<1||count>10000||!Set.of("PLANNED","COMPLETED").contains(status))throw new IllegalArgumentException("Bounded scheduler batch required");
        String sql="WITH inserted AS (INSERT INTO scheduler.task(version,status,schedule_datetime,created_at,updated_at,created_by,updated_by,splitting_point) SELECT "+
                (version==null?"NULL":version)+",?,(to_timestamp("+planned+"/1000.0) AT TIME ZONE 'UTC'),(now() AT TIME ZONE 'UTC'),(now() AT TIME ZONE 'UTC'),?,?,? FROM generate_series(1,"+count+") RETURNING id) "+
                "INSERT INTO scheduler.task_action(task_id,object_type,object_id,object_name,action) SELECT id,'EXP',?,?||id::text,'START' FROM inserted";
        step("Scheduler: create bounded owned batch",()->client.executeUpdate(StatusChange2972DbSteps.bind(sql,status,user,user,point,objectId,owner)));
        // Capture before a real job can remove actions. A failed read still leaves marker-based cleanup.
        var ids = query("SELECT DISTINCT task_id FROM scheduler.task_action WHERE object_name LIKE ? AND object_id=?",
                owner + "%", objectId).stream().map(row -> ((Number)row.get("task_id")).longValue()).toList();
        infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner).rememberIds(ids);
    }
    public void setOwnedCreatedAt(String owner, long taskId, long utcMillis) {
        checkOwner(owner);
        var ledger = infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner);
        String sql = "UPDATE scheduler.task t SET created_at=(to_timestamp(" + utcMillis + "/1000.0) AT TIME ZONE 'UTC') " +
                "WHERE t.id=? AND EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=t.id AND a.object_name LIKE ?)"
                + " AND NOT EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=t.id AND NOT "
                + ledger.ownershipSql("a") + ")";
        step("Scheduler: set deterministic creation time on owned fixture", () ->
                client.executeUpdate(StatusChange2972DbSteps.bind(sql, taskId, owner + "%")));
        if (!Objects.equals(utcMillis, task(taskId).get("created_at")))
            throw new AssertionError("Owned fixture creation time was not updated");
    }
    public void cleanup(String owner,long objectId) {
        checkOwner(owner);
        var ledger = infrastructure.scheduler.SchedulerFixtureLedger.require(owner, objectId);
        try {
            var knownBefore = ledger.knownTaskIds();
            String knownSelection = knownBefore.isEmpty() ? "" : " OR t.id IN ("
                    + infrastructure.scheduler.SchedulerFixtureLedger.ids(knownBefore) + ")";
            String selection = " FROM scheduler.task t LEFT JOIN scheduler.task_action a ON a.task_id=t.id"
                    + " WHERE (t.id IN (SELECT x.task_id FROM scheduler.task_action x"
                    + " WHERE x.object_name LIKE ? OR x.object_id IN (?,?))" + knownSelection + ")";
            var candidates = query("SELECT t.id,a.id AS action_id,a.object_id,a.object_name" + selection,
                    owner + "%", objectId, objectId + 1);
            var groups = groupCleanupRows(candidates);
            List<Long> owned = groups.entrySet().stream().filter(group ->
                    ledger.disposition(group.getKey(), group.getValue())
                            == infrastructure.scheduler.SchedulerFixtureLedger.Entry.Disposition.OWNED)
                    .map(Map.Entry::getKey).toList();
            ledger.rememberIds(owned);
            var known = ledger.knownTaskIds();
            ledger.save("CLEANUP_STARTED", Map.of("candidateTasks", groups.size(),
                    "provenOwnedTasks", owned.size(), "knownTaskIdsCount", known.size()));
            if (!known.isEmpty())
                step("Scheduler: remove proven owned actions and parents with a live all-action guard",
                        () -> client.executeUpdate(ledger.cleanupSql()));
            var remaining = query("SELECT t.id,a.id AS action_id,a.object_id,a.object_name" + selection,
                    owner + "%", objectId, objectId + 1);
            var remainingGroups = groupCleanupRows(remaining);
            var blocking = ledger.blockingIds(remainingGroups);
            long remainingKnownParents = known.isEmpty() ? 0 : ((Number)query(
                    "SELECT count(*) AS remaining FROM scheduler.task WHERE id IN ("
                            + infrastructure.scheduler.SchedulerFixtureLedger.ids(known)
                            + ")").get(0).get("remaining")).longValue();
            long foreignPreserved = remainingGroups.size() - blocking.size();
            Map<String,Object> summary = Map.of("owner", owner, "candidateTasks", groups.size(),
                    "provenOwnedTasks", owned.size(), "knownTaskIdsCount", known.size(),
                    "remainingKnownParents", remainingKnownParents,
                    "blockingTaskIds", blocking, "foreignCandidateTasksPreserved", foreignPreserved,
                    "remainingCandidateParents", remainingGroups.size());
            boolean cleaned = blocking.isEmpty() && remainingKnownParents == 0;
            ledger.save(cleaned ? "CLEANED" : "MANUAL_RECOVERY", summary);
            // Evidence rendering failure must not recategorize a confirmed cleanup as unsafe.
            try { steps.container.KubernetesTunnelSteps.evidence("Scheduler owned cleanup receipt", summary); }
            catch (RuntimeException | AssertionError evidenceFailure) {
                System.out.println("[scheduler-cleanup] receiptAttachmentUnavailable=true; persistentJournalSaved=true");
            }
            if (!cleaned)
                throw new IllegalStateException("OWNED_CLEANUP_UNCONFIRMED: known/mixed/ambiguous data remains; inspect fixture journal");
        } catch (RuntimeException | Error failure) {
            try { ledger.save("MANUAL_RECOVERY", Map.of("failureType", failure.getClass().getName(),
                    "knownTaskIds", ledger.knownTaskIds(), "instruction", "Re-read rows; never delete by objectId alone")); }
            catch (RuntimeException | Error journalFailure) { failure.addSuppressed(journalFailure); }
            throw failure;
        }
    }
    private static Map<Long,List<Map<String,Object>>> groupCleanupRows(List<Map<String,Object>> rows) {
        Map<Long,List<Map<String,Object>>> groups = new LinkedHashMap<>();
        for (var row : rows)
            groups.computeIfAbsent(((Number)row.get("id")).longValue(), ignored -> new ArrayList<>()).add(row);
        return groups;
    }
    public long clockMillis() {
        List<Map<String, Object>> rows = query(
                "SELECT floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint AS clock_millis");
        if (rows.size() != 1 || !(rows.get(0).get("clock_millis") instanceof Number))
            throw new IllegalStateException("Database clock unavailable");
        return ((Number) rows.get(0).get("clock_millis")).longValue();
    }

    /** Never enable a global scheduler job while another owner's active data is present. */
    public void requireNoForeignRunnableTasks(String owner) {
        checkOwner(owner);
        String predicate = " FROM scheduler.task t WHERE t.status IN ('PLANNED','IN_PROGRESS') AND ("
                + "NOT EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=t.id AND a.object_name LIKE ?) "
                + "OR EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=t.id "
                + "AND (a.object_name IS NULL OR a.object_name NOT LIKE ?)))";
        var rows = query("SELECT count(*) AS foreign_count" + predicate, owner + "%", owner + "%");
        if (rows.size() != 1 || !(rows.get(0).get("foreign_count") instanceof Number))
            throw new IllegalStateException("FOREIGN_RUNNABLE_TASKS_CHECK_INVALID");
        long count = ((Number) rows.get(0).get("foreign_count")).longValue();
        if (count != 0) {
            // Inventory is SELECT-only. If evidence collection fails, the safety refusal still wins.
            try {
                var groups = query("SELECT t.status,t.created_by,count(*) AS task_count,"
                        + "min(t.created_at) AS oldest_created_at,max(t.created_at) AS newest_created_at,"
                        + "min(t.schedule_datetime) AS earliest_schedule,max(t.schedule_datetime) AS latest_schedule,"
                        + "min(extract(epoch FROM ((now() AT TIME ZONE 'UTC')-t.created_at))) AS min_age_seconds,"
                        + "max(extract(epoch FROM ((now() AT TIME ZONE 'UTC')-t.created_at))) AS max_age_seconds"
                        + predicate + " GROUP BY t.status,t.created_by ORDER BY task_count DESC,t.status,t.created_by LIMIT 20",
                        owner + "%", owner + "%");
                var sample = query("SELECT t.id,t.status,t.created_by,t.created_at,t.schedule_datetime"
                        + predicate + " ORDER BY t.id LIMIT 10", owner + "%", owner + "%");
                steps.container.KubernetesTunnelSteps.evidence("Foreign scheduler tasks - read-only safety inventory",
                        Map.of("foreignRunnableCount", count, "currentRunOwner", owner,
                                "belongsExclusivelyToCurrentRun", false, "groupsLimit", 20, "sampleLimit", 10,
                                "groups", groups, "sample", sample, "jobEnabled", false,
                                "boundary", "Owner means numeric created_by, not a verified human identity; no foreign data is modified"));
            } catch (RuntimeException | AssertionError diagnosticFailure) {
                System.out.println("[scheduler-foreign-tasks] inventoryUnavailableType="
                        + diagnosticFailure.getClass().getSimpleName() + "; safetyRefusalPreserved=true");
            }
            throw new IllegalStateException("FOREIGN_RUNNABLE_TASKS: count=" + count
                    + "; no scheduler job was enabled; inspect the read-only safety inventory");
        }
    }

    public void setOwnedExecutionTimes(String owner, long taskId, Long started, Long ended) {
        checkOwner(owner);
        infrastructure.scheduler.SchedulerFixtureLedger.requireMarker(owner);
        String startSql = started == null ? "NULL" : "(to_timestamp(" + started + "/1000.0) AT TIME ZONE 'UTC')";
        String endSql = ended == null ? "NULL" : "(to_timestamp(" + ended + "/1000.0) AT TIME ZONE 'UTC')";
        String sql = "UPDATE scheduler.task t SET start_datetime=" + startSql + ",end_datetime=" + endSql
                + " WHERE t.id=? AND EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=t.id "
                + "AND a.object_name LIKE ?) AND NOT EXISTS (SELECT 1 FROM scheduler.task_action a "
                + "WHERE a.task_id=t.id AND (a.object_name IS NULL OR a.object_name NOT LIKE ?))";
        step("Scheduler: set execution timestamps only on an owned fixture", () ->
                client.executeUpdate(StatusChange2972DbSteps.bind(sql, taskId, owner + "%", owner + "%")));
        Map<String, Object> actual = task(taskId);
        if (!Objects.equals(started, actual.get("start_datetime")) || !Objects.equals(ended, actual.get("end_datetime")))
            throw new IllegalStateException("Owned execution timestamp preparation was not applied");
    }

    public Map<String, Object> awaitStatus(long taskId, String expected, int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600) throw new IllegalArgumentException("Bounded timeout required");
        return step("Scheduler: await persisted status " + expected + " for owned task " + taskId, () -> {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(timeoutSeconds);
            Map<String, Object> last;
            do {
                last = task(taskId);
                if (expected.equals(last.get("status"))) return last;
                if (System.nanoTime() >= deadline) break;
                try { Thread.sleep(1000); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting scheduler task", interrupted);
                }
            } while (true);
            throw new AssertionError("Expected status " + expected + " for task " + taskId
                    + ", observed " + last.get("status") + " within " + timeoutSeconds + "s");
        });
    }

    private static void checkOwner(String owner) {
        if (owner==null || !owner.matches("SCHIT[0-9a-f]{32}[A-Za-z0-9]*")) throw new IllegalArgumentException("Owned fixture marker required");
    }
}
