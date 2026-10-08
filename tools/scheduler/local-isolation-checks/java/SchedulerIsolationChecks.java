package scheduler.localchecks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import infrastructure.kubernetes.SchedulerRunLease;
import infrastructure.scheduler.SchedulerFixtureLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import steps.rest.scheduler.SchedulerSteps;

/** In-memory CAS and ownership checks; no framework environment, DB, HTTP or cluster is started. */
public final class SchedulerIsolationChecks {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void fails(Runnable action, String label) {
        boolean failed = false;
        try { action.run(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, label);
    }
    private static final class Store {
        ObjectNode state = SchedulerSteps.JSON.createObjectNode();
        int patches;
        boolean rejectNext;
        Store() { state.putObject("metadata").put("uid", "configmap-uid").put("resourceVersion", "1"); }
        JsonNode read() { return state.deepCopy(); }
        void patch(String raw) {
            if (rejectNext) { rejectNext = false; throw new IllegalStateException("CAS conflict"); }
            try {
                JsonNode operations = SchedulerSteps.JSON.readTree(raw);
                ObjectNode next = state.deepCopy();
                for (JsonNode op : operations) {
                    String path = op.path("path").asText();
                    if ("test".equals(op.path("op").asText())) {
                        if (!next.at(path).equals(op.path("value"))) throw new IllegalStateException("CAS conflict");
                    } else {
                        int slash = path.lastIndexOf('/');
                        ObjectNode parent = (ObjectNode) next.at(path.substring(0, slash));
                        String key = path.substring(slash + 1).replace("~1", "/").replace("~0", "~");
                        if ("remove".equals(op.path("op").asText())) parent.remove(key);
                        else parent.set(key, op.path("value").deepCopy());
                    }
                }
                ((ObjectNode)next.path("metadata")).put("resourceVersion", Integer.toString(++patches + 1));
                state = next;
            } catch (java.io.IOException malformed) { throw new IllegalStateException(malformed); }
        }
    }
    public static void main(String[] args) throws Exception {
        Store store = new Store();
        SchedulerRunLease first = new SchedulerRunLease(store::read, store::patch);
        first.acquire();
        check(first.acquired(), "lease acquired");
        first.requireHeld(); checks++;
        int before = store.patches;
        SchedulerRunLease second = new SchedulerRunLease(store::read, store::patch);
        fails(second::acquire, "parallel run refused");
        second.close();
        check(store.patches == before, "busy run performs no writes");
        first.close();
        check(store.state.at("/metadata/annotations").isMissingNode(), "original absent annotations restored");
        first.close();
        check(store.patches == before + 1, "release idempotent");

        store = new Store();
        ((ObjectNode)store.state.path("metadata")).putObject("annotations").put("external", "keep");
        SchedulerRunLease lease = new SchedulerRunLease(store::read, store::patch);
        lease.acquire();
        ((ObjectNode)store.state.at("/metadata/annotations")).put("external-new", "preserve");
        lease.close();
        check("keep".equals(store.state.at("/metadata/annotations/external").asText()), "pre-existing annotation preserved");
        check("preserve".equals(store.state.at("/metadata/annotations/external-new").asText()), "concurrent unrelated annotation preserved");

        store = new Store();
        lease = new SchedulerRunLease(store::read, store::patch);
        lease.acquire();
        ((ObjectNode)store.state.at("/metadata/annotations")).put(SchedulerRunLease.KEY, "another-run");
        fails(lease::requireHeld, "lease theft detected");
        before = store.patches;
        fails(lease::close, "another run cannot be released");
        check(store.patches == before, "conflicting owner preserved");

        store = new Store();
        store.rejectNext = true;
        lease = new SchedulerRunLease(store::read, store::patch);
        fails(lease::acquire, "CAS conflict propagated");
        lease.close();
        check(store.patches == 0, "failed acquisition has no mutation");

        Path journal = Files.createTempDirectory("scheduler-isolation-checks-");
        System.setProperty("scheduler.fixture.journal.directory", journal.toString());
        String owner = "SCHIT" + "a".repeat(32);
        String foreign = "SCHIT" + "b".repeat(32);
        var entry = SchedulerFixtureLedger.register(owner, 900001L, "ift");
        Map<String,Object> marked = new HashMap<>(Map.of("id", 10L, "object_id", 900001L, "object_name", owner + "F1"));
        check(entry.ownsAction(marked), "own marker accepted");
        var other = new HashMap<>(marked);
        other.put("object_name", foreign + "F1");
        check(!entry.ownsAction(other), "matching object id is NOT ownership");
        var unnamed = new HashMap<>(marked);
        unnamed.put("object_name", null);
        check(!entry.ownsAction(unnamed), "unmarked row without create response refused");
        SchedulerFixtureLedger.observeCreate(Map.of("actions", List.of(Map.of("objectId", 900001L))), 10L);
        check(entry.ownsAction(unnamed), "unnamed row requires response id AND reserved object");
        unnamed.put("object_id", 900099L);
        check(!entry.ownsAction(unnamed), "response id alone is NOT ownership");
        check(!List.of(marked, other).stream().allMatch(entry::ownsAction), "mixed-owner parent protected");
        check(entry.ownershipSql("a").contains("COALESCE("), "SQL null names cannot bypass NOT EXISTS");
        check(entry.ownershipSql("a").contains("a.task_id IN (10)"), "unnamed SQL clause bound to captured task id");
        check(!entry.ownershipSql("a").contains(foreign), "no foreign marker in delete predicate");
        fails(() -> SchedulerFixtureLedger.require(owner, 1L), "wrong object namespace refused");
        check(SchedulerFixtureLedger.root(owner + "F1").equals(owner), "exact UUID root");
        entry.save("CLEANED", Map.of("remainingActions", 0));
        String report = Files.readString(journal.resolve("ift").resolve(owner + ".json"));
        check(report.contains("\"state\":\"CLEANED\""), "durable cleanup receipt");
        check(!report.contains("object_name") && !report.contains("password"), "journal contains no action values or credentials");
        SchedulerFixtureLedger.clear();
        fails(() -> SchedulerFixtureLedger.require(owner, 900001L), "closed scenario cannot clean later data");
        // Only these explicitly created local check files are removed.
        Files.delete(journal.resolve("ift").resolve(owner + ".json"));
        Files.delete(journal.resolve("ift"));
        Files.delete(journal);
        System.clearProperty("scheduler.fixture.journal.directory");
        System.out.println("IsolationChecksPassed=" + checks);
        System.out.println("CorporateTestsExecuted=0; NetworkDispatches=0; DatabaseConnections=0");
    }
}
