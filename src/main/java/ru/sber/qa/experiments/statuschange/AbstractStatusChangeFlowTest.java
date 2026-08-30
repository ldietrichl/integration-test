package ru.sber.qa.experiments.statuschange;

import flow.Flows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import steps.db.experiments.v2.StatusChangeDbSteps;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractStatusChangeFlowTest extends Flows {
    private final Set<String> transactionIds = new LinkedHashSet<>();
    private final Set<Long> experimentIds = new LinkedHashSet<>();

    protected String newTransactionId() {
        String transactionId = "it-" + UUID.randomUUID();
        transactionIds.add(transactionId);
        return transactionId;
    }

    protected String newRequestId() {
        return UUID.randomUUID().toString();
    }

    protected long syntheticExpId() {
        return 8_000_000_000_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);
    }

    protected void trackExperiment(long experimentId) {
        experimentIds.add(experimentId);
    }

    protected long createTrackedExperimentV2(FlowWithDbRest flow) {
        long experimentId = flow.restCustomSteps().experimentsV2Steps().createDefaultExperimentV2();
        trackExperiment(experimentId);
        return experimentId;
    }

    protected void assertStatusChangeElementTableExists() {
        assertTrue(
                statusChangeElementTableExists(),
                "Не найдена таблица experiments.status_change_element. "
                        + "Проверьте применение Liquibase-миграции EXPLAB-2928 и подключение к нужной БД"
        );
    }

    protected void assumeStatusChangeElementTableExists() {
        Assumptions.assumeTrue(
                statusChangeElementTableExists(),
                "Пропуск: в БД отсутствует experiments.status_change_element. "
                        + "Сначала должна быть применена Liquibase-миграция EXPLAB-2928"
        );
    }

    protected void assertStatusChangeRequestTableExists() {
        assertTrue(
                statusChangeRequestTableExists(),
                "Не найдена таблица experiments.exp_status_change_request. "
                        + "Проверьте применение Liquibase-миграций и подключение к нужной БД"
        );
    }

    private boolean statusChangeElementTableExists() {
        boolean[] exists = new boolean[1];
        getFlowWithDb()
                .step("Проверяем наличие experiments.status_change_element", flow ->
                        exists[0] = flow.dbCustomSteps().statusChangeDbSteps()
                                .statusChangeElementTableExists())
                .run();
        return exists[0];
    }

    private boolean statusChangeRequestTableExists() {
        boolean[] exists = new boolean[1];
        getFlowWithDb()
                .step("Проверяем наличие experiments.exp_status_change_request", flow ->
                        exists[0] = flow.dbCustomSteps().statusChangeDbSteps()
                                .statusChangeRequestTableExists())
                .run();
        return exists[0];
    }

    @AfterEach
    protected void cleanupStatusChangeFixtures() {
        if (transactionIds.isEmpty() && experimentIds.isEmpty()) {
            return;
        }

        try {
            getFlowWithDbRest()
                    .step("Удаляем тестовые данные status-change", flow -> {
                        StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                        transactionIds.forEach(db::deleteFixture);
                        experimentIds.forEach(experimentId ->
                                flow.restCustomSteps().experimentsV2Steps().deleteExperimentV2ById(experimentId));
                    })
                    .run();
        } catch (RuntimeException ignored) {
            // Cleanup не должен маскировать исходное падение теста.
        } finally {
            transactionIds.clear();
            experimentIds.clear();
        }
    }
}
