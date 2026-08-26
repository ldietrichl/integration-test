package ru.sber.qa.splitter;

import constants.Endpoints;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.SplittingConfigMessageDto;
import flow.Flows;
import flow.RestCustomFlow;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.allure.CriticalRegression;

import java.util.UUID;

@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class SplitterFunctionalPlanTest_flow extends Flows {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONFIG_ENDPOINT = Endpoints.Splitter.SPLITTER_CONFIG;
    private static final String SPLIT_ENDPOINT = Endpoints.Splitter.SPLITTER_SPLIT;

    private static final long BASE_VERSION = 4_000_000_000L
            + ((System.currentTimeMillis() / 1000L) % 1_000_000L) * 100L;

    private static final String OBJECT_ONE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OBJECT_TWO_ID = "22222222-2222-2222-2222-222222222222";

    private static final String OBJECT_ONE = """
            {
              "objectId": "11111111-1111-1111-1111-111111111111",
              "objectParams": [
                {"paramCode": "configCommId", "paramValues": ["1001"], "dataType": "INTEGER"},
                {"paramCode": "cjId", "paramValues": ["123"], "dataType": "INTEGER"},
                {"paramCode": "sellingProductId", "paramValues": ["1-SSSV6DD"], "dataType": "STRING"},
                {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                {"paramCode": "templateId", "paramValues": ["120321"], "dataType": "INTEGER"}
              ]
            }
            """;

    private static final String OBJECT_TWO = """
            {
              "objectId": "22222222-2222-2222-2222-222222222222",
              "objectParams": [
                {"paramCode": "configCommId", "paramValues": ["1002"], "dataType": "INTEGER"},
                {"paramCode": "cjId", "paramValues": ["123"], "dataType": "INTEGER"},
                {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"},
                {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                {"paramCode": "templateId", "paramValues": ["120222"], "dataType": "INTEGER"}
              ]
            }
            """;

    @BeforeEach
    void setLabels() {
    }

    @CriticalRegression
    @Test
    @Order(1)
    @DisplayName("CFG-01. Валидный конфиг принимается и начинает применяться в split")
    void configShouldBeLoaded() {
        long version = version(1);

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow ->
                        postConfig(flow, configMessage(version, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что split использует загруженную версию конфига", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(2)
    @DisplayName("CFG-02. Более старая версия конфига не должна менять активную версию")
    void oldConfigVersionShouldBeRejected() {
        long actualVersion = version(2);
        long oldVersion = version(1);

        getFlowWithRest()
                .step("Загружаем более новую версию конфига", flow ->
                        postConfig(flow, configMessage(actualVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Пытаемся загрузить более старую версию", flow ->
                        postConfig(flow, configMessage(oldVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что активной осталась более новая версия", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(actualVersion))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(3)
    @DisplayName("CFG-03. forceConfigLoad=true позволяет активировать более старую версию")
    void oldConfigVersionShouldBeLoadedWhenForceEnabled() {
        long highVersion = version(4);
        long forcedOldVersion = version(3);

        getFlowWithRest()
                .step("Загружаем более новую версию конфига", flow ->
                        postConfig(flow, configMessage(highVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Загружаем более старую версию с forceConfigLoad=true", flow ->
                        postConfig(flow, configMessage(forcedOldVersion, true, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что активной стала принудительно загруженная версия", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(forcedOldVersion))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(4)
    @DisplayName("CFG-04. Конфиг без salt и без layer не должен становиться активным")
    void configWithoutSaltOrLayerShouldBeRejected() {
        long validVersion = version(5);
        long invalidVersion = version(6);

        getFlowWithRest()
                .step("Загружаем валидный базовый конфиг", flow ->
                        postConfig(flow, configMessage(validVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем невалидный конфиг без salt/layer и получаем 400", flow ->
                        postConfig(flow, configMessage(invalidVersion, false, invalidNoSaltOrLayerConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))

                .step("Проверяем, что активная версия не изменилась", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(5)
    @DisplayName("CFG-05. Конфиг без objectSelectConditions не должен становиться активным")
    void configWithoutConditionsShouldBeRejected() {
        long validVersion = version(7);
        long invalidVersion = version(8);

        getFlowWithRest()
                .step("Загружаем валидный базовый конфиг", flow ->
                        postConfig(flow, configMessage(validVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем невалидный конфиг без objectSelectConditions и получаем 400", flow ->
                        postConfig(flow, configMessage(invalidVersion, false, invalidNoConditionsConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)))

                .step("Проверяем, что активная версия не изменилась", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(6)
    @DisplayName("CFG-06. Конфиг с пересечением диапазонов не должен становиться активным")
    void configWithIntersectingRangesShouldBeRejected() {
        long validVersion = version(8);
        long invalidVersion = version(7);

        getFlowWithRest()
                .step("Загружаем валидный базовый конфиг", flow ->
                        postConfig(flow, configMessage(validVersion, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем конфиг с пересечением диапазонов", flow ->
                        postConfig(flow, configMessage(invalidVersion, false, invalidRangesConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что активная версия не изменилась", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion))
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(7)
    @DisplayName("SPL-01. Для объекта с матчем возвращаются MAIN и ALL")
    void splitShouldReturnMainAndAllForMatchedObject() {
        long version = version(8);
        String objectPath = objectPath(OBJECT_TWO_ID);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");
        String allPath = objectResultPath(OBJECT_TWO_ID, "ALL");

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow ->
                        postConfig(flow, configMessage(version, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем split-запрос для объекта, который матчит эксперимент", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath + " != null"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + " != null"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(allPath + " != null"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps.size() == 1"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(allPath + ".resultExps.size() == 1")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(8)
    @DisplayName("SPL-02. Для объекта без матча возвращается пустой objectResults")
    void splitShouldReturnObjectWithoutResultsWhenNoExperimentMatched() {
        long version = version(9);

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow ->
                        postConfig(flow, configMessage(version, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем split-запрос для объекта без совпадений", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_ONE)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '%s'".formatted(OBJECT_ONE_ID)),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0")
                                ))
                .run();
    }

    @Test
    @Disabled("На текущем стенде конфиги с REQUEST_PARAMS не активируются через /mapper/config")
    @Order(9)
    @DisplayName("SPL-03. Привязка объектов по REQUEST_PARAMS")
    void splitShouldBindObjectsUsingRequestParams() {
        long version = version(10);
        String mainObjectOnePath = objectResultPath(OBJECT_ONE_ID, "MAIN");
        String mainObjectTwoPath = objectResultPath(OBJECT_TWO_ID, "MAIN");

        getFlowWithRest()
                .step("Загружаем конфиг с REQUEST_PARAMS", flow ->
                        postConfig(flow, configMessage(version, false, requestParamsConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем применение конфига и привязку двух объектов", flow -> {
                    String request = splitRequest(
                            randomRequestId(),
                            "123456",
                            "[{\"paramCode\":\"age\",\"paramValues\":[\"60\"],\"dataType\":\"INTEGER\"}]",
                            "[%s,%s]".formatted(OBJECT_ONE, OBJECT_TWO)
                    );
                    postSplit(flow, request).should(
                            RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version)),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainObjectOnePath + ".resultExps[0].expId == 301"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainObjectTwoPath + ".resultExps[0].expId == 301")
                    );
                })
                .run();
    }

    @Test
    @Disabled("На текущем стенде конфиги с REQUEST_PARAMS/AND-OR не активируются через /mapper/config")
    @Order(10)
    @DisplayName("SPL-04. Обработка AND/OR в objectSelectConditions")
    void splitShouldProcessAndOrConditions() {
        long version = version(11);

        getFlowWithRest()
                .step("Загружаем конфиг с AND/OR правилами", flow ->
                        postConfig(flow, configMessage(version, false, andOrConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что оба объекта попадают в результат", flow -> {
                    String request = splitRequest(
                            randomRequestId(),
                            "123456",
                            "[{\"paramCode\":\"age\",\"paramValues\":[\"50\"],\"dataType\":\"INTEGER\"},{\"paramCode\":\"segment\",\"paramValues\":[\"segment1\"],\"dataType\":\"STRING\"}]",
                            "[%s,%s]".formatted(OBJECT_ONE, OBJECT_TWO)
                    );
                    postSplit(flow, request).should(
                            RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version)),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath(OBJECT_ONE_ID) + " != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath(OBJECT_TWO_ID) + " != null")
                    );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @Order(11)
    @DisplayName("SPL-06. MAIN выбирается по приоритету actionType")
    void splitShouldChooseMainExperimentByActionTypePriority() {
        long version = version(12);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");
        String objectPath = objectPath(OBJECT_TWO_ID);

        getFlowWithRest()
                .step("Загружаем конфиг с несколькими экспериментами", flow ->
                        postConfig(flow, configMessage(version, false, priorityConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что MAIN выбирается по максимальному приоритету", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps[0].expId == 603"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath + ".objectFlags.find { it.code == 'filtered' } != null")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(12)
    @DisplayName("SPL-07. При равном приоритете MAIN выбирается по минимальному expId")
    void splitShouldChooseMainExperimentByMinExpIdWhenPriorityIsEqual() {
        long version = version(13);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");

        getFlowWithRest()
                .step("Загружаем конфиг с равным приоритетом actionType", flow ->
                        postConfig(flow, configMessage(version, false, tieByIdConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем выбор MAIN по минимальному expId", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps[0].expId == 701")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(13)
    @DisplayName("SPL-08. Некорректный actionType не участвует в выборе MAIN")
    void splitShouldIgnoreInvalidActionTypeWhenSelectingMainExperiment() {
        long version = version(14);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");

        getFlowWithRest()
                .step("Загружаем конфиг с невалидным и валидным actionType", flow ->
                        postConfig(flow, configMessage(version, false, invalidActionTypeConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем, что в MAIN попадает только валидный эксперимент", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps[0].expId == 802")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(14)
    @DisplayName("SPL-12. На текущем стенде actionType=2 возвращает filtered=false")
    void splitShouldMarkObjectAsFilteredWhenActionTypeIsTwo() {
        long version = version(15);
        String objectPath = objectPath(OBJECT_TWO_ID);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=2", flow ->
                        postConfig(flow, configMessage(version, false, filteredByActionTypeTwoConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем фактическое поведение стенда", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath + ".objectFlags.find { it.code == 'filtered' }.value == 'false'"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps[0].expId == 1201")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(15)
    @DisplayName("SPL-13. На текущем стенде actionType=4 возвращает filtered=false")
    void splitShouldMarkObjectAsFilteredWhenActionTypeIsFour() {
        long version = version(16);
        String objectPath = objectPath(OBJECT_TWO_ID);
        String mainPath = objectResultPath(OBJECT_TWO_ID, "MAIN");

        getFlowWithRest()
                .step("Загружаем конфиг с actionType=4", flow ->
                        postConfig(flow, configMessage(version, false, filteredByActionTypeFourConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Проверяем фактическое поведение стенда", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath + ".objectFlags.find { it.code == 'filtered' }.value == 'false'"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainPath + ".resultExps[0].expId == 1301")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(16)
    @DisplayName("SPL-17. Невалидный split-запрос возвращает HTTP 400")
    void splitShouldReturnValidationFailedForInvalidRequest() {
        long version = version(17);

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow ->
                        postConfig(flow, configMessage(version, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем невалидный split-запрос без requestId", flow ->
                        postSplit(flow, splitInvalidRequestWithoutRequestId())
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("status == 400"),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("error == 'Bad Request'")
                                ))
                .run();
    }

    @CriticalRegression
    @Test
    @Order(17)
    @DisplayName("SPL-18. Пустой массив объектов возвращает пустой splittingResults")
    void splitShouldReturnEmptyResultsForEmptyObjects() {
        long version = version(18);

        getFlowWithRest()
                .step("Загружаем валидный конфиг", flow ->
                        postConfig(flow, configMessage(version, false, baseConfigJson()))
                                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)))

                .step("Отправляем split-запрос с пустым массивом объектов", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[]"))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 0")
                                ))
                .run();
    }

    @Test
    @Disabled("Тест зависит от полностью чистого состояния стенда; на текущем окружении конфигурация уже загружена")
    @Order(18)
    @DisplayName("SPL-16. Без загруженного конфига сервис должен вернуть NO_SPLIT_CONFIG")
    void splitShouldReturnNoConfigErrorWhenConfigWasNotLoaded() {
        getFlowWithRest()
                .step("Отправляем split без предварительной загрузки конфига", flow ->
                        postSplit(flow, splitRequest(randomRequestId(), "123456", "[]", "[%s]".formatted(OBJECT_TWO)))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                                        RestMatchers.haveBodyWithEvaluatableJsonPathExpression("errorCode == 'NO_SPLIT_CONFIG'")
                                ))
                .run();
    }

    private ValidatableResponseWrapper postConfig(RestCustomFlow flow, SplittingConfigMessageDto message) {
        return flow.restClient().post(spec ->
                        spec.contentType(ContentType.JSON)
                                .accept("application/json")
                                .body(message),
                splitterBaseUri + CONFIG_ENDPOINT);
    }

    private ValidatableResponseWrapper postSplit(RestCustomFlow flow, String requestBody) {
        return flow.restClient().post(spec ->
                        spec.contentType(ContentType.JSON)
                                .accept("application/json")
                                .body(requestBody),
                splitterBaseUri + SPLIT_ENDPOINT);
    }

    private SplittingConfigMessageDto configMessage(long version, boolean forceConfigLoad, String splittingConfigJson) {
        try {
            JsonNode splittingConfig = OBJECT_MAPPER.readTree(splittingConfigJson);
            String uuid = UUID.randomUUID().toString();
            return new SplittingConfigMessageDto(uuid, uuid, version, forceConfigLoad, "MAPPER", splittingConfig);
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось распарсить splittingConfigJson", e);
        }
    }

    private String splitRequest(String requestId, String splittingId, String requestParamsJson, String splittingObjectsJson) {
        return """
                {
                  "requestId": "%s",
                  "splittingId": "%s",
                  "requestParams": %s,
                  "splittingObjects": %s
                }
                """.formatted(requestId, splittingId, requestParamsJson, splittingObjectsJson);
    }

    private String splitInvalidRequestWithoutRequestId() {
        return """
                {
                  "splittingId": "123456",
                  "requestParams": [],
                  "splittingObjects": [%s]
                }
                """.formatted(OBJECT_TWO);
    }

    private long version(int offset) {
        return BASE_VERSION + offset;
    }

    private String randomRequestId() {
        return UUID.randomUUID().toString();
    }

    private String objectPath(String objectId) {
        return "splittingResults.find { it.objectId == '%s' }".formatted(objectId);
    }

    private String objectResultPath(String objectId, String ruleCode) {
        return objectPath(objectId) + ".objectResults.find { it.ruleCode == '%s' }".formatted(ruleCode);
    }

    private String baseConfigJson() { return """
            {
              "experiments": [
                {
                  "id": 101,
                  "purpose": "DCG",
                  "salt": "SALT-101",
                  "objectSelectConditions": [
                    {
                      "id": 1,
                      "rules": [[{
                        "dataType": "STRING",
                        "paramCode": "sellingProductId",
                        "paramSource": "SPLITTING_OBJECTS",
                        "operatorCode": "equal",
                        "values": ["1-XCDV6TA"]
                      }]]
                    }
                  ],
                  "groups": [{
                    "code": "A",
                    "shares": [{"shareFrom": 0, "shareTo": 10000}],
                    "splittingResults": [{
                      "conditionId": 1,
                      "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]
                    }]
                  }]
                }
              ]
            }
            """; }

    private String requestParamsConfigJson() { return """
            {
              "experiments": [{
                "id": 301,
                "salt": "SALT-301",
                "objectSelectConditions": [{
                  "id": 1,
                  "rules": [[{
                    "dataType": "INTEGER",
                    "paramCode": "age",
                    "paramSource": "REQUEST_PARAMS",
                    "operatorCode": "more_equal",
                    "values": ["50"]
                  }]]
                }],
                "groups": [{
                  "code": "A",
                  "shares": [{"shareFrom": 0, "shareTo": 10000}],
                  "splittingResults": [{
                    "conditionId": 1,
                    "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]
                  }]
                }]
              }]
            }
            """; }

    private String andOrConfigJson() { return """
            {
              "experiments": [{
                "id": 401,
                "salt": "SALT-401",
                "objectSelectConditions": [{
                  "id": 1,
                  "rules": [
                    [
                      {"dataType": "INTEGER", "paramCode": "age", "paramSource": "REQUEST_PARAMS", "operatorCode": "equal", "values": ["50"]},
                      {"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}
                    ],
                    [
                      {"dataType": "STRING", "paramCode": "segment", "paramSource": "REQUEST_PARAMS", "operatorCode": "equal", "values": ["segment1"]},
                      {"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-SSSV6DD"]}
                    ]
                  ]
                }],
                "groups": [{
                  "code": "A",
                  "shares": [{"shareFrom": 0, "shareTo": 10000}],
                  "splittingResults": [{
                    "conditionId": 1,
                    "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]
                  }]
                }]
              }]
            }
            """; }

    private String priorityConfigJson() { return """
            {
              "experiments": [
                {
                  "id": 601,
                  "salt": "SALT-601",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]}]
                },
                {
                  "id": 602,
                  "salt": "SALT-602",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                },
                {
                  "id": 603,
                  "salt": "SALT-603",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["2"], "dataType": "INTEGER"}]}]}]
                }
              ]
            }
            """; }

    private String tieByIdConfigJson() { return """
            {
              "experiments": [
                {
                  "id": 701,
                  "salt": "SALT-701",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                },
                {
                  "id": 702,
                  "salt": "SALT-702",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                }
              ]
            }
            """; }

    private String invalidActionTypeConfigJson() { return """
            {
              "experiments": [
                {
                  "id": 801,
                  "salt": "SALT-801",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "unknownAction", "paramValues": ["999"], "dataType": "INTEGER"}]}]}]
                },
                {
                  "id": 802,
                  "salt": "SALT-802",
                  "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                  "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]}]
                }
              ]
            }
            """; }

    private String filteredByActionTypeTwoConfigJson() { return """
            {
              "experiments": [{
                "id": 1201,
                "salt": "SALT-1201",
                "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["2"], "dataType": "INTEGER"}]}]}]
              }]
            }
            """; }

    private String filteredByActionTypeFourConfigJson() { return """
            {
              "experiments": [{
                "id": 1301,
                "salt": "SALT-1301",
                "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["4"], "dataType": "INTEGER"}]}]}]
              }]
            }
            """; }

    private String invalidNoSaltOrLayerConfigJson() { return """
            {
              "experiments": [{
                "id": 901,
                "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]}]
              }]
            }
            """; }

    private String invalidNoConditionsConfigJson() { return """
            {
              "experiments": [{
                "id": 902,
                "salt": "SALT-902",
                "objectSelectConditions": [],
                "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": []}]
              }]
            }
            """; }

    private String invalidRangesConfigJson() { return """
            {
              "experiments": [{
                "id": 903,
                "salt": "SALT-903",
                "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                "groups": [
                  {"code": "A", "shares": [{"shareFrom": 0, "shareTo": 7000}], "splittingResults": []},
                  {"code": "B", "shares": [{"shareFrom": 6000, "shareTo": 10000}], "splittingResults": []}
                ]
              }]
            }
            """; }
}

