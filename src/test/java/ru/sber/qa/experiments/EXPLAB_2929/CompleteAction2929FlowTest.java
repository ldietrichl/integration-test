package ru.sber.qa.experiments.EXPLAB_2929;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import dto.experiments.v2.statuschange.CompleteActionRequestDto;
import dto.experiments.v2.statuschange.StatusChangeResult;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.experiments.statuschange.AbstractStatusChangeFlowTest;
import ru.sber.qa.matchers.RestMatchers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("experiment-status-change")
public class CompleteAction2929FlowTest extends AbstractStatusChangeFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2929-API-01. Callback DONE обновляет ожидающее действие")
    void doneCallbackShouldUpdatePendingAction() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String requestId = newRequestId();
        String blockerRequestId = newRequestId();

        getFlowWithDbRest()
                .step("Создаем действие и блокирующий элемент незавершенной группы", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, requestId, expId, null, null);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, blockerRequestId, expId, null, null);
                })
                .step("Передаем результат DONE", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(requestId, StatusChangeResult.DONE, null)))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем обновленную запись", flow -> {
                    Map<String, Object> row = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(requestId)
                            .singleRow()
                            .toSimpleRow();
                    assertEquals("DONE", String.valueOf(row.get("result")));
                    assertNull(row.get("result_details"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2929-API-02. Callback ERROR сохраняет описание внешней ошибки")
    void errorCallbackShouldStoreResultDetails() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String requestId = newRequestId();

        getFlowWithDbRest()
                .step("Создаем ожидающее действие", flow ->
                        flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                                transactionId, requestId, createTrackedExperimentV2(flow), null, null))
                .step("Передаем результат ERROR", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        requestId,
                                        StatusChangeResult.ERROR,
                                        "external error"
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем результат и описание", flow -> {
                    Map<String, Object> row = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(requestId)
                            .singleRow()
                            .toSimpleRow();
                    assertEquals("ERROR", String.valueOf(row.get("result")));
                    assertEquals("external error", String.valueOf(row.get("result_details")));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2929-API-03. Один callback-массив обновляет несколько действий")
    void callbackArrayShouldUpdateEveryAction() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String firstRequestId = newRequestId();
        String secondRequestId = newRequestId();
        String blockerRequestId = newRequestId();

        getFlowWithDbRest()
                .step("Создаем три элемента одной группы", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, firstRequestId, expId, null, null);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, secondRequestId, expId, null, null);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, blockerRequestId, expId, null, null);
                })
                .step("Передаем два результата одним массивом", flow ->
                        flow.restCustomSteps().experimentsV2Steps().completeStatusChangeAction(List.of(
                                        callback(firstRequestId, StatusChangeResult.DONE, null),
                                        callback(secondRequestId, StatusChangeResult.DONE, null)
                                ))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем оба результата", flow -> {
                    assertEquals("DONE", resultByRequestId(flow, firstRequestId));
                    assertEquals("DONE", resultByRequestId(flow, secondRequestId));
                    assertNull(flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(blockerRequestId)
                            .singleRow()
                            .toSimpleRow()
                            .get("result"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2929-API-04. Неизвестный requestId не прерывает обработку")
    void unknownRequestIdShouldBeAcceptedAsNotFound() {
        assumeStatusChangeElementTableExists();

        getFlowWithDbRest()
                .step("Передаем результат для неизвестного requestId", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        newRequestId(),
                                        StatusChangeResult.DONE,
                                        null
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2929-API-05/06. Повторный callback не перезаписывает результат")
    void duplicateCallbackShouldKeepOriginalResult() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String requestId = newRequestId();
        String blockerRequestId = newRequestId();

        getFlowWithDbRest()
                .step("Создаем завершенное действие и блокирующий элемент", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, requestId, expId, "DONE", "original");
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, blockerRequestId, expId, null, null);
                })
                .step("Передаем повторный ERROR callback", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        requestId,
                                        StatusChangeResult.ERROR,
                                        "retry"
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем сохранение исходного результата", flow -> {
                    Map<String, Object> row = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(requestId)
                            .singleRow()
                            .toSimpleRow();
                    assertEquals("DONE", String.valueOf(row.get("result")));
                    assertEquals("original", String.valueOf(row.get("result_details")));
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-07. Обязательные поля callback валидируются")
    void missingRequiredFieldsShouldReturnBadRequest() {
        assumeStatusChangeElementTableExists();

        getFlowWithDbRest()
                .step("Проверяем доступность complete-action валидным callback", this::assumeCompleteActionAvailable)
                .step("Передаем callback без requestId", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        null,
                                        StatusChangeResult.DONE,
                                        null
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Передаем callback без result", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        newRequestId(),
                                        null,
                                        null
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-08. Невалидный enum и поврежденный JSON возвращают 400")
    void malformedPayloadShouldReturnBadRequest() {
        assumeStatusChangeElementTableExists();

        String requestId = newRequestId();

        getFlowWithDbRest()
                .step("Проверяем доступность complete-action валидным callback", this::assumeCompleteActionAvailable)
                .step("Передаем неизвестное значение result", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction("""
                                        [{"requestId":"%s","result":"SUCCESS"}]
                                        """.formatted(requestId))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Передаем поврежденный JSON", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction("[{\"requestId\":")
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-09. requestId не в формате UUID возвращает 400")
    void nonUuidRequestIdShouldReturnBadRequest() {
        assumeStatusChangeElementTableExists();

        getFlowWithDbRest()
                .step("Проверяем доступность complete-action валидным callback", this::assumeCompleteActionAvailable)
                .step("Передаем requestId, не соответствующий UUID", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        "abc",
                                        StatusChangeResult.DONE,
                                        null
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-10. Пустой callback-массив возвращает 400")
    void emptyCallbackArrayShouldReturnBadRequest() {
        assumeStatusChangeElementTableExists();

        getFlowWithDbRest()
                .step("Проверяем доступность complete-action валидным callback", this::assumeCompleteActionAvailable)
                .step("Передаем пустой массив", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of())
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-13. NOT_FOUND не мешает обновить следующий элемент массива")
    void unknownElementShouldNotInterruptFollowingValidElement() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String validRequestId = newRequestId();
        String blockerRequestId = newRequestId();

        getFlowWithDbRest()
                .step("Создаем валидный и блокирующий элементы", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, validRequestId, expId, null, null);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, blockerRequestId, expId, null, null);
                })
                .step("Передаем неизвестный и валидный requestId", flow ->
                        flow.restCustomSteps().experimentsV2Steps().completeStatusChangeAction(List.of(
                                        callback(newRequestId(), StatusChangeResult.DONE, null),
                                        callback(validRequestId, StatusChangeResult.DONE, null)
                                ))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем обработку валидного элемента", flow ->
                        assertEquals("DONE", resultByRequestId(flow, validRequestId)))
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-11. Длинный result_details сохраняется без обрезания")
    void longResultDetailsShouldBeStored() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String requestId = newRequestId();
        String blockerRequestId = newRequestId();
        String longResultDetails = "external error details ".repeat(20);

        getFlowWithDbRest()
                .step("Создаем ожидающее действие и блокирующий элемент", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, requestId, expId, null, null);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            transactionId, blockerRequestId, expId, null, null);
                })
                .step("Передаем ERROR с длинным result_details", flow ->
                        flow.restCustomSteps().experimentsV2Steps()
                                .completeStatusChangeAction(List.of(callback(
                                        requestId,
                                        StatusChangeResult.ERROR,
                                        longResultDetails
                                )))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что result_details сохранен полностью", flow -> {
                    Map<String, Object> row = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(requestId)
                            .singleRow()
                            .toSimpleRow();

                    assertEquals("ERROR", String.valueOf(row.get("result")));
                    assertEquals(longResultDetails, String.valueOf(row.get("result_details")));
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2929-API-12. Дубликат requestId не обрабатывается молча")
    void duplicateRequestIdShouldNotBeProcessedSilently() {
        assumeStatusChangeElementTableExists();

        String firstTransactionId = newTransactionId();
        String secondTransactionId = newTransactionId();
        String requestId = newRequestId();
        boolean[] duplicateRejectedByDb = new boolean[1];

        getFlowWithDbRest()
                .step("Проверяем доступность complete-action валидным callback", this::assumeCompleteActionAvailable)
                .step("Создаем две записи с одинаковым request_id, если БД это допускает", flow -> {
                    long expId = createTrackedExperimentV2(flow);
                    flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                            firstTransactionId, requestId, expId, null, null);
                    try {
                        flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                                secondTransactionId, requestId, expId, null, null);
                    } catch (RuntimeException ignored) {
                        duplicateRejectedByDb[0] = true;
                    }
                })
                .step("Если дубликат принят, callback не должен молча обновить произвольную запись", flow -> {
                    if (duplicateRejectedByDb[0]) {
                        return;
                    }

                    int statusCode = flow.restCustomSteps().experimentsV2Steps()
                            .completeStatusChangeAction(List.of(callback(requestId, StatusChangeResult.DONE, null)))
                            .toResponse()
                            .statusCode();

                    assertTrue(statusCode >= HttpStatus.SC_BAD_REQUEST,
                            "Дубликат request_id не должен завершаться успешным произвольным обновлением");

                    List<Map<String, Object>> rows = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementsByRequestId(requestId)
                            .toSimpleTable();
                    assertEquals(2, rows.size(), "Обе дублирующие записи должны остаться для диагностики");
                    assertTrue(rows.stream().allMatch(row -> row.get("result") == null),
                            "Callback по дублирующему request_id не должен обновлять одну случайную запись");
                })
                .run();
    }

    private static CompleteActionRequestDto callback(
            String requestId,
            StatusChangeResult result,
            String resultDetails
    ) {
        return CompleteActionRequestDto.builder()
                .requestId(requestId)
                .result(result)
                .resultDetails(resultDetails)
                .build();
    }

    private void assumeCompleteActionAvailable(FlowWithDbRest flow) {
        int statusCode = flow.restCustomSteps().experimentsV2Steps()
                .completeStatusChangeAction(List.of(callback(
                        newRequestId(),
                        StatusChangeResult.DONE,
                        null
                )))
                .toResponse()
                .statusCode();
        Assumptions.assumeTrue(
                statusCode == HttpStatus.SC_OK,
                "Пропуск негативной проверки: валидный callback для неизвестного requestId должен вернуть 200, "
                        + "но вернул " + statusCode + ". Основной дефект фиксирует EXPLAB-2929-API-04"
        );
    }

    private static String resultByRequestId(flow.DbCustomFlow flow, String requestId) {
        Object result = flow.dbCustomSteps().statusChangeDbSteps()
                .findElementByRequestId(requestId)
                .singleRow()
                .toSimpleRow()
                .get("result");
        return String.valueOf(result);
    }
}
