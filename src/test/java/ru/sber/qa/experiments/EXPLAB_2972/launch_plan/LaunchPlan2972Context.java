package ru.sber.qa.experiments.EXPLAB_2972.launch_plan;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.StatusChange2972Settings;
import io.perfeccionista.framework.fixture.*;
import io.qameta.allure.Allure;
import steps.rest.experiments.v2.StatusChange2972Steps;
import steps.rest.experiments.v2.StatusChange2972ControlSteps;
import steps.db.experiments.v2.StatusChange2972DbSteps;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class LaunchPlan2972Context {
    final StatusChange2972Settings settings;
    final StatusChange2972Steps api;
    final int id;
    LaunchPlan2972Context(StatusChange2972Settings settings,int id) {
        this.settings=settings;this.id=id;
        api=new StatusChange2972Steps(settings,100+id,String.format("UC-%02d",id));
    }
    static void preflight(StatusChange2972Settings settings,int id) throws Exception {
        if(!"true".equals(settings.required(String.format("launch-plan.case.%02d.prepared",id))))
            throw new IllegalStateException("UC stand prerequisites have not been prepared");
        LaunchPlan2972Users.prepare(settings);
        settings.validateExecution(List.of());
        if(Set.of(2,7,20,23,24,25,26).contains(id))settings.required(String.format("launch-plan.case.%02d.fixture-id",id));
        if(Set.of(3,12,18).contains(id)&&!"true".equals(settings.required("launch-plan.mapper-disabled")))
            throw new IllegalStateException("Prepare actual mapper-disabled=true on the server");
        if(id==17&&!"configuration-unavailable".equals(settings.required("launch-plan.fault")))
            throw new IllegalStateException("Prepare an actual configuration API failure");
        if(id==23&&!"scheduler-delete-503".equals(settings.required("launch-plan.fault")))
            throw new IllegalStateException("Prepare an actual scheduler delete failure");
        if(Set.of(6,13,14,16,20,23,24).contains(id)) {
            settings.required("control.base-uri");
            var probe=new StatusChange2972ControlSteps(new StatusChange2972Steps(settings,100+id));
            JsonNode capabilities=probe.capabilities();
            if(!settings.env.equals(capabilities.path("environment").asText()))
                throw new IllegalStateException("Controller environment differs from test.properties");
            List<String> required=new ArrayList<>(List.of("sessions","observations"));
            if(id!=6)required.add("jobs");
            if(id==6)required.add("scheduler-faults");
            if(id==13)required.add("mapper-switch");
            if(id==23)required.add("scheduler-delete-faults");
            if(id==24)required.add("scheduler-execution");
            for(String capability:required)if(!capabilities.path("capabilities").path(capability).asBoolean())
                throw new IllegalStateException("Controller lacks required capability: "+capability);
            if(id==24) {
                String implementation=capabilities.path("schedulerImplementation").asText();
                if(!Set.of("real","stub").contains(implementation))
                    throw new IllegalStateException("Controller must identify schedulerImplementation as real or stub");
                if(!settings.env.equals("local")&&!implementation.equals("real"))
                    throw new IllegalStateException("Corporate execution requires the real scheduler service");
                Allure.label("schedulerImplementation",implementation);
            }
            Allure.addAttachment("UC controller capabilities","application/json",capabilities.toString(),".json");
        }
        if(Set.of(2,17).contains(id))try(var db=new StatusChange2972DbSteps(settings,"experiment")){
            db.query("SELECT id FROM experiments.experiment WHERE false");
            if(id==17)db.query("SELECT id FROM experiments.status_change_element WHERE false");
        }
    }
    static List<String> roles(int id) {
        if(Set.of(25,26).contains(id))return List.of("primary","approver","admin","operator");
        if(Set.of(1,4,5,6,7).contains(id))return List.of("primary","approver");
        if(Set.of(11,12,13).contains(id))return List.of("primary","approver","admin");
        if(Set.of(14,16,17,18,19,20,23,24).contains(id))return List.of("primary","approver","operator");
        return List.of("primary");
    }
    String role(String role){return role;}
    long user(String role){return settings.user(role(role));}
    JsonNode get(long exp) throws Exception{return api.ok(api.get(exp));}
    JsonNode getAs(long exp,String role) throws Exception{return api.ok(api.call(false,"GET","/api/v2/experiments/"+exp,null,role(role)));}
    StatusChange2972Steps.Reply change(long exp,String status,String role) throws Exception {
        return api.status(exp,status,role(role),UUID.randomUUID().toString());
    }
    long fixture(String status) throws Exception {
        long exp=Long.parseLong(settings.required(String.format("launch-plan.case.%02d.fixture-id",id)));
        JsonNode value=get(exp);assertTrue(value.path("name").asText().startsWith("EXPLAB-2972-"));
        if(id==2)assertNotEquals(5,value.path("version").asInt());else assertEquals(5,value.path("version").asInt());
        assertEquals("MAPPER",value.at("/splittingPoint/code").asText(),"Fixture must use the tested splitting point");
        assertEquals(status,value.at("/status/code").asText());
        if(Set.of(20,23,24,25,26).contains(id))assertEquals(user("operator"),value.path("createdBy").asLong(),"Operator must own the fixture");
        if(id==7)assertEquals(user("primary"),value.path("createdBy").asLong(),"Initiator must own the approval fixture");
        return exp;
    }
    long agreement(boolean autoStart,boolean autoStop) throws Exception {
        long exp=api.create(autoStart,autoStop,172800,259200);
        api.ok(change(exp,"AGREEMENT","primary"));assertEquals("AGREEMENT",api.statusOf(exp));return exp;
    }
    long agreed(boolean autoStop) throws Exception {
        String owner=Set.of(14,16,17,18,19).contains(id)?"operator":"primary";
        long exp=api.create(false,autoStop,172800,259200,owner);
        api.ok(change(exp,"AGREEMENT",owner));api.ok(change(exp,"AGREED","approver"));
        assertEquals("AGREED",api.statusOf(exp),"Preparation must reach AGREED before the tested start");return exp;
    }
    void finalActor(long exp,String status,String field,String timeField,String role,long acceptedUpdatedDt) throws Exception {
        api.awaitStatus(exp,status);JsonNode result=get(exp);
        assertEquals(user(role),result.path(field).asLong());
        assertTrue(result.path(timeField).isIntegralNumber(),timeField+" must be epoch milliseconds");
        long time=result.path(timeField).asLong();assertTrue(time>0,timeField+" must be populated");
        // Both timestamps are supplied by the service: remote stand clock skew must not fail a valid transition.
        assertTrue(acceptedUpdatedDt>0,"The accepted transition must provide updatedDt");
        assertTrue(time>=acceptedUpdatedDt&&time<=result.path("updatedDt").asLong(),timeField+" outside the server transition window");
        if(status.equals("IN_PROGRESS"))assertEquals(acceptedUpdatedDt,time,"realStartDt must be set by STARTING and preserved");
        if(status.equals("STOPPED"))assertEquals(result.path("updatedDt").asLong(),time,"realEndDt must match the STOPPED update");
    }
    void message(StatusChange2972Steps.Reply reply,String text){assertTrue(reply.body().contains(text),"Missing API message: "+text);}
    static List<JsonNode> array(JsonNode node){assertTrue(node.isArray());List<JsonNode> result=new ArrayList<>();node.forEach(result::add);return result;}
    List<JsonNode> tasks(JsonNode observation,long exp){return array(observation.path("tasks")).stream().filter(t->array(t.path("actions")).stream()
            .anyMatch(a->"EXP".equals(a.path("objectType").asText())&&a.path("objectId").asLong()==exp)).toList();}
    JsonNode plannedStopTask(JsonNode observation,long exp,JsonNode experiment) {
        List<JsonNode> tasks=tasks(observation,exp).stream().filter(t->"PLANNED".equals(t.path("status").asText())).toList();
        assertEquals(1,tasks.size(),"Exactly one planned task is required for this experiment");JsonNode task=tasks.get(0);
        assertFalse(task.path("id").isMissingNode()||task.path("id").isNull(),"Scheduler task identifier is required");
        assertEquals(experiment.path("endDt").asLong(),task.path("scheduleDateTime").asLong(),"STOP must be scheduled for endDt");
        assertEquals(user("operator"),task.path("createdBy").asLong());
        List<JsonNode> actions=array(task.path("actions"));assertEquals(1,actions.size());
        assertAll(()->assertEquals("EXP",actions.get(0).path("objectType").asText()),
                ()->assertEquals(exp,actions.get(0).path("objectId").asLong()),
                ()->assertEquals("STOP",actions.get(0).path("action").asText()));
        return task;
    }
    static final class DataFixture implements Fixture<LaunchPlan2972Context,Void> {
        final StatusChange2972Settings settings;final int id;LaunchPlan2972Context context;
        DataFixture(StatusChange2972Settings settings,int id){this.settings=settings;this.id=id;}
        public FixtureSetUpResult<LaunchPlan2972Context> setUp(){context=new LaunchPlan2972Context(settings,id);return FixtureSetUpResult.of(context);}
        public FixtureTearDownResult<Void> tearDown(){
            try {if(context!=null)context.api.close();return FixtureTearDownResult.empty();}
            catch(Exception e){return FixtureTearDownResult.exception(new IllegalStateException("UC fixture cleanup failed",e));}
        }
    }
}
