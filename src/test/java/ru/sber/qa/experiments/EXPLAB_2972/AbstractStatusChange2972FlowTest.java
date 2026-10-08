package ru.sber.qa.experiments.EXPLAB_2972;

import config.services.core.StatusChange2972Settings;
import flow.Flows;
import fixtures.experiments.StatusChange2972Fixture;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.fixture.FixtureService;
import steps.rest.experiments.v2.StatusChange2972Steps;
import java.util.List;

abstract class AbstractStatusChange2972FlowTest extends Flows {
    protected void runScenario(int id, boolean managed) {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        var scenario = StatusChange2972Catalog.get(id);
        String identity = String.format("EXPLAB-2972-TP-%02d", id);
        io.qameta.allure.Allure.story(scenario.title());
        io.qameta.allure.Allure.label("severity", scenario.severity());
        io.qameta.allure.Allure.label("scenarioId", identity);
        io.qameta.allure.Allure.parameter("Сценарий", identity);
        io.qameta.allure.Allure.parameter("Набор", managed ? "управляемый" : "обычный");
        if (id == 2 || id == 3 || id == 17) {
            boolean localUsers = settings.localAuth();
            String provisioning = settings.optional("launch-plan.users.mode", "prepared");
            String implementation = settings.optional("launch-plan.users.implementation", "unspecified");
            String roleBoundary = settings.env.equals("local")
                    ? (localUsers ? "Права experiment-service с разными локальными пользователями, без проверки IAM и gateway"
                    : "Роли не проверены: локальный режим системного пользователя не подходит для этого сценария")
                    : "Права experiment-service с отдельными учётными записями выбранного стенда";
            io.qameta.allure.Allure.label("roleValidation", settings.env.equals("local")
                    ? (localUsers ? "experiment-permissions-local-identities" : "not-validated") : "stand-users");
            io.qameta.allure.Allure.label("userProvisioning", provisioning);
            io.qameta.allure.Allure.label("userServiceImplementation", implementation);
            io.qameta.allure.Allure.parameter("Граница проверки ролей", roleBoundary);
            io.qameta.allure.Allure.parameter("Подготовка пользователей", provisioning);
            io.qameta.allure.Allure.parameter("Реализация user-service", implementation);
        }
        io.qameta.allure.Allure.description("Автоматизированный сценарий HTTP/SQL/Kafka с управляемыми предусловиями: «" + scenario.title()
                + "». Предусловия проверяются первым Flow-шагом. Полные границы проверки: COVERAGE.jira.txt; "
                + "подготовка стенда: CONTROLLED-SCENARIOS.jira.txt. Внешние наблюдения не подменяются PASS этого теста.");
        io.qameta.allure.Allure.getLifecycle().updateTestCase(result -> {
            result.setName(identity + ". " + scenario.title());
            // Subset selection changes JUnit invocation indexes, but must not create new test histories.
            result.setTestCaseId(stableId(identity));
            result.setHistoryId(stableId(identity + ":" + settings.env));
        });
        var fixture = new java.util.concurrent.atomic.AtomicReference<StatusChange2972Steps>();
        getFlowWithDbRest()
                .step("EXPLAB-2972 TP-" + id + ": проверить предусловия и окружение", flow -> {
                    if (managed) settings.requireManaged(id);
                    try { settings.validateExecution(List.of(id)); }
                    catch (Exception error) { throw new IllegalStateException("Scenario prerequisites failed", error); }
                })
                .step("EXPLAB-2972 TP-" + id + ": зарегистрировать фикстуру и автоматическую очистку", flow -> {
                    fixture.set(Environment.getForCurrentThread().getService(FixtureService.class)
                            .executeFixture(new StatusChange2972Fixture(settings, id)).process().getNotNullResult());
                })
                .step("EXPLAB-2972 TP-" + id + ": " + scenario.title(), flow -> {
                    try {
                        StatusChange2972Scenarios.run(fixture.get(), id);
                    } catch (RuntimeException error) { throw error; }
                    catch (Exception error) { throw new IllegalStateException("Scenario execution failed", error); }
                }).run();
    }

    private static String stableId(String identity) {
        return java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
