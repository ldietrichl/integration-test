package ru.sber.qa.experiments.EXPLAB_2972.launch_plan;

import config.environment.special.EnvironmentConfigWithStatusChange2972;
import config.services.core.StatusChange2972Settings;
import flow.Flows;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.perfeccionista.framework.fixture.FixtureService;
import io.qameta.allure.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import ru.sber.qa.allure.Regression;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithStatusChange2972.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("experiment-status-change")
@TmsLink("EXPLAB-2972")
@Epic("Experiment service")
@Feature("EXPLAB-2972: пользовательские сценарии v2.5.0")
public class LaunchPlan2972FlowTest extends Flows {
    static Stream<Arguments> scenarios() {
        var settings=new StatusChange2972Settings();
        Set<Integer> selected=new LinkedHashSet<>();
        for(String item:settings.required("launch-plan.cases").split(",")) {
            int id=Integer.parseInt(item.trim().replace("UC-",""));
            if(!LaunchPlan2972Cases.CASES.containsKey(id)||!selected.add(id))
                throw new IllegalArgumentException("Unknown, already covered, or duplicate UC: "+item);
        }
        return selected.stream().map(id->Arguments.of(id,LaunchPlan2972Cases.CASES.get(id).title()));
    }

    @Regression
    @ParameterizedTest(name="EXPLAB-2972 UC-{0}: {1}")
    @MethodSource("scenarios")
    void launchPlan(int id,String title) {
        var settings=new StatusChange2972Settings();
        var item=LaunchPlan2972Cases.CASES.get(id);
        String identity=String.format("EXPLAB-2972-UC-%02d",id);
        Allure.story(title);Allure.label("scenarioId",identity);Allure.label("severity","normal");
        Allure.parameter("План","сценарии запуск.xlsx");Allure.parameter("Строки",item.rows());
        Allure.parameter("Роль",item.role());Allure.label("coverageLayer","API");
        String provisioning=settings.optional("launch-plan.users.mode","prepared");
        String implementation=settings.optional("launch-plan.users.implementation","unspecified");
        Allure.label("roleValidation",settings.env.equals("local")
                ?(settings.localAuth()?"experiment-permissions-local-identities":"not-validated")
                :"configured-stand-accounts");
        Allure.label("userProvisioning",provisioning);
        Allure.label("userServiceImplementation",implementation);
        Allure.parameter("Подготовка пользователей",provisioning);
        Allure.parameter("Реализация user-service",implementation);
        if(settings.env.equals("local"))Allure.parameter("Граница проверки ролей","Права experiment-service с локальными пользователями; IAM и gateway не проверены");
        Allure.parameter("Спецификация", "ФП ExpLab v17: стр. 731–736, 3292–3346, 2234–2268");
        Allure.description("Источник: сценарии запуск.xlsx, Лист1, строки "+item.rows()+". "+item.expected()
                +" Проверяется API-поведение. Состояние кнопок и отображение сообщений в браузере не проверяются.");
        Allure.getLifecycle().updateTestCase(result->{
            result.setName(identity+". "+title);
            result.setTestCaseId(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
            result.setHistoryId(UUID.nameUUIDFromBytes((identity+":"+settings.env+":API").getBytes(StandardCharsets.UTF_8)).toString());
        });
        var context=new AtomicReference<LaunchPlan2972Context>();
        getFlowWithDbRest()
                .step(identity+": проверить предусловия и выбранную среду",flow->{
                    try {LaunchPlan2972Context.preflight(settings,id);}
                    catch(Exception | AssertionError error){throw new IllegalStateException("UC scenario prerequisites failed",error);}
                })
                .step(identity+": зарегистрировать выделенные данные и очистку",flow->context.set(
                        Environment.getForCurrentThread().getService(FixtureService.class)
                                .executeFixture(new LaunchPlan2972Context.DataFixture(settings,id)).process().getNotNullResult()))
                .step(identity+": "+title,flow->{
                    try {LaunchPlan2972Cases.run(context.get(),id);}
                    catch(RuntimeException error){throw error;}
                    catch(Exception error){throw new IllegalStateException("UC scenario execution failed",error);}
                }).run();
    }
}
