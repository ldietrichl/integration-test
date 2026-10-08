package ru.sber.qa.experiments.EXPLAB_2972;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.environment.special.EnvironmentConfigWithStatusChange2972;
import io.perfeccionista.framework.Environment;
import steps.rest.experiments.v2.*;
import steps.db.experiments.v2.StatusChange2972DbSteps;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual requests and assertions for the 16 formerly operator-only scenarios. */
final class StatusChange2972ControlledScenarios {
    static void run(StatusChange2972Steps c, int id) throws Exception {
        if (id == 42) {
            v1ConfigActions(c);
            return;
        }
        if (id == 43 || id == 44) {
            retryAfterFault(c,id);
            return;
        }
        if (id == 45) {
            requestSources(c);
            return;
        }
        try (var control = new StatusChange2972ControlSteps(c)) {
            control.open();
            switch (id) {
                case 6,12,30 -> scheduling(c,control,id);
                case 9 -> clockBoundary(c,control);
                case 17 -> concurrentAgreement(c,control);
                case 35 -> deletePlanned(c,control);
                case 39,40,41 -> configJobs(c,control,id);
                case 54 -> audit(c,control);
                case 55 -> monitoring(c,control);
                case 60 -> legacy(c,control);
                default -> throw new IllegalArgumentException("Not a controlled scenario: " + id);
            }
        }
    }

