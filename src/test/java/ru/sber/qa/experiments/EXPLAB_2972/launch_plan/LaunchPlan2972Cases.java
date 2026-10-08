package ru.sber.qa.experiments.EXPLAB_2972.launch_plan;

import com.fasterxml.jackson.databind.JsonNode;
import steps.rest.experiments.v2.StatusChange2972ControlSteps;
import steps.db.experiments.v2.StatusChange2972DbSteps;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additional Excel cases. The v17 specification resolves the outdated synchronous expectations. */
final class LaunchPlan2972Cases {
    record Case(String title,String rows,String role,String expected) { }
    static final Map<Integer,Case> CASES = Map.ofEntries(
        entry(1,"Отправка на согласование и чтение согласующим","12–14","Инициатор","AGREEMENT, updatedBy инициатора"),
        entry(2,"Отказ отправки версии, отличной от 5","15–16","Инициатор","422 expVersionIncorrect; DRAFT сохранён"),
        entry(3,"Согласование при отключённом MAPPER","17–18","Инициатор","Блокировка MAPPER не запрещает AGREEMENT"),
        entry(4,"Согласование другим пользователем","22–25","Согласующий","AGREED, statusAgreementChangedBy согласующего"),
        entry(5,"Возврат на доработку","26–28","Согласующий","NOT_AGREED, автор согласования очищен; комментарий UI отдельно"),
        entry(6,"Недоступный scheduler при согласовании","29–30","Согласующий","AGREED, предупреждение, задача не создана"),
        entry(7,"Отмена просроченного автозапуска","31","Согласующий","AGREED, autoStart=false, предупреждение"),
        entry(11,"Возврат администратором","37–38","Администратор","NOT_AGREED, автор согласования очищен"),
        entry(12,"Запрет запуска MAPPER администратору","39","Администратор","422, сообщение блокировки, AGREED сохранён"),
        entry(13,"Запуск после снятия блокировки MAPPER","40–41","Администратор","STARTING → IN_PROGRESS"),
        entry(14,"Запуск собственного эксперимента оператором","45–47","Оператор","STARTING → IN_PROGRESS, startedBy и realStartDt"),
        entry(16,"Планирование автоостановки","50","Оператор","STOP-задача на endDt с createdBy оператора; IN_PROGRESS"),
        entry(17,"Отказ configuration-service при запуске","51","Оператор","По PDF 2252–2253: STARTING и startingActionNotCreated"),
        entry(18,"Запрет запуска MAPPER оператору","52","Оператор","422, сообщение блокировки, AGREED сохранён"),
        entry(19,"Запрет запуска несогласованного эксперимента","53","Оператор","API отклоняет AGREEMENT → STARTING; кнопка проверяется отдельно"),
        entry(20,"Остановка оператором","57–59","Оператор","STOPPING → STOPPED, stoppedBy и realEndDt"),
        entry(23,"Отказ удаления автозадач при остановке","63","Оператор","STOPPING с предупреждением, после обработки конфигураций STOPPED"),
        entry(24,"Автоостановка через scheduler","64","Оператор","Фактическое выполнение STOP-задачи, STOPPED, автор из задачи"),
        entry(25,"Запрет запуска и остановки STOPPED","68","Все роли","API запрещает STARTING/STOPPING для STOPPED"),
        entry(26,"Запрет изменений COMPLETED","69","Все роли","API запрещает все перечисленные смены статуса COMPLETED")
    );
    private static Map.Entry<Integer,Case> entry(int id,String title,String rows,String role,String expected){
        return Map.entry(id,new Case(title,rows,role,expected));
    }
    static void run(LaunchPlan2972Context c,int id) throws Exception {
        switch(id) {
            case 1 -> {
                long exp=c.agreement(false,false);JsonNode entity=c.getAs(exp,"approver");
                assertEquals("AGREEMENT",entity.at("/status/code").asText());
                assertEquals(c.user("primary"),entity.path("updatedBy").asLong());
                assertEquals(c.user("primary"),entity.path("createdBy").asLong());
            }
            case 2 -> {
                long exp=Long.parseLong(c.settings.required("launch-plan.case.02.fixture-id"));
                try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                    var before=db.query("SELECT id,name,version,status,created_by FROM experiments.experiment WHERE id=?",exp);
                    assertEquals(1,before.size());assertTrue(before.get(0).get("name").toString().startsWith("EXPLAB-2972-"));
                    assertNotEquals("5",before.get(0).get("version").toString());assertEquals("DRAFT",before.get(0).get("status"));
                    assertEquals(c.user("primary"),((Number)before.get(0).get("created_by")).longValue(),"Initiator must own the legacy fixture");
                    var reply=c.change(exp,"AGREEMENT","primary");var after=db.query("SELECT id,name,version,status,created_by FROM experiments.experiment WHERE id=?",exp);
                    c.api.record("Invalid version: before and after",Map.of("before",before,"after",after));
                    assertAll(()->assertEquals(422,reply.code()),()->assertEquals(before,after),
                            ()->assertEquals("expVersionIncorrect",reply.json().at("/params/code").asText()),
                            ()->c.message(reply,"Версия эксперимента не = 5"));
                }
            }
            case 3 -> {
                long exp=c.agreement(false,false);assertEquals("AGREEMENT",c.api.statusOf(exp));
                assertEquals(c.user("primary"),c.get(exp).path("updatedBy").asLong());
            }
            case 4 -> {
                long exp=c.agreement(false,false);c.api.ok(c.change(exp,"AGREED","approver"));
                JsonNode entity=c.get(exp);assertEquals("AGREED",entity.at("/status/code").asText());
                assertEquals(c.user("approver"),entity.at("/status/statusAgreementChangedBy").asLong());
                assertEquals(c.user("approver"),entity.path("updatedBy").asLong());
            }
            case 5,11 -> {
                long exp=c.agreement(false,false);String role=id==5?"approver":"admin";
                c.api.ok(c.change(exp,"NOT_AGREED",role));JsonNode entity=c.get(exp);
                assertEquals("NOT_AGREED",entity.at("/status/code").asText());
                assertTrue(entity.at("/status/statusAgreementChangedBy").isNull(),"Agreement author must be null");
                assertEquals(c.user(role),entity.path("updatedBy").asLong());
            }
            case 6 -> {
                try(var control=new StatusChange2972ControlSteps(c.api)) {
                    control.open();long exp=c.agreement(true,false);control.fault("scheduler-503");
                    var reply=c.change(exp,"AGREED","approver");JsonNode entity=c.get(exp);var tasks=c.tasks(control.observe(),exp);
                    assertAll(()->c.api.warning(reply,"autostartTaskNotCreated"),
                            ()->assertEquals("AGREED",entity.at("/status/code").asText()),
                            ()->assertTrue(tasks.isEmpty(),"Scheduler must not accept an autostart task during create failure"),
                            ()->assertEquals(c.user("approver"),entity.at("/status/statusAgreementChangedBy").asLong()));
                }
            }
            case 7 -> {
                long exp=c.fixture("AGREEMENT");JsonNode before=c.get(exp);
                assertTrue(before.path("autoStart").asBoolean());assertTrue(before.path("startDt").asLong()<System.currentTimeMillis());
                var reply=c.change(exp,"AGREED","approver");JsonNode entity=c.get(exp);
                assertAll(()->c.api.warning(reply,"autostartCanceled"),
                        ()->assertEquals("AGREED",entity.at("/status/code").asText()),
                        ()->assertFalse(entity.path("autoStart").asBoolean()),
                        ()->assertEquals(c.user("approver"),entity.at("/status/statusAgreementChangedBy").asLong()));
            }
            case 12,18 -> {
                long exp=c.agreed(false);var reply=c.change(exp,"STARTING",id==12?"admin":"operator");
                JsonNode entity=c.get(exp);
                assertAll(()->assertEquals(422,reply.code()),
                        ()->c.message(reply,"Заблокированы запуски и остановки экспериментов для Mapper"),
                        ()->assertEquals("AGREED",entity.at("/status/code").asText()));
            }
            case 13 -> {
                try(var control=new StatusChange2972ControlSteps(c.api)) {
                    control.open();long exp=c.agreed(false);
                    control.command("mapper",Map.of("disabled",true));
                    assertEquals(422,c.change(exp,"STARTING","admin").code());
                    assertEquals("AGREED",c.api.statusOf(exp),"Rejected start must preserve AGREED");
                    control.command("mapper",Map.of("disabled",false));
                    c.api.ok(c.change(exp,"STARTING","admin"));assertEquals("STARTING",c.api.statusOf(exp));
                    long acceptedAt=c.get(exp).path("updatedDt").asLong();
                    control.jobs("both");c.finalActor(exp,"IN_PROGRESS","startedBy","realStartDt","admin",acceptedAt);
                }
            }
            case 14,16 -> {
                try(var control=new StatusChange2972ControlSteps(c.api)) {
                    control.open();long exp=c.agreed(id==16);
                    if(id==14) {
                        var denied=c.change(exp,"STARTING","primary");c.api.rejected(denied);
                        assertEquals("AGREED",c.api.statusOf(exp),"Another creator must not start the operator's experiment");
                    }
                    c.api.ok(c.change(exp,"STARTING","operator"));assertEquals("STARTING",c.api.statusOf(exp));
                    JsonNode starting=c.get(exp);long acceptedAt=starting.path("updatedDt").asLong();
                    assertEquals(c.user("operator"),starting.path("createdBy").asLong());
                    if(id==16) {
                        c.plannedStopTask(control.observe(),exp,c.get(exp));
                    }
                    control.jobs("both");c.finalActor(exp,"IN_PROGRESS","startedBy","realStartDt","operator",acceptedAt);
                }
            }
            case 17 -> {
                long exp=c.agreed(false);var reply=c.change(exp,"STARTING","operator");
                JsonNode entity=c.get(exp);
                try(var db=new StatusChange2972DbSteps(c.settings,"experiment")) {
                    var rows=db.actions(exp);c.api.record("Configuration actions observed after HTTP failure",rows);
                    assertAll(()->c.api.warning(reply,"startingActionNotCreated"),
                            ()->assertEquals("STARTING",entity.at("/status/code").asText()),
                            ()->assertTrue(rows.isEmpty(),"No configuration action may be accepted during the prepared HTTP failure"));
                }
            }
            case 19 -> {
                long exp=c.api.create(false,false,172800,259200,"operator");
                c.api.ok(c.change(exp,"AGREEMENT","operator"));var reply=c.change(exp,"STARTING","operator");
                JsonNode entity=c.get(exp);
                assertAll(()->assertEquals(422,reply.code()),
                        ()->assertEquals("incorrectTransition",reply.json().at("/params/code").asText()),
                        ()->assertEquals("AGREEMENT",entity.at("/status/code").asText()));
            }
            case 20,23 -> {
                try(var control=new StatusChange2972ControlSteps(c.api)) {
                    control.open();long exp=c.fixture("IN_PROGRESS");
                    if(id==23) {
                        JsonNode entity=c.get(exp);assertTrue(entity.path("autoStop").asBoolean());
                        c.plannedStopTask(control.observe(),exp,entity);
                        control.command("fault",Map.of("mode","scheduler-delete-503"));
                    }
                    var reply=c.change(exp,"STOPPING","operator");
                    c.api.ok(reply);assertEquals("STOPPING",c.api.statusOf(exp));
                    long acceptedAt=c.get(exp).path("updatedDt").asLong();
                    // Still exercise the asynchronous completion when only the warning contract is broken.
                    assertAll(()->{if(id==23)c.api.warning(reply,"autotasksNotDeleted");},
                            ()->{if(id==23)c.plannedStopTask(control.observe(),exp,c.get(exp));},
                            ()->{control.jobs("both");c.finalActor(exp,"STOPPED","stoppedBy","realEndDt","operator",acceptedAt);});
                }
            }
            case 24 -> {
                try(var control=new StatusChange2972ControlSteps(c.api)) {
                    control.open();long exp=c.fixture("IN_PROGRESS");JsonNode before=c.get(exp);assertTrue(before.path("autoStop").asBoolean());
                    JsonNode task=c.plannedStopTask(control.observe(),exp,before);
                    control.command("scheduler-execute",Map.of("taskId",task.path("id"),"expId",exp));
                    // Release the configured scheduler (a declared timer stub is allowed only locally).
                    // This test never sends the scheduled STOPPING request itself.
                    c.api.awaitStatus(exp,"STOPPING");
                    long acceptedAt=c.get(exp).path("updatedDt").asLong();
                    assertTrue(acceptedAt>=task.path("scheduleDateTime").asLong(),"Scheduled stop must not run before endDt");
                    control.jobs("both");
                    assertAll("Scheduler delivery and experiment completion",
                            ()->c.api.awaitAssertion("Scheduler task completion",()->assertTrue(c.tasks(control.observe(),exp).stream()
                                    .anyMatch(t->t.path("id").equals(task.path("id"))&&"COMPLETED".equals(t.path("status").asText())),
                                    "Scheduler task must complete")),
                            ()->c.finalActor(exp,"STOPPED","stoppedBy","realEndDt","operator",acceptedAt));
                }
            }
            case 25,26 -> {
                String initial=id==25?"STOPPED":"COMPLETED";long exp=c.fixture(initial);
                List<String> targets=id==25?List.of("STARTING","STOPPING"):
                    List.of("DRAFT","AGREEMENT","AGREED","NOT_AGREED","STARTING","IN_PROGRESS","STOPPING","STOPPED");
                List<org.junit.jupiter.api.function.Executable> checks=new ArrayList<>();
                for(String role:LaunchPlan2972Context.roles(id))for(String target:targets)checks.add(()->{
                    var reply=c.change(exp,target,role);JsonNode after=c.get(exp);
                    assertAll(role+" cannot change "+initial+" to "+target,
                            ()->{
                                boolean mayPerformAction=role.equals("admin")||(role.equals("operator")&&!Set.of("AGREED","NOT_AGREED").contains(target));
                                if(mayPerformAction)assertEquals(422,reply.code(),"Authorized owner/admin must reach business validation");
                                else assertTrue(Set.of(401,403,422).contains(reply.code()),"Expected permission or business rejection, got "+reply.code());
                            },
                            ()->assertEquals(initial,after.at("/status/code").asText(),role+" changed terminal status to "+target));
                });
                assertAll(initial+" is terminal for every role",checks);
            }
            default -> throw new IllegalArgumentException("Unimplemented UC "+id);
        }
    }
}
