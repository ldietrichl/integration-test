package scheduler.localchecks;

import infrastructure.scheduler.SchedulerFixtureLedger;
import infrastructure.scheduler.SchedulerRootReadinessProbe;
import java.nio.file.*;
import java.util.*;
import steps.rest.scheduler.SchedulerSteps;

/** Pure ownership/response policy checks. No DB, HTTP, framework lifecycle or cluster. */
public final class SchedulerCleanupRestorationChecks {
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
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("scheduler-cleanup-contract-");
        System.setProperty("scheduler.fixture.journal.directory", directory.toString());
        String owner = "SCHIT" + "a".repeat(32);
        try {
            var entry = SchedulerFixtureLedger.register(owner, 900001L, "ift");
            check(entry.knownTaskIds().isEmpty(), "registration does not invent ownership");
            fails(entry::cleanupSql, "empty ledger cannot delete parents");
            SchedulerFixtureLedger.observeCreate(Map.of("actions", List.of(Map.of("objectId", 900001L))), 10L);
            check(entry.knownTaskIds().equals(Set.of(10L)), "create response remains known without actions");
            entry.rememberIds(List.of(11L, 12L));
            check(entry.knownTaskIds().equals(Set.of(10L, 11L, 12L)), "DB and HTTP ownership are merged");
            check(entry.cleanupSql().contains("WHERE id IN (10,11,12) FOR UPDATE"), "parent IDs bounded and locked");
            check(entry.cleanupSql().contains("AND NOT EXISTS (SELECT 1 FROM scheduler.task_action a WHERE a.task_id=l.id AND NOT "
                    + entry.ownershipSql("a") + ")"), "foreign actions block parent deletion");
            check(entry.cleanupSql().contains("WHERE (l.id IN (11,12) OR EXISTS"),
                    "only DB-confirmed orphan IDs bypass a surviving owned action");
            check(entry.cleanupSql().contains("USING eligible e WHERE t.id=e.id"), "parents do not depend on removed action IDs");
            check(entry.cleanupSql().contains("(SELECT count(*) FROM removed)>=0"), "child deletion is an explicit dependency");
            check(!entry.cleanupSql().contains("TRUNCATE"), "no bulk truncate");
            var foreign = Map.<String,Object>of("id", 10L, "object_id", 900001L, "object_name", "foreign");
            check(!entry.ownsAction(foreign), "known parent ID never proves a foreign action");
            var unnamed = new HashMap<String,Object>(foreign);
            unnamed.put("object_name", null);
            check(entry.ownsAction(unnamed), "unnamed action needs response ID and reserved object");
            unnamed.put("id", 11L);
            check(!entry.ownsAction(unnamed), "DB observed ID alone cannot own an unnamed action");
            check(entry.cleanupSql().contains("COALESCE("), "SQL null handling is explicit");
            entry.save("MANUAL_RECOVERY", Map.of("remainingKnownParents", 1));
            var journal = SchedulerSteps.JSON.readTree(Files.readString(directory.resolve("ift").resolve(owner + ".json")));
            check(journal.path("responseTaskIds").size() == 1, "response IDs persisted");
            check(journal.path("observedTaskIds").size() == 2, "DB IDs persisted");
            check("MANUAL_RECOVERY".equals(journal.path("state").asText()), "unconfirmed cleanup cannot be green");
            var ok = SchedulerSteps.JSON.readTree("{\"content\":[]}");
            check(SchedulerRootReadinessProbe.successfulSample(200, ok), "registry JSON accepted");
            for (int status : new int[]{502,503,504})
                check(!SchedulerRootReadinessProbe.successfulSample(status, null), "warming status " + status);
            for (int status : new int[]{301,400,401,403,404,500})
                fails(() -> SchedulerRootReadinessProbe.successfulSample(status, ok), "status stays visible " + status);
            fails(() -> SchedulerRootReadinessProbe.successfulSample(200, null), "missing JSON refused");
            var health = SchedulerSteps.JSON.readTree("{\"status\":\"UP\"}");
            fails(() -> SchedulerRootReadinessProbe.successfulSample(200, health), "actuator is not an ingress registry");
            System.out.println("CleanupRestorationChecksPassed=" + checks);
            System.out.println("CorporateTestsExecuted=0; NetworkDispatches=0; DatabaseConnections=0");
        } finally {
            SchedulerFixtureLedger.clear();
            Files.deleteIfExists(directory.resolve("ift").resolve(owner + ".json"));
            Files.deleteIfExists(directory.resolve("ift"));
            Files.deleteIfExists(directory);
            System.clearProperty("scheduler.fixture.journal.directory");
        }
    }
}
