package ru.sber.qa.experiments.EXPLAB_2972;

import steps.rest.experiments.v2.StatusChange2972Steps;
import steps.db.experiments.v2.StatusChange2972DbSteps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class StatusChange2972Scenarios {
    static final Set<Integer> REGULAR = Set.of(1,4,5,7,16,18,19,20);
    static final Set<Integer> MANAGED = java.util.stream.IntStream.rangeClosed(1,60).boxed()
            .filter(id -> !REGULAR.contains(id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    static void run(StatusChange2972Steps c, int id) throws Exception {
        if (steps.rest.experiments.v2.StatusChange2972ControlSteps.CASES.contains(id)) {
            StatusChange2972ControlledScenarios.run(c,id);
            return;
        }
        switch (id) {
            case 1 -> {
                long exp=c.draft(); var before=c.ok(c.get(exp)); var after=c.ok(c.status(exp,"AGREED")).path("experiment");
                assertEquals("AGREED",after.at("/status/code").asText()); assertEquals("AGREED",c.statusOf(exp));
                assertEquals(before.path("createdBy"),after.path("createdBy"));
                assertEquals(c.settings.user("primary"),after.at("/status/statusAgreementChangedBy").asLong());
                assertEquals(c.settings.user("primary"),after.path("updatedBy").asLong());
            }
            case 2 -> {
                assertNotEquals(c.settings.user("primary"),c.settings.user("approver"),"U1 and U2 must be different");
                long exp=c.draft(); var r=c.ok(c.status(exp,"AGREED","approver",UUID.randomUUID().toString())).path("experiment");
                assertEquals(c.settings.user("primary"),r.path("createdBy").asLong());
                assertEquals(c.settings.user("approver"),r.at("/status/statusAgreementChangedBy").asLong());
                assertEquals(c.settings.user("approver"),r.path("updatedBy").asLong());
                assertEquals(c.settings.user("approver"),r.path("statusChangedBy").asLong());
                assertEquals("AGREED",c.statusOf(exp));
            }
            case 3 -> {
                assertNotEquals(c.settings.user("primary"),c.settings.user("denied"),"Use a separate authenticated user without agreement permission");
                long exp=c.draft();var before=c.ok(c.get(exp));
                c.rejected(c.status(exp,"AGREED","denied",UUID.randomUUID().toString()));var after=c.ok(c.get(exp));
                for(String field:List.of("status","createdBy","updatedBy","updatedDt","statusChangedBy","autoStart","autoStop"))
                    assertEquals(before.get(field),after.get(field),"Rejected agreement changed " + field);
            }
            case 4 -> {
                long exp=c.draft();c.ok(c.status(exp,"AGREEMENT"));assertEquals("AGREEMENT",c.statusOf(exp));c.ok(c.status(exp,"AGREED"));assertEquals("AGREED",c.statusOf(exp));
            }
            case 5 -> {
                long exp=c.draft();var before=c.ok(c.get(exp));c.ok(c.status(exp,"AGREED"));var after=c.ok(c.get(exp));
                for(String key:List.of("id","name","createdBy","createdDt","salt","startDt","endDt","autoStart","autoStop","version","splittingPoint","expTemplate","quantum","metrics","objectSelectConditions","experimentsGroup","relations"))assertEquals(before.get(key),after.get(key),"Unexpected change: "+key);
            }
            case 7 -> { for(Boolean flag:Arrays.asList(false,null)){long exp=c.create(flag,false,172800,259200);c.ok(c.status(exp,"AGREED"));assertFalse(c.ok(c.get(exp)).path("autoStart").asBoolean());} }
            case 8,10,11,13 -> {
                long exp=c.fixture("DRAFT");var before=c.ok(c.get(exp));
                if(id==8||id==11){assertTrue(before.path("autoStart").asBoolean(),"Prepare autoStart=true");assertTrue(before.path("startDt").asLong()<System.currentTimeMillis(),"Prepare expired startDt");}
                if(id!=8){assertTrue(before.path("autoStop").asBoolean(),"Prepare autoStop=true");assertTrue(before.path("endDt").asLong()<System.currentTimeMillis(),"Prepare expired endDt");}
                if(id==13)try(var db=new StatusChange2972DbSteps(c.settings,"experiment")){
                    var row=db.query("SELECT schedule_param FROM experiments.experiment WHERE id=?",exp);assertEquals(1,row.size());
                    assertNotNull(row.get(0).get("schedule_param"),"Prepare scheduleParam");
                    var schedule=StatusChange2972Steps.JSON.readTree(row.get(0).get("schedule_param").toString());
                    assertTrue(schedule.path("repeatPeriod").isIntegralNumber(),"Prepare a valid scheduleParam.repeatPeriod");
                    assertTrue(schedule.path("monthShift").isIntegralNumber(),"Prepare a valid scheduleParam.monthShift");
                    assertTrue(schedule.path("saltTextPart").isTextual(),"Prepare a valid scheduleParam.saltTextPart");
                }
                var r=c.status(exp,"AGREED");
                var accepted=c.ok(r).path("experiment");
                assertEquals("AGREED",accepted.at("/status/code").asText(),"Agreement response must confirm the transition before checking cancellation flags");
                var after=c.ok(c.get(exp));
                assertEquals("AGREED",after.at("/status/code").asText(),"Agreement must be persisted before checking cancellation flags");
                if(id==8||id==11){assertFalse(after.path("autoStart").asBoolean());c.warning(r,"autostartCanceled");}
                if(id!=8){assertFalse(after.path("autoStop").asBoolean());c.warning(r,"autostopCanceled");}
            }
            case 14 -> {long exp=c.fixture("DRAFT");assertTrue(c.ok(c.get(exp)).path("autoStart").asBoolean());c.warning(c.status(exp,"AGREED"),"autostartTaskNotCreated");assertEquals("AGREED",c.statusOf(exp));}
            case 15 -> {
                long exp=c.draft();bulk(c,UUID.randomUUID().toString(),"SCHEDULER",List.of(Map.of("id",exp,"status","AGREED","userId",c.settings.user("primary"))));c.awaitStatus(exp,"AGREED");
                assertEquals(c.settings.user("primary"),c.ok(c.get(exp)).at("/status/statusAgreementChangedBy").asLong());
            }
            case 16 -> {for(boolean same:List.of(true,false)){long exp=c.draft();String request=UUID.randomUUID().toString();c.ok(c.status(exp,"AGREED","primary",request));c.rejected(c.status(exp,"AGREED","primary",same?request:UUID.randomUUID().toString()));assertEquals("AGREED",c.statusOf(exp));}}
            case 18 -> {for(String target:List.of("STARTING","IN_PROGRESS","STOPPING","STOPPED")){long exp=c.draft();c.rejected(c.status(exp,target));assertEquals("DRAFT",c.statusOf(exp));}}
            case 19 -> {
                List<org.junit.jupiter.api.function.Executable> variants=new ArrayList<>();
                for(String variant:List.of("missing-status","json-null","empty","unknown","lowercase")) {
                    variants.add(()->{
                        long exp=c.draft();var before=c.ok(c.get(exp));
                        var body=StatusChange2972Steps.JSON.createObjectNode()
                                .put("requestId",UUID.randomUUID().toString()).put("expId",exp);
                        switch(variant) {
                            case "missing-status" -> { }
                            case "json-null" -> body.putNull("status");
                            case "empty" -> body.put("status","");
                            case "unknown" -> body.put("status","UNKNOWN");
                            case "lowercase" -> body.put("status","agreed");
                            default -> throw new IllegalArgumentException(variant);
                        }
                        var reply=c.call(false,"POST","/api/v2/experiments/status",body,"primary");
                        var after=c.ok(c.get(exp));
                        assertAll("Malformed status: "+variant,
                                // REST validation is 400, not the internal algorithm's 422 targetStatusNotFound.
                                ()->assertEquals(400,reply.code(),"PDF v17, pages 2085-2086: request validation HTTP code"),
                                ()->assertEquals("Некорректный запрос",reply.json().path("message").asText(),
                                        "PDF v17, pages 2085-2086: documented request validation message"),
                                ()->assertEquals(before.path("status"),after.path("status"),"Rejected target changed status"),
                                ()->assertEquals(before.path("updatedDt"),after.path("updatedDt"),"Rejected target changed updatedDt"),
                                ()->assertEquals(before.path("updatedBy"),after.path("updatedBy"),"Rejected target changed updatedBy"),
                                ()->assertEquals(before.path("statusChangedBy"),after.path("statusChangedBy"),"Rejected target changed statusChangedBy"));
                    });
                }
                variants.add(()->{
                    // The enum table lists AGREED, but does not explicitly define trimming.
                    // Record this parser-normalization probe separately; it has no PASS/FAIL oracle.
                    long exp=c.draft();var before=c.ok(c.get(exp));
                    var reply=c.status(exp," AGREED ");var after=c.ok(c.get(exp));
                    c.record("Exploratory whitespace normalization; excluded from contract verdict",
                            Map.of("inputStatus"," AGREED ","httpStatus",reply.code(),"response",reply.json(),
                                    "before",before,"after",after,"verdict","NOT_ASSESSED",
                                    "reason","PDF enum table does not explicitly specify trimming; API owner clarification required"));
                });
                assertAll("Malformed status contract; whitespace is recorded without a verdict",variants);
            }
            case 20 -> {for(Long exp:Arrays.asList(null,0L,-1L,Long.MAX_VALUE)){var request=StatusChange2972Steps.JSON.createObjectNode().put("requestId",UUID.randomUUID().toString()).put("status","AGREED");if(exp!=null)request.put("expId",exp);c.rejected(c.call(false,"POST","/api/v2/experiments/status",request,"primary"));}}
            case 21 -> {
                long exp=Long.parseLong(c.settings.required("case.21.fixture-id"));
                try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                    var rows=db.query("SELECT name,status,version FROM experiments.experiment WHERE id=?",exp);
                    assertEquals(1,rows.size(),"Prepare a non-V2 fixture");
                    var before=rows.get(0);
                    assertTrue(before.get("name").toString().startsWith("EXPLAB-2972-"));
                    assertNotEquals(5,((Number)before.get("version")).intValue());
                    assertEquals("DRAFT",before.get("status"));
                    c.rejected(c.status(exp,"AGREED"));
                    assertEquals(rows,db.query("SELECT name,status,version FROM experiments.experiment WHERE id=?",exp));
                }
            }
            case 22 -> {String from=c.settings.required("case.22.status");assertTrue(Set.of("STOPPED","COMPLETED","NOT_AGREED").contains(from));long exp=c.fixture(from);c.rejected(c.status(exp,"AGREED"));assertEquals(from,c.statusOf(exp));}
            case 23 -> {long exp=c.fixture("AGREED");c.ok(c.status(exp,"NOT_AGREED"));var after=c.ok(c.get(exp));assertEquals("NOT_AGREED",after.at("/status/code").asText());assertTrue(after.at("/status/statusAgreementChangedBy").isNull());}
            case 24 -> {long exp=c.fixture("AGREED");assertTrue(c.ok(c.get(exp)).path("autoStart").asBoolean());c.warning(c.status(exp,"NOT_AGREED"),"autotasksNotDeleted");}
            case 25 -> {long exp=c.fixture("DRAFT");assertEquals("MAPPER",c.ok(c.get(exp)).at("/splittingPoint/code").asText());c.ok(c.status(exp,"AGREED"));assertEquals("AGREED",c.statusOf(exp));}
            case 26 -> {
                long start=c.fixture("AGREED");
                long stop=Long.parseLong(c.settings.required("case.26.stopping-fixture-id"));
                var stopping=c.ok(c.get(stop));
                assertTrue(stopping.path("name").asText().startsWith("EXPLAB-2972-"),"Prepare an owned stop fixture");
                assertEquals("IN_PROGRESS",stopping.at("/status/code").asText());
                assertAll("MAPPER blocks both start and stop",
                        () -> mapperBlocked(c,start,"AGREED","STARTING"),
                        () -> mapperBlocked(c,stop,"IN_PROGRESS","STOPPING"));
            }
            case 27 -> {long exp=c.fixture("AGREED");c.ok(c.status(exp,"STARTING"));assertTrue(Set.of("STARTING","IN_PROGRESS").contains(c.statusOf(exp)));}
            case 28,29 -> {long exp=c.fixture("AGREED");c.ok(c.status(exp,"STARTING"));actions(c,exp,"AGREED","STARTING",4);}
            case 31 -> {long exp=c.fixture("AGREED");assertTrue(c.ok(c.get(exp)).path("autoStop").asBoolean());var r=c.status(exp,"STARTING");c.warning(r,"autostartTaskNotCreated");assertTrue(r.json().path("warnings").toString().contains("останов"),"Autostop error described as autostart");}
            case 32 -> {long exp=c.fixture("AGREED");c.warning(c.status(exp,"STARTING"),"startingActionNotCreated");}
            case 33,38 -> {
                String from=id==33?"STARTING":"STOPPING",target=id==33?"IN_PROGRESS":"STOPPED";long exp=c.fixture(from);var before=c.ok(c.get(exp));
                bulk(c,UUID.randomUUID().toString(),"SCHEDULER",List.of(Map.of("id",exp,"status",target,"userId",c.settings.user("primary"))));c.awaitStatus(exp,target);var after=c.ok(c.get(exp));
                assertEquals(before.path("realStartDt"),after.path("realStartDt"));assertEquals(before.path("startedBy"),after.path("startedBy"));
                if(id==38)assertAll("STOPPED time and actor (v2.5.0 v4, pages 12–14)",
                        ()->assertTrue(after.hasNonNull("realEndDt"),"STOPPED must record realEndDt"),
                        ()->assertEquals(after.path("updatedDt"),after.path("realEndDt"),"realEndDt must match the STOPPED update; investigate later saves separately"),
                        ()->assertEquals(c.settings.user("primary"),after.path("stoppedBy").asLong(),"STOPPED must record the transition actor"));
            }
            case 34 -> {long exp=c.fixture("IN_PROGRESS");c.ok(c.status(exp,"STOPPING"));actions(c,exp,"IN_PROGRESS","STOPPING",3);}
            case 36 -> {long exp=c.fixture("IN_PROGRESS");c.warning(c.status(exp,"STOPPING"),"stoppingActionNotCreated");}
            case 37 -> {long exp=c.fixture("IN_PROGRESS");c.warning(c.status(exp,"STOPPING"),"autotasksNotDeleted");}
            case 46,47,48 -> callbacks(c,id);
            case 49,50,51,52 -> configs(c,id);
            case 53 -> {
                long exp=c.fixture("DRAFT");var r=c.status(exp,"AGREED");assertTrue(r.code()>=400,"Save/commit failure must not return success");assertEquals("DRAFT",c.statusOf(exp));
            }
            case 56 -> {long exp=c.fixture("DRAFT");c.ok(c.status(exp,"AGREED"));assertEquals("AGREED",c.statusOf(exp));}
            case 57 -> {
                long good=c.draft(),bad=c.draft();String request=UUID.randomUUID().toString();bulk(c,request,"UI",List.of(Map.of("id",good,"status","AGREED"),Map.of("id",bad,"status","IN_PROGRESS")));
                c.awaitStatus(good,"AGREED");assertEquals("DRAFT",c.statusOf(bad));
                try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                    c.awaitAssertion("Queue failure for invalid transition", () -> {
                                var rows=db.query("SELECT error_code FROM experiments.exp_status_change_request WHERE request_id=? AND exp_id=?",request,bad);
                                assertEquals(1,rows.size());assertEquals("incorrectTransition",rows.get(0).get("error_code"));
                            });
                    c.record("Queue",db.query("SELECT error_code FROM experiments.exp_status_change_request WHERE request_id=? AND exp_id=?",request,bad));
                }
            }
            case 58 -> {
                var r=c.call(false,"POST","/api/v2/experiments/status/bulk",Map.of("requestId",UUID.randomUUID().toString(),"requestDt",System.currentTimeMillis(),"requestSource","UI","actions",List.of(Map.of("id",Long.MAX_VALUE,"status","AGREED"))),"primary");c.rejected(r);
                long exp=c.draft();bulk(c,UUID.randomUUID().toString(),"UI",List.of(Map.of("id",exp,"status","AGREED")));c.awaitStatus(exp,"AGREED");
            }
            case 59 -> {long exp=c.draft();c.ok(c.status(exp,"AGREED"));c.ok(c.status(exp,"STARTING"));c.awaitStatus(exp,"IN_PROGRESS");c.ok(c.status(exp,"STOPPING"));c.awaitStatus(exp,"STOPPED");var after=c.ok(c.get(exp));assertFalse(after.path("realStartDt").isNull());assertFalse(after.path("realEndDt").isNull());}
            default -> throw new IllegalArgumentException("Unknown scenario TP-"+id);
        }
    }
    private static void mapperBlocked(StatusChange2972Steps c,long exp,String from,String target) throws Exception {
        var before=c.ok(c.get(exp));assertEquals("MAPPER",before.at("/splittingPoint/code").asText());
        var reply=c.status(exp,target);c.rejected(reply);
        var after=c.ok(c.get(exp));assertEquals(from,after.at("/status/code").asText());
        for(String field:List.of("updatedDt","updatedBy","realStartDt","realEndDt","startedBy","stoppedBy"))
            assertEquals(before.get(field),after.get(field),"Blocked MAPPER transition changed " + field);
    }
    static void bulk(StatusChange2972Steps c,String request,String source,List<?> actions)throws Exception {c.ok(c.call(false,"POST","/api/v2/experiments/status/bulk",Map.of("requestId",request,"requestDt",System.currentTimeMillis(),"requestSource",source,"actions",actions),"primary"));}
    static void actions(StatusChange2972Steps c,long exp,String from,String to,int expected)throws Exception {
        try(var db=new StatusChange2972DbSteps(c.settings,"experiment")){
            var rows=db.actions(exp).stream().filter(r->to.equals(r.get("exp_target_status"))).toList();c.record("Status change actions",rows);assertEquals(expected,rows.size());
            Set<String> codes=new HashSet<>(),requests=new HashSet<>(),transactions=new HashSet<>();
            for(var row:rows){assertEquals(from,row.get("exp_status"));assertEquals("CONFIG",row.get("action_type"));UUID.fromString((String)row.get("request_id"));requests.add((String)row.get("request_id"));transactions.add((String)row.get("transaction_id"));codes.add((String)row.get("action"));assertEquals(exp,((Number)row.get("exp_id")).longValue());}
            assertEquals(expected,requests.size());assertEquals(1,transactions.size());assertEquals(expected==4?Set.of("EXP","V1_EXP_CONFIG","EXP_LINKS","SPLITTING"):Set.of("EXP","V1_EXP_CONFIG","SPLITTING"),codes);
        }
    }
    static void callbacks(StatusChange2972Steps c,int scenario)throws Exception {
        long exp=c.fixture("STARTING");
        try(var db=new StatusChange2972DbSteps(c.settings,"experiment")){
            var rows=db.actions(exp);assertFalse(rows.isEmpty(),"Prepare status_change_element rows while processors are paused");
            if(scenario==47){var first=rows.get(0);assertNotNull(first.get("result"),"Prepare a completed row");c.ok(c.call(false,"POST","/api/v2/experiments/complete-action",List.of(Map.of("requestId",first.get("request_id"),"result","ERROR","result_details","repeat"),Map.of("requestId",UUID.randomUUID().toString(),"result","DONE")),"primary"));assertEquals(rows,db.actions(exp),"Duplicate callback changed stored results");return;}
            var pending=rows.stream().filter(r->r.get("result")==null).toList();assertTrue(pending.size()>=(scenario==46?2:4),"Prepare at least two/four pending actions");
            List<Map<String,Object>> callbacks=new ArrayList<>();for(int i=0;i<(scenario==46?2:pending.size());i++)callbacks.add(Map.of("requestId",pending.get(i).get("request_id"),"result",scenario==46&&i==1?"ERROR":"DONE","result_details","EXPLAB-2972 managed callback"));
            c.ok(c.call(false,"POST","/api/v2/experiments/complete-action",callbacks,"primary"));var after=db.actions(exp);c.record("Callback DB",after);
            for(var callback:callbacks){var stored=after.stream().filter(r->callback.get("requestId").equals(r.get("request_id"))).findFirst().orElseThrow();assertEquals(callback.get("result"),stored.get("result"));assertEquals(callback.get("result_details"),stored.get("result_details"));}
            if(scenario==48){assertEquals("STARTING",c.statusOf(exp),"Finalizer must be paused for isolated callback observation");bulk(c,UUID.randomUUID().toString(),"UI",List.of(Map.of("id",exp,"status","IN_PROGRESS")));c.awaitStatus(exp,"IN_PROGRESS");}
        }
    }
    static List<ObjectNode> batch(long exp,String action){
        List<ObjectNode> batch=new ArrayList<>();for(String code:action.equals("START")?List.of("EXP","V1_EXP_CONFIG","EXP_LINKS","SPLITTING"):List.of("EXP","V1_EXP_CONFIG","SPLITTING")){
            var n=StatusChange2972Steps.JSON.createObjectNode().put("requestId",UUID.randomUUID().toString()).put("requestSource","UI").put("configCode",code).put("splittingPointCode","MAPPER");
            if(!code.equals("SPLITTING")){var p=n.putObject("requestParams").put("expId",exp);if(!code.equals("EXP_LINKS"))p.put("expAction",action);}batch.add(n);
        }return batch;
    }
    static void configs(StatusChange2972Steps c,int scenario)throws Exception {
        long exp=c.fixture("AGREED");
        try(var db=new StatusChange2972DbSteps(c.settings,"configuration")){
            if(scenario==50){
                for(String kind:List.of("requestId","requestSource","configCode","enum","type","null","empty")){
                    var n=batch(exp,"START").get(0);String request=n.path("requestId").asText();
                    if(Set.of("requestId","requestSource","configCode").contains(kind))n.remove(kind);
                    if(kind.equals("enum"))n.put("configCode","UNKNOWN");if(kind.equals("type"))((ObjectNode)n.path("requestParams")).put("expId","bad-type");
                    var response=c.call(true,"POST","/api/v2/configurations/request-configs",kind.equals("empty")?List.of():kind.equals("null")?Arrays.asList(n,null):List.of(n),"primary");
                    if(kind.equals("empty"))c.ok(response);else c.rejected(response);
                    assertTrue(db.query("SELECT id FROM configurations.config_request WHERE request_id=?",request).isEmpty(),"Partial/invalid batch was saved");
                }return;
            }
            for(String action:scenario==49?List.of("START","STOP"):List.of("START")){
                var batch=batch(exp,action);for(var n:batch)n.put("userId",c.settings.user("primary"));
                if(scenario==51&&c.settings.required("case.51.mode").equals("mixed"))batch.get(2).remove("requestSource");
                var r=c.call(true,"POST","/api/v2/configurations/request-configs",batch,"primary");
                if(scenario==51)assertTrue(r.code()>=400,"Prepared failure was not observed");else c.ok(r);
                if(scenario==52)c.ok(c.call(true,"POST","/api/v2/configurations/request-configs",batch,"primary"));
                for(var n:batch){var rows=db.query("SELECT request_source,config_code,splitting_point_code,user_id,request_params FROM configurations.config_request WHERE request_id=?",n.path("requestId").asText());c.record("Config queue",rows);
                    assertEquals(scenario==51?0:scenario==52?2:1,rows.size(),"Jobs must be paused; TP52 characterizes archive duplicate acceptance");
                    for(var row:rows){assertEquals("UI",row.get("request_source"));assertEquals(n.path("configCode").asText(),row.get("config_code"));assertEquals("MAPPER",row.get("splitting_point_code"));assertEquals(c.settings.user("primary"),((Number)row.get("user_id")).longValue());
                        if(n.has("requestParams")){var params=StatusChange2972Steps.JSON.readTree((String)row.get("request_params"));assertEquals(exp,params.path("expId").asLong());if(n.path("requestParams").has("expAction"))assertEquals(action,params.path("expAction").asText());}}
                }
            }
        }
    }
}