    private static List<JsonNode> list(JsonNode array) {
        assertTrue(array.isArray(),"Observation must be an array: " + array);
        List<JsonNode> values=new ArrayList<>();array.forEach(values::add);return values;
    }
    private static List<JsonNode> tasks(JsonNode observation,long exp) {
        return list(observation.path("tasks")).stream().filter(t -> list(t.path("actions")).stream()
                .anyMatch(a -> a.path("objectId").asLong()==exp)).toList();
    }
    private static void task(StatusChange2972Steps c,StatusChange2972ControlSteps control,long exp,String action,long when) throws Exception {
        task(c,control,exp,action,when,c.settings.user("primary"));
    }
    private static void task(StatusChange2972Steps c,StatusChange2972ControlSteps control,long exp,String action,long when,long creator) throws Exception {
        var entity=c.ok(c.get(exp));
        c.awaitAssertion("Exactly one " + action + " scheduler task", () -> {
            var actual=tasks(control.observe(),exp).stream().filter(t -> list(t.path("actions")).stream()
                    .anyMatch(a -> action.equals(a.path("action").asText()))).toList();
            assertEquals(1,actual.size());var t=actual.get(0);
            assertEquals("PLANNED",t.path("status").asText());assertEquals(when,t.path("scheduleDateTime").asLong());
            assertEquals(creator,t.path("createdBy").asLong());
            assertEquals(entity.at("/splittingPoint/code").asText(),t.path("splittingPointCode").asText());
            var actions=list(t.path("actions"));assertEquals(1,actions.size());var a=actions.get(0);
            assertEquals("EXP",a.path("objectType").asText());assertEquals(exp,a.path("objectId").asLong());
            assertEquals(entity.path("name").asText(),a.path("objectName").asText());
        });
    }
    private static void scheduling(StatusChange2972Steps c,StatusChange2972ControlSteps control,int id) throws Exception {
        long exp=c.create(id==6,id!=6,172800,259200);var before=c.ok(c.get(exp));
        c.ok(c.status(exp,"AGREED"));assertEquals("AGREED",c.statusOf(exp));
        if(id==6) {
            assertTrue(c.ok(c.get(exp)).path("autoStart").asBoolean());
            task(c,control,exp,"START",before.path("startDt").asLong());
            try(var db=new StatusChange2972DbSteps(c.settings,"experiment")){assertTrue(db.actions(exp).isEmpty());}
        } else {
            assertTrue(c.ok(c.get(exp)).path("autoStop").asBoolean());
            assertTrue(tasks(control.observe(),exp).isEmpty(),"AGREED must not schedule STOP");
            c.ok(c.status(exp,"STARTING"));task(c,control,exp,"STOP",before.path("endDt").asLong());
            StatusChange2972Scenarios.actions(c,exp,"AGREED","STARTING",4);
            if(id==30) for(boolean flag:List.of(false,true)) {
                long other=c.create(false,false,172800,259200);
                if(flag)control.command("fixture",Map.of("expId",other,"autoStopNull",true));
                c.ok(c.status(other,"AGREED"));c.ok(c.status(other,"STARTING"));
                assertTrue(tasks(control.observe(),other).isEmpty(),"Disabled/null autoStop created a task");
            }
        }
    }
    private static void clockBoundary(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        long time=System.currentTimeMillis()+3600000;
        var frozen=control.command("clock",Map.of("epochMillis",time));
        assertEquals(time,frozen.path("serviceEpochMillis").asLong());assertTrue(frozen.path("frozen").asBoolean());
        assertAll("Independent autostart date boundaries", Arrays.stream(new long[]{-1,0,1})
                .<org.junit.jupiter.api.function.Executable>mapToObj(offset -> () -> {
            long exp=c.create(true,false,172800,259200);
            control.command("fixture",Map.of("expId",exp,"startDt",time+offset,"endDt",time+86400000));
            assertEquals(time+offset,c.ok(c.get(exp)).path("startDt").asLong());
            long clockReadsBefore=control.command("clock-state",Map.of()).path("clockReads").asLong();
            c.ok(c.status(exp,"AGREED"));
            var proof=control.command("clock-state",Map.of());
            assertEquals(time,proof.path("serviceEpochMillis").asLong());assertTrue(proof.path("frozen").asBoolean());
            assertTrue(proof.path("clockReads").asLong()>clockReadsBefore,
                    "Service clock hook was not used by the status request; boundary precondition is unverified");
            assertEquals(offset>=0,c.ok(c.get(exp)).path("autoStart").asBoolean());
            if(offset<0)assertTrue(tasks(control.observe(),exp).isEmpty());else task(c,control,exp,"START",time+offset);
        }));
    }
    private static void concurrentAgreement(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        assertNotEquals(c.settings.user("primary"),c.settings.user("approver"));
        long exp=c.create(true,false,172800,259200);long start=c.ok(c.get(exp)).path("startDt").asLong();
        assertTrue(control.command("read-barrier",Map.of("expId",exp,"participants",2)).path("armed").asBoolean());
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        List<Future<StatusChange2972Steps.Reply>> futures=new ArrayList<>();
        try {
            for(String role:List.of("primary","approver"))futures.add(executor.submit(() -> {
                var environment=Environment.createForCurrentThread(EnvironmentConfigWithStatusChange2972.class,"TP-17 " + role);
                try {
                    environment.init().beforeTest();var worker=new StatusChange2972Steps(c.settings,17);
                    ready.countDown();assertTrue(go.await(c.settings.timeout(),TimeUnit.SECONDS));
                    return worker.status(exp,"AGREED",role,UUID.randomUUID().toString());
                } finally {environment.afterTest().shutdown().removeForCurrentThread();}
            }));
            assertTrue(ready.await(c.settings.timeout(),TimeUnit.SECONDS));go.countDown();
            List<StatusChange2972Steps.Reply> replies=new ArrayList<>();
            for(var f:futures)replies.add(f.get(c.settings.timeout()+10,TimeUnit.SECONDS));
            List<Integer> codes=replies.stream().map(StatusChange2972Steps.Reply::code).toList();
            // The contract does not prescribe which concurrent HTTP request must lose.
            // Verify the actual persisted agreement and its single scheduler side effect.
            var barrier=control.command("barrier-state",Map.of("expId",exp));
            var persisted=c.ok(c.get(exp));
            long agreementActor=persisted.at("/status/statusAgreementChangedBy").asLong();
            c.record("Concurrent agreement outcomes",Map.of("httpStatuses",codes,
                    "persistedStatus",persisted.path("status"),"agreementActor",agreementActor,
                    "barrier",barrier));
            assertAll("Concurrent agreement and scheduler consistency",
                    ()->assertTrue(codes.stream().allMatch(code -> code==200 || (code>=400 && code<500)),
                            "Each request must complete with success or a client rejection: " + codes),
                    ()->assertTrue(codes.stream().anyMatch(code -> code==200),
                            "At least one valid concurrent agreement must succeed: " + codes),
                    ()->assertEquals("AGREED",persisted.at("/status/code").asText()),
                    ()->assertTrue(Set.of(c.settings.user("primary"),c.settings.user("approver")).contains(agreementActor),
                            "Persisted agreement actor must be one of the real concurrent users"),
                    ()->task(c,control,exp,"START",start,agreementActor),
                    ()->assertEquals(2,barrier.path("arrivals").asInt(),
                            "Both requests must reach the real experiment read barrier"),
                    ()->assertFalse(barrier.path("barrierTimedOut").asBoolean(),
                            "Read barrier timeout invalidates the concurrency precondition"));
        } finally {go.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
    }
    private static void deletePlanned(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        long exp=c.fixture("IN_PROGRESS"),other=c.draft();
        c.retain(other); // Its control scheduler task must keep a valid experiment until stand cleanup.
        control.command("seed-tasks",Map.of("expId",exp,"otherExpId",other));
        var before=list(control.observe().path("tasks"));
        assertEquals(Set.of("PLANNED","IN_PROGRESS","COMPLETED"),new HashSet<>(tasks(control.observe(),exp).stream().map(t->t.path("status").asText()).toList()));
        assertFalse(tasks(control.observe(),other).isEmpty());
        c.ok(c.status(exp,"STOPPING"));var observation=control.observe();var after=list(observation.path("tasks"));
        var expected=before.stream().filter(t -> !("PLANNED".equals(t.path("status").asText())&&list(t.path("actions")).stream().anyMatch(a->a.path("objectId").asLong()==exp))).toList();
        assertEquals(expected,after,"Only PLANNED tasks of F may be deleted");
        assertTrue(list(observation.path("requests")).stream().anyMatch(r -> r.path("path").asText().endsWith("/delete-planned")&&r.path("body").path("objectId").asLong()==exp));
    }
    private static List<Map<String,Object>> queue(StatusChange2972Steps c,String request) {
        try(var db=new StatusChange2972DbSteps(c.settings,"configuration")) {
            return db.query("SELECT * FROM configurations.config_request WHERE request_id=?",request);
        }
    }
    private static Map<String,String> start(StatusChange2972Steps c,long exp) throws Exception {
        c.ok(c.status(exp,"STARTING"));Map<String,String> requests=new HashMap<>();
        try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
            for(var row:db.actions(exp))if("STARTING".equals(row.get("exp_target_status")))requests.put((String)row.get("action"),(String)row.get("request_id"));
        }
        assertEquals(4,requests.size());for(String id:requests.values())assertEquals(1,queue(c,id).size());return requests;
    }
    private static void published(JsonNode message,String request,long exp,String action) {
        assertEquals(request,message.path("requestId").asText());assertEquals(exp,message.at("/message/id").asLong());
        assertEquals(action,message.at("/messageInfo/action").asText());assertFalse(message.path("messageId").asText().isBlank());
    }
    private static void configJobs(StatusChange2972Steps c,StatusChange2972ControlSteps control,int id) throws Exception {
        control.jobs("paused");
        try(var kafka=new StatusChange2972KafkaSteps(c)) {
            long exp=c.fixture("AGREED");var requests=start(c,exp);
            kafka.expect(requests.values());
            control.jobs(id==39?"experiment":"general");
            if(id==39) {
                String request=requests.get("EXP");published(kafka.await(request).get(0),request,exp,"START");
                c.awaitAssertion("EXP callback and committed completion",()->{
                    assertTrue(queue(c,request).isEmpty());
                    try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                        var row=db.actions(exp).stream().filter(r->request.equals(r.get("request_id"))).findFirst().orElseThrow(() -> new AssertionError("Expected correlated record is missing"));
                        assertEquals("DONE",row.get("result"));
                    }
                });
                assertTrue(list(control.observe().path("requests")).stream().anyMatch(r->r.path("path").asText().contains("complete-action")&&r.path("body").toString().contains(request)));
            } else if(id==40) {
                String request=requests.get("EXP");
                c.awaitAssertion("General job has attempted EXP",()->assertTrue(queue(c,request).isEmpty()
                        ||list(control.observe().path("configLogs")).stream().map(JsonNode::asText)
                        .anyMatch(log->log.contains(request)&&log.contains("ConfigRequestsJob")&&log.contains("processing:"))));
                if(queue(c,request).isEmpty())assertFalse(kafka.await(request).isEmpty(),"General job discarded EXP without delivery");
                control.jobs("both");published(kafka.await(request).get(0),request,exp,"START");
            } else {
                assertAll("Independent configuration outputs",
                        List.of("SPLITTING","EXP_LINKS").stream()
                                .<org.junit.jupiter.api.function.Executable>map(code -> () -> {
                    String request=requests.get(code);
                    try {
                        assertFalse(kafka.await(request).isEmpty(),"No output for " + code);
                        c.awaitAssertion("Queue completion " + code,()->assertTrue(queue(c,request).isEmpty()));
                    } finally {
                        c.record("Queue after publication wait " + code + " / " + request,queue(c,request));
                    }
                }));
            }
        }
    }
    private static void v1ConfigActions(StatusChange2972Steps parent) {
        List<org.junit.jupiter.api.function.Executable> variants=new ArrayList<>();
        for(String action:List.of("START","STOP")) {
            variants.add(()->{
                // A failed START publication must not prevent testing the independent STOP payload.
                // Every variant has a fresh fixture, requestId and real controller/Kafka session.
                try(var c=new StatusChange2972Steps(parent.settings,42,"TP-42 / "+action);
                    var control=new StatusChange2972ControlSteps(c)) {
                    control.open();control.jobs("paused");
                    try(var kafka=new StatusChange2972KafkaSteps(c)) {
                        long exp=c.draft();c.ok(c.status(exp,"AGREED"));
                        String request=action.equals("START")?start(c,exp).get("V1_EXP_CONFIG"):
                                enqueue(c,exp,"V1_EXP_CONFIG","UI","STOP",false);
                        kafka.expect(List.of(request));control.jobs("general");
                        try {
                            assertAll("V1_EXP_CONFIG "+action+" publication and completion",
                                    ()->assertFalse(kafka.await(request).isEmpty(),"No V1_EXP_CONFIG output for "+action),
                                    ()->{if(action.equals("START"))c.awaitAssertion("Queue completion V1_EXP_CONFIG START",
                                            ()->assertTrue(queue(c,request).isEmpty()));});
                        } finally {
                            c.record("Queue after publication wait V1_EXP_CONFIG " + action + " / " + request,queue(c,request));
                        }
                    }
                }
            });
        }
        assertAll("Independent V1_EXP_CONFIG START and STOP requests",variants);
    }

    private static String enqueue(StatusChange2972Steps c,long exp,String code,String source,String action,boolean legacy) throws Exception {
        String request=UUID.randomUUID().toString();ObjectNode body=StatusChange2972Steps.JSON.createObjectNode()
                .put("requestId",request).put("requestSource",source).put("configCode",code).put("splittingPointCode","MAPPER").put("userId",c.settings.user("primary"));
        ObjectNode params=body.putObject("requestParams").put("expAction",action);
        if(legacy) {
            params.putArray("expIds").add(exp);
            body.remove("splittingPointCode");body.putArray("splittingPoints").add("MAPPER");
        } else params.put("expId",exp);
        c.ok(c.call(true,"POST",legacy?"/api/v2/configurations/config-request":"/api/v2/configurations/request-configs",legacy?body:List.of(body),"primary"));
        assertEquals(1,queue(c,request).size());return request;
    }
    private static void retryAfterFault(StatusChange2972Steps parent,int id) {
        List<String> faults=id==43?List.of("enhance-500","enhance-empty"):List.of("kafka-sync","kafka-async","callback-503");
        assertAll("Independent fault and recovery variants", faults.stream()
                .<org.junit.jupiter.api.function.Executable>map(fault -> () -> {
                    // Each variant owns a new session so a retained failed queue item cannot starve the next fault.
                    try(var c=new StatusChange2972Steps(parent.settings,id,"TP-"+id+" / "+fault);
                        var control=new StatusChange2972ControlSteps(c)) {
                        control.open();retryFaultVariant(c,control,fault);
                    }
                }));
    }
    private static void retryFaultVariant(StatusChange2972Steps c,StatusChange2972ControlSteps control,String fault) throws Exception {
            control.jobs("paused");control.fault(fault);
            try(var kafka=new StatusChange2972KafkaSteps(c)) {
                long exp=c.draft();c.ok(c.status(exp,"AGREED"));String request=start(c,exp).get("EXP");
                control.jobs("experiment");
                var attempt=control.command("await-attempt",Map.of("requestId",request,"expId",exp,"fault",fault));
                assertTrue(attempt.path("observed").asBoolean(),"Fault was not actually exercised");
                assertFalse(queue(c,request).isEmpty(),"Failed request must remain retryable");
                try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                    assertTrue(db.actions(exp).stream().filter(r->request.equals(r.get("request_id"))).noneMatch(r->"DONE".equals(r.get("result"))));
                }
                if(!fault.equals("callback-503"))assertTrue(kafka.poll(request).isEmpty(),"Kafka delivered despite injected delivery/enhance failure");
                control.fault("none");published(kafka.await(request).get(0),request,exp,"START");
                c.awaitAssertion("Recovered EXP request",()->assertTrue(queue(c,request).isEmpty()));
                List<JsonNode> delivered=kafka.poll(request);Set<String> messageIds=new HashSet<>();
                for(var m:delivered){assertEquals(request,m.path("requestId").asText());assertTrue(messageIds.add(m.path("messageId").asText()),"Duplicate messageId");}
            } finally {control.fault("none");}
    }
    private static void requestSources(StatusChange2972Steps parent) {
        List<org.junit.jupiter.api.function.Executable> variants=new ArrayList<>();
        // New request-configs enum: PDF v17 page 3104.
        for(String source:List.of("UI","SCHEDULER","KLEIBER"))for(String action:List.of("START","STOP")) {
            variants.add(() -> {
                try(var c=new StatusChange2972Steps(parent.settings,45,"TP-45 / "+source+" / "+action);
                    var control=new StatusChange2972ControlSteps(c)) {
                    control.open();control.jobs("paused");long exp=c.draft();c.ok(c.status(exp,"AGREED"));
                    try(var kafka=new StatusChange2972KafkaSteps(c)) {
                        String request=enqueue(c,exp,"EXP",source,action,false);
                        assertEquals(source,queue(c,request).get(0).get("request_source"),"Queue must retain the input source");
                        control.jobs("experiment");
                        var message=kafka.await(request).get(0);published(message,request,exp,action);
                        // The EXP publisher contract explicitly normalizes requestSource to UI (PDF v17, page 3115).
                        assertEquals("UI",message.at("/messageInfo/requestSource").asText());
                    }
                }
            });
        }
        // Preserve the formerly positive undocumented values as negative variants.
        for(String source:List.of("KAFKA","CONFIG_SERVICE"))for(String action:List.of("START","STOP")) {
            variants.add(() -> {
                try(var c=new StatusChange2972Steps(parent.settings,45,"TP-45 / invalid " + source + " / " + action);
                    var control=new StatusChange2972ControlSteps(c)) {
                    control.open();control.jobs("paused");long exp=c.draft();c.ok(c.status(exp,"AGREED"));
                    String request=UUID.randomUUID().toString();
                    var before=queue(c,request);c.record("Invalid source queue before " + request,before);
                    assertTrue(before.isEmpty(),"Fresh requestId must have no queue row");
                    var body=StatusChange2972Steps.JSON.createObjectNode().put("requestId",request)
                            .put("requestSource",source).put("configCode","EXP")
                            .put("splittingPointCode","MAPPER").put("userId",c.settings.user("primary"));
                    body.putObject("requestParams").put("expId",exp).put("expAction",action);
                    var reply=c.call(true,"POST","/api/v2/configurations/request-configs",List.of(body),"primary");
                    var after=queue(c,request);c.record("Invalid source queue after " + request,after);
                    var error=reply.json()==null?StatusChange2972Steps.JSON.getNodeFactory().nullNode():reply.json();
                    assertAll("Invalid requestSource " + source + " / " + action + " (PDF v17 pages 3106-3107)",
                            ()->assertEquals(400,reply.code(),"Invalid request must fail before queue insertion"),
                            ()->assertEquals("Некорректный запрос",error.path("message").asText()),
                            ()->assertDoesNotThrow(()->UUID.fromString(error.path("id").asText()),
                                    "Error id must be a UUID; even a blank success body is reported as an assertion failure"),
                            ()->assertEquals(before,after,"Invalid source must not create or change a queue row"));
                }
            });
        }
        assertAll("EXP request source contract and publication combinations",variants);
    }
    private static void audit(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        long exp=c.draft();String request=UUID.randomUUID().toString();c.ok(c.status(exp,"AGREED","primary",request));
        c.awaitAssertion("One logical agreement audit event",()->{
            var events=list(control.observe().path("audit")).stream().filter(n->list(n.path("params")).stream()
                    .anyMatch(p->"id".equals(p.path("name").asText())&&Long.toString(exp).equals(p.path("value").asText())))
                    .filter(n->n.path("changedParams").toString().contains("AGREED")).toList();
            assertEquals(1,events.size(),"Duplicate/missing status audit event");var event=events.get(0);
            var changed=list(event.path("changedParams")).stream().filter(p->"status".equals(p.path("name").asText())).findFirst().orElseThrow(() -> new AssertionError("Expected correlated record is missing"));
            assertEquals("DRAFT",changed.path("oldValue").asText());assertEquals("AGREED",changed.path("value").asText());
            var raw=StatusChange2972Steps.JSON.readTree(event.path("message").asText());assertEquals("expStatusChanged",raw.path("name").asText());
            var parameter=list(raw.path("params")).stream().filter(p->p.path("name").asText().contains("ExpStatusChange")).findFirst().orElseThrow(() -> new AssertionError("Expected correlated record is missing"));
            var context=StatusChange2972Steps.JSON.readTree(parameter.path("value").asText());
            assertEquals(request,context.path("requestId").asText());assertEquals(c.settings.user("primary"),context.at("/user/userId").asLong());
        });
    }
    private static void monitoring(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        assertAll("Monitoring variants", List.of("success","invalid-transition","scheduler-failure").stream()
                .<org.junit.jupiter.api.function.Executable>map(mode -> () -> monitoringMode(c,control,mode)));
    }
    private static void monitoringMode(StatusChange2972Steps c,StatusChange2972ControlSteps control,String mode) throws Exception {
            control.fault(mode.equals("scheduler-failure")?"scheduler-503":"none");
            long exp=c.create(mode.equals("scheduler-failure"),false,172800,259200);long since=System.currentTimeMillis();
            var reply=c.status(exp,mode.equals("invalid-transition")?"IN_PROGRESS":"AGREED");
            if(mode.equals("invalid-transition")) {
                c.rejected(reply);assertEquals("DRAFT",c.statusOf(exp));
                // v2.5 validates the transition before creating a transaction and STARTED event (PDF 2240–2242).
                var events=list(control.observe().path("monitoring")).stream().filter(n->n.path("expId").asLong()==exp).toList();
                assertTrue(events.stream().noneMatch(n->Set.of("STARTED","CHANGED").contains(n.path("result").asText())),
                        "A rejected transition must not be reported as a started or completed change");
                return;
            }
            c.ok(reply);
            c.awaitAssertion("Monitoring semantics " + mode,()->{
                var events=list(control.observe().path("monitoring")).stream().filter(n->n.path("expId").asLong()==exp).toList();
                assertFalse(events.isEmpty());
                var startedEvents=events.stream().filter(n->"STARTED".equals(n.path("result").asText())).toList();
                assertEquals(1,startedEvents.size(),"Exactly one transition must start");var started=startedEvents.get(0);
                assertFalse(started.path("transactionId").asText().isBlank());
                for(var event:events) {
                    assertEquals("EXP_STATUS_CHANGE",event.path("function").asText());assertEquals("experiment-service",event.path("service").asText());
                    assertEquals("MAPPER",event.path("splittingPointCode").asText());
                    assertTrue(event.path("completedTimestamp").asLong()>=since-2000&&event.path("completedTimestamp").asLong()<=System.currentTimeMillis()+2000);
                }
                var changed=events.stream().filter(n->"CHANGED".equals(n.path("result").asText())).toList();
                assertEquals(1,changed.size());assertEquals("AGREED",changed.get(0).path("newStatus").asText());
                // CHANGED has expId/newStatus but no mandatory transactionId in the v2.5 contract (PDF 2268).
                if(mode.equals("scheduler-failure")) {
                    var failure=events.stream().filter(n->"ON_AGREE_AUTOSTART_TASK_NOT_CREATED".equals(n.path("result").asText())).toList();
                    assertEquals(1,failure.size(),"Scheduler failure must have its documented monitoring result");
                    assertEquals(started.path("transactionId"),failure.get(0).path("transactionId"));
                }
            });
    }
    private static void legacy(StatusChange2972Steps c,StatusChange2972ControlSteps control) throws Exception {
        control.jobs("paused");long exp=c.fixture("AGREED");
        long legacyExp=Long.parseLong(c.settings.required("case.60.legacy-fixture-id"));
        try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
            var rows=db.query("SELECT name,version,status FROM experiments.experiment WHERE id=?",legacyExp);
            assertEquals(1,rows.size());var row=rows.get(0);
            assertTrue(row.get("name").toString().startsWith("EXPLAB-2972-"));
            assertEquals(4,((Number)row.get("version")).intValue(),"Prepare a separate valid legacy fixture");
            assertEquals("IN_PROGRESS",row.get("status"));
        }
        // V1_EXP_CONFIG is a documented legacy processor input (PDF v17 pages
        // 3047-3048, 3061-3062). Its acceptance by the old config-request REST
        // endpoint is not documented: prepare an explicit, scoped queue fixture.
        String old=UUID.randomUUID().toString();
        var prepared=control.command("seed-legacy-config",Map.of("expId",legacyExp,"requestId",old,
                "requestSource","UI","expAction","START"));
        assertTrue(prepared.path("prepared").asBoolean());
        assertTrue(prepared.path("preconditionOnly").asBoolean());
        assertEquals(0,prepared.path("processingResultMutations").asInt(-1));
        assertEquals(old,prepared.path("requestId").asText());
        assertEquals(legacyExp,prepared.path("expId").asLong());
        // The helper records the actual internal Feign-compatible V1 source read;
        // this processor test does not claim to validate a V1 user's credentials.
        var legacySource=prepared.path("legacyEnhance");
        assertTrue(legacySource.isArray() && legacySource.size()==1,"Legacy fixture must have real V1 source data");
        assertEquals(legacyExp,legacySource.get(0).path("id").asLong());
        var initial=queue(c,old);c.record("Initial legacy processor queue fixture " + old,initial);
        assertEquals(1,initial.size());assertEquals("V1_EXP_CONFIG",initial.get(0).get("config_code"));
        assertEquals("UI",initial.get(0).get("request_source"));
        var params=StatusChange2972Steps.JSON.readTree(initial.get(0).get("request_params").toString());
        assertEquals(legacyExp,params.path("expIds").path(0).asLong());
        assertEquals("START",params.path("expAction").asText());
        try(var kafka=new StatusChange2972KafkaSteps(c)) {
            String modern=enqueue(c,exp,"EXP","UI","START",false);
            kafka.expect(List.of(old,modern));
            try {
                control.jobs("both");var legacyMessages=kafka.await(old);
                assertFalse(legacyMessages.isEmpty(),"Legacy expIds queue prerequisite produced no config");
                for(var message:legacyMessages) {
                    assertEquals(old,message.path("requestId").asText());
                    assertEquals("UI",message.at("/messageInfo/requestSource").asText(),
                            "Legacy publisher retains the queue source (PDF v17 pages 3057-3058)");
                }
                published(kafka.await(modern).get(0),modern,exp,"START");
                c.awaitAssertion("Both legacy and new queues complete",()->{assertTrue(queue(c,old).isEmpty());assertTrue(queue(c,modern).isEmpty());});
            } finally {
                c.record("Queue after legacy/modern publication wait",Map.of(old,queue(c,old),modern,queue(c,modern)));
            }
        }
    }
}
