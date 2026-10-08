package scheduler.localchecks;

import infrastructure.scheduler.SchedulerFixtureLedger;
import infrastructure.scheduler.SchedulerRegressionPolicy;
import infrastructure.scheduler.SchedulerCleanupFailures;
import infrastructure.scheduler.SchedulerRootReadinessProbe;
import java.nio.file.*;
import java.util.*;
import steps.rest.scheduler.SchedulerSteps;
import static infrastructure.scheduler.SchedulerRegressionPolicy.Phase.*;

public final class SchedulerSharedStandChecks {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    private static void fails(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, label);
    }
    private static Map<String,Object> row(long id, Long action, long object, String name) {
        Map<String,Object> r = new HashMap<>();
        r.put("id",id); r.put("action_id",action); r.put("object_id",object); r.put("object_name",name);
        return r;
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("scheduler-shared-contract-");
        System.setProperty("scheduler.fixture.journal.directory", directory.toString());
        String owner = "SCHIT" + "a".repeat(32);
        try {
            for (var selected : SchedulerRegressionPolicy.Phase.values()) {
                for (var group : List.of(READ_ONLY,FIXTURES,JOBS)) {
                    for (boolean approved : List.of(false,true)) {
                        String expected = selected != FULL && selected != group ? "PHASE_NOT_SELECTED"
                                : group != READ_ONLY && !approved ? "MUTATION_WINDOW_REQUIRED" : null;
                        check(Objects.equals(expected,SchedulerRegressionPolicy.refusal(selected,group,approved)),
                                "phase/window selection " + selected + "/" + group + "/" + approved);
                    }
                }
            }
            check(SchedulerRegressionPolicy.phase("full") == FULL,"full selects all groups");
            fails(() -> SchedulerRegressionPolicy.phase("typo"),"unknown phase refused");
            check(SchedulerRegressionPolicy.group("SchedulerApiRegressionFlowTest","sch135") == READ_ONLY,"health read-only");
            check(SchedulerRegressionPolicy.group("SchedulerApiRegressionFlowTest","sch044") == FIXTURES,"service validation stays selected");
            check(SchedulerRegressionPolicy.group("SchedulerRealStandRegressionFlowTest","cleanupHung") == JOBS,"cleanup job is destructive");
            check(SchedulerRegressionPolicy.group("SchedulerRealStandRegressionFlowTest","deleteCascade") == FIXTURES,"owned deletion fixture group");
            check(SchedulerRegressionPolicy.group("SchedulerWorkloadRegressionFlowTest","pod001") == JOBS,"pod deletion requires window");
            fails(() -> SchedulerRegressionPolicy.group("UnknownTest","test"),"unclassified scenario fails closed");
            check(SchedulerRegressionPolicy.identityRequired("SchedulerApiRegressionFlowTest","sch013"),"MY_TASKS always needs identity");
            check(SchedulerRegressionPolicy.anonymousContractRequired("SchedulerManagedRegressionFlowTest","sch014"),"header-free contract required");
            System.setProperty("scheduler.mutation.window.approved","invalid");
            fails(SchedulerRegressionPolicy::mutationWindowApproved,"invalid approval refused");
            System.clearProperty("scheduler.mutation.window.approved");

            var entry = SchedulerFixtureLedger.register(owner,900001L,"ift");
            check(entry.knownTaskIds().isEmpty(),"registration does not invent task IDs");
            fails(entry::cleanupSql,"empty ledger cannot delete");
            fails(() -> SchedulerFixtureLedger.requireMarker("foreign"),"unregistered marker cannot mutate");
            fails(() -> SchedulerFixtureLedger.register("SCHIT"+"b".repeat(32),900002L,"ift"),"overlapping object ranges rejected");
            SchedulerFixtureLedger.observeCreate(Map.of("actions",List.of(Map.of("objectId",900001L))),10L);
            check(entry.knownTaskIds().equals(Set.of(10L)),"HTTP observation persisted");
            var orphan = row(10L,null,900001L,null);
            check(entry.disposition(10L,List.of(orphan)) == SchedulerFixtureLedger.Entry.Disposition.UNCONFIRMED,
                    "response ID alone cannot own an orphan");
            check(entry.cleanupSql().contains("WHERE (false OR EXISTS"),"orphan requires DB ownership proof");
            var owned = row(10L,100L,900001L,owner+"A");
            var foreign = row(20L,200L,900001L,"other-run");
            check(entry.disposition(10L,List.of(owned)) == SchedulerFixtureLedger.Entry.Disposition.OWNED,"marker proves ownership");
            check(entry.disposition(20L,List.of(foreign)) == SchedulerFixtureLedger.Entry.Disposition.FOREIGN,"same object ID does not own foreign row");
            check(entry.blockingIds(Map.of(20L,List.of(foreign))).isEmpty(),"definitely foreign collision does not fail cleanup");
            check(entry.blockingIds(Map.of(10L,List.of(orphan))).equals(Set.of(10L)),"unconfirmed orphan still blocks");
            var mixed = row(10L,101L,900001L,"external-action");
            check(entry.disposition(10L,List.of(owned,mixed)) == SchedulerFixtureLedger.Entry.Disposition.UNCONFIRMED,"mixed-owner parent preserved");
            check(!entry.ownsAction(mixed),"known parent does not own named foreign action");
            check(entry.ownsAction(row(10L,102L,900001L,null)),"unnamed action requires response and reserved object");
            check(!entry.ownsAction(row(11L,103L,900001L,null)),"object match alone insufficient");
            check(!entry.ownsAction(row(10L,104L,800001L,null)),"response match alone insufficient");
            entry.rememberIds(List.of(10L,11L));
            check(entry.disposition(10L,List.of(orphan)) == SchedulerFixtureLedger.Entry.Disposition.OWNED,"previous DB proof permits orphan cleanup");
            check(entry.cleanupSql().contains("WHERE id IN (10,11) FOR UPDATE"),"cleanup locks bounded IDs");
            check(entry.cleanupSql().contains("l.id IN (10,11)"),"orphan proof uses observed IDs");
            check(entry.cleanupSql().contains("AND NOT EXISTS"),"foreign actions veto deletion");
            check(entry.cleanupSql().contains("(SELECT count(*) FROM removed)>=0"),"child deletion dependency");
            check(entry.cleanupSql().contains("COALESCE("),"NULL ownership explicit");
            fails(() -> entry.rememberIds(List.of(-1L)),"negative IDs rejected");
            fails(() -> entry.ownershipSql("a;drop"),"SQL alias injection rejected");
            fails(() -> SchedulerFixtureLedger.ids(List.of(0L)),"zero SQL ID rejected");
            entry.save("MANUAL_RECOVERY",Map.of("blockingIds",List.of(10L)));
            var journal = SchedulerSteps.JSON.readTree(Files.readString(directory.resolve("ift").resolve(owner+".json")));
            check(journal.path("runId").asText().startsWith("SCHRUN"),"run identity persisted");
            check(journal.path("environment").asText().equals("ift"),"environment persisted");
            check(journal.path("journalFormatVersion").asInt()==2,"journal version");
            check(journal.path("state").asText().equals("MANUAL_RECOVERY"),"unsafe state not success");
            check(journal.path("responseTaskIds").size()==1,"response IDs retained");
            check(journal.path("observedTaskIds").size()==2,"ownership evidence retained");

            AssertionError primary = new AssertionError("service-defect");
            IllegalStateException cleanup = new IllegalStateException("cleanup");
            SchedulerCleanupFailures.run(primary,()->{throw cleanup;});
            check(primary.getSuppressed().length==1 && primary.getSuppressed()[0]==cleanup,"cleanup does not mask service defect");
            fails(()->SchedulerCleanupFailures.run(null,()->{throw cleanup;}),"cleanup-only failure stays failure");
            SchedulerCleanupFailures.run(primary,()->{throw primary;});
            check(primary.getSuppressed().length==1,"self-suppression avoided");
            var ok=SchedulerSteps.JSON.readTree("{\"content\":[]}");
            check(SchedulerRootReadinessProbe.successfulSample(200,ok),"registry readiness body");
            for(int code : new int[]{401,403,404,500})
                fails(()->SchedulerRootReadinessProbe.successfulSample(code,ok),"HTTP defect stays failure "+code);
            System.out.println("SharedStandChecksPassed="+checks);
            System.out.println("CorporateTestsExecuted=0; NetworkDispatches=0; DatabaseConnections=0");
        } finally {
            SchedulerFixtureLedger.clear();
            Files.deleteIfExists(directory.resolve("ift").resolve(owner+".json"));
            Files.deleteIfExists(directory.resolve("ift"));
            Files.deleteIfExists(directory);
            System.clearProperty("scheduler.fixture.journal.directory");
            System.clearProperty("scheduler.mutation.window.approved");
        }
    }
}
