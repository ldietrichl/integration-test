package ru.sber.qa.experiments.EXPLAB_2930;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.experiments.statuschange.AbstractStatusChangeFlowTest;
import steps.db.experiments.v2.StatusChangeDbSteps;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("experiment-status-change")
public class StatusChangeProcessor2930FlowTest extends AbstractStatusChangeFlowTest {
    private static final Duration PROCESSOR_TIMEOUT = Duration.ofSeconds(
            Long.getLong("exlab2930.processor.timeout.seconds", 45L));
    private static final Duration STABILITY_WINDOW = Duration.ofSeconds(
            Long.getLong("exlab2930.processor.stability.seconds", 12L));
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2930-JOB-02. Группа DONE/null остается в ожидании")
    void partialGroupShouldRemainUnprocessed() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        long expId = syntheticExpId();

        getFlowWithDbRest()
                .step("Создаем частично завершенную группу", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    db.insertStatusChangeElement(transactionId, newRequestId(), expId, "DONE", null);
                    db.insertStatusChangeElement(transactionId, newRequestId(), expId, null, null);
                })
                .step("Убеждаемся, что processor не забирает группу", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    await("Частичная группа должна оставаться неизменной")
                            .pollInSameThread()
                            .pollInterval(POLL_INTERVAL)
                            .during(STABILITY_WINDOW)
                            .atMost(PROCESSOR_TIMEOUT)
                            .untilAsserted(() -> {
                                assertEquals(2, db.countElements(transactionId));
                                assertEquals(0, db.countRequests(transactionId));
                            });
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2930-JOB-03/08. Полностью DONE-группа создает запрос смены статуса")
    void completedGroupShouldCreateStatusChangeRequest() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        long[] expId = new long[1];

        getFlowWithDbRest()
                .step("Создаем тестовый эксперимент", flow -> {
                    expId[0] = flow.restCustomSteps().experimentsV2Steps().createDefaultExperimentV2();
                    trackExperiment(expId[0]);
                })
                .step("Создаем полностью завершенную группу", flow ->
                        flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                                transactionId,
                                newRequestId(),
                                expId[0],
                                "DRAFT",
                                "COMPLETED",
                                "DONE",
                                null
                        ))
                .step("Ожидаем создание exp_status_change_request", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    await("DONE-группа должна быть преобразована в запрос смены статуса")
                            .pollInSameThread()
                            .pollInterval(POLL_INTERVAL)
                            .atMost(PROCESSOR_TIMEOUT)
                            .untilAsserted(() -> {
                                assertEquals(0, db.countElements(transactionId));
                                assertEquals(1, db.countRequests(transactionId));
                            });

                    Map<String, Object> request = db.findRequestsByTraceId(transactionId)
                            .singleRow()
                            .toSimpleRow();
                    assertEquals(expId[0], ((Number) request.get("exp_id")).longValue());
                    assertEquals("COMPLETED", String.valueOf(request.get("target_status")));
                    assertEquals("CONFIG_SERVICE", String.valueOf(request.get("request_source")));
                    assertEquals(transactionId, String.valueOf(request.get("trace_id")));
                    assertNotNull(request.get("request_dt"));
                    assertDoesNotThrow(() -> UUID.fromString(String.valueOf(request.get("request_id"))));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2930-JOB-04. Группа DONE/ERROR не создает запрос смены статуса")
    void errorGroupShouldRemainUnprocessed() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        long expId = syntheticExpId();

        getFlowWithDbRest()
                .step("Создаем группу с ошибкой действия", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    db.insertStatusChangeElement(transactionId, newRequestId(), expId, "DONE", null);
                    db.insertStatusChangeElement(transactionId, newRequestId(), expId, "ERROR", "external error");
                })
                .step("Убеждаемся, что ERROR-группа не обрабатывается", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    await("ERROR-группа должна оставаться в status_change_element")
                            .pollInSameThread()
                            .pollInterval(POLL_INTERVAL)
                            .during(STABILITY_WINDOW)
                            .atMost(PROCESSOR_TIMEOUT)
                            .untilAsserted(() -> {
                                assertEquals(2, db.countElements(transactionId));
                                assertEquals(0, db.countRequests(transactionId));
                            });
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2930-JOB-05. Обработка одной группы не удаляет незавершенную группу той же транзакции")
    void completedGroupShouldNotDeletePartialGroupWithSameTransactionId() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        long[] completedExpId = new long[1];
        long partialExpId = syntheticExpId();

        getFlowWithDbRest()
                .step("Создаем эксперимент для завершенной группы", flow -> {
                    completedExpId[0] = flow.restCustomSteps().experimentsV2Steps().createDefaultExperimentV2();
                    trackExperiment(completedExpId[0]);
                })
                .step("Создаем DONE и незавершенную группы с общим transaction_id", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    db.insertStatusChangeElement(
                            transactionId,
                            newRequestId(),
                            completedExpId[0],
                            "DRAFT",
                            "COMPLETED",
                            "DONE",
                            null
                    );
                    db.insertStatusChangeElement(
                            transactionId,
                            newRequestId(),
                            partialExpId,
                            "DRAFT",
                            "COMPLETED",
                            null,
                            null
                    );
                })
                .step("Ожидаем обработку DONE-группы", flow -> {
                    StatusChangeDbSteps db = flow.dbCustomSteps().statusChangeDbSteps();
                    await("Для завершенной группы должен появиться request")
                            .pollInSameThread()
                            .pollInterval(POLL_INTERVAL)
                            .atMost(PROCESSOR_TIMEOUT)
                            .untilAsserted(() -> assertEquals(1, db.countRequests(transactionId)));

                    Map<String, Object> remaining = db.findElementsByTransactionId(transactionId)
                            .singleRow("Незавершенная группа не должна удаляться вместе с завершенной")
                            .toSimpleRow();
                    assertEquals(partialExpId, ((Number) remaining.get("exp_id")).longValue());
                    assertEquals(1, db.countElements(transactionId));
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2930-JOB-13. request_dt хранится как timestamp согласно PDF")
    void requestDateShouldUseTimestampType() {
        assertStatusChangeRequestTableExists();

        getFlowWithDb()
                .step("Проверяем физический тип request_dt", flow -> {
                    Map<String, Object> column = flow.dbExpLabClient().executeSelect("""
                            SELECT data_type
                            FROM information_schema.columns
                            WHERE table_schema = 'experiments'
                              AND table_name = 'exp_status_change_request'
                              AND column_name = 'request_dt'
                            """).singleRow().toSimpleRow();

                    String dataType = String.valueOf(column.get("data_type"));
                    assertTrue(
                            dataType.startsWith("timestamp"),
                            "PDF требует timestamp/NOW(), фактический тип request_dt: " + dataType
                    );
                })
                .run();
    }
}
