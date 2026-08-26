package ru.sber.qa.splitter.tests_v9.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.config.SplittingResultDto;
import dto.splitter.split.SplitRequestDto;
import io.qameta.allure.Allure;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import steps.rest.RestCustomSteps;
import util.splittercheck.SplitterResponseReader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertNotNull;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;

public abstract class AbstractSplitterV9FlowTest extends AbstractAnalyticSplitterFlowTest {

    protected static final String V9_SALT = "24096d2M1e";
    protected static final String OBJECT_1 = "1";
    protected static final String OBJECT_2 = "2";
    protected static final String OBJECT_REACTIONS = "v9-reactions-object";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TS_AT = Pattern.compile("@timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    private static final Pattern TS_PLAIN = Pattern.compile("\"timestamp\"\\s*:\\s*(\\d{10,})(?:\\.\\d+)?");
    private static final Duration DEFAULT_KAFKA_TIMEOUT = Duration.ofSeconds(30);

    protected enum EndpointMode {
        MAPPER("MAPPER") {
            @Override
            public ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request) {
                return steps.splitterSteps().loadConfig(request);
            }

            @Override
            public ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request) {
                return steps.splitterSteps().split(request);
            }
        },
        REACTIONS("REACTIONS") {
            @Override
            public ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request) {
                return steps.splitterSteps().loadReactionsConfig(request);
            }

            @Override
            public ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request) {
                return steps.splitterSteps().splitReactions(request);
            }
        };

        private final String splittingPointCode;

        EndpointMode(String splittingPointCode) {
            this.splittingPointCode = splittingPointCode;
        }

        public String splittingPointCode() {
            return splittingPointCode;
        }

        public abstract ValidatableResponseWrapper load(RestCustomSteps steps, LoadConfigRequestDto request);

        public abstract ValidatableResponseWrapper split(RestCustomSteps steps, SplitRequestDto request);
    }

    protected LoadConfigRequestDto configFor(EndpointMode endpointMode, long version, ExperimentDto... experiments) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode(endpointMode.splittingPointCode())
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(Arrays.asList(experiments))
                        .build())
                .build();
    }

    protected ValidatableResponseWrapper loadConfig(FlowWithRest flow,
                                                    EndpointMode endpointMode,
                                                    LoadConfigRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(endpointMode.load(flow.restCustomSteps(), request));
        shouldBeConfigLoaded(response);
        return response;
    }

    protected ValidatableResponseWrapper split(FlowWithRest flow,
                                               EndpointMode endpointMode,
                                               SplitRequestDto request) {
        return shouldBe200(endpointMode.split(flow.restCustomSteps(), request));
    }

    protected GroupDto groupWithDocResult(String code,
                                          List<ShareDto> shares,
                                          int conditionId,
                                          String actionType,
                                          String resultValue) {
        return group(code, shares, List.of(resultWithParams(
                conditionId,
                param("actionType", actionType, "INTEGER"),
                param("result", resultValue, "INTEGER"))));
    }

    protected GroupDto groupWithEmptyResultParams(String code, int shareFrom, int shareTo, int conditionId) {
        return group(code, List.of(share(shareFrom, shareTo)), List.of(resultWithParams(conditionId)));
    }

    protected GroupDto groupWithDocResult(String code,
                                          int shareFrom,
                                          int shareTo,
                                          int conditionId,
                                          String actionType,
                                          String resultValue) {
        return groupWithDocResult(code, List.of(share(shareFrom, shareTo)), conditionId, actionType, resultValue);
    }

    protected SplittingResultDto resultWithParams(int conditionId, ParamDto... params) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(Arrays.asList(params))
                .build();
    }

    protected Long[] ids(long... values) {
        Long[] result = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = values[i];
        }
        return result;
    }

    protected JsonNode jsonBody(ValidatableResponseWrapper response, String context) {
        return SplitterResponseReader.snapshot(response).requireJsonBody(context);
    }

    protected JsonNode findObjectById(ValidatableResponseWrapper response, String objectId) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для поиска objectId=" + objectId);
        JsonNode results = root.path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));
        JsonNode found = null;
        for (JsonNode object : results) {
            if (Objects.equals(objectId, object.path("objectId").asText(null))) {
                found = object;
                break;
            }
        }
        assertNotNull(found, "Не найден objectId=" + objectId + body(response));
        return found;
    }

    protected boolean hasObject(ValidatableResponseWrapper response, String objectId) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки objectId=" + objectId);
        JsonNode results = root.path("splittingResults");
        if (!results.isArray()) {
            return false;
        }
        for (JsonNode object : results) {
            if (Objects.equals(objectId, object.path("objectId").asText(null))) {
                return true;
            }
        }
        return false;
    }

    protected JsonNode findRule(ValidatableResponseWrapper response, String objectId, String ruleCode, boolean failIfMissing) {
        if (!hasObject(response, objectId)) {
            if (failIfMissing) {
                assertTrue(false, "Не найден objectId=" + objectId + body(response));
            }
            return null;
        }
        JsonNode object = findObjectById(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        if (!objectResults.isArray()) {
            if (failIfMissing) {
                assertTrue(false, "objectResults должен быть массивом для objectId=" + objectId + body(response));
            }
            return null;
        }
        JsonNode found = null;
        for (JsonNode rule : objectResults) {
            if (Objects.equals(ruleCode, rule.path("ruleCode").asText(null))) {
                found = rule;
                break;
            }
        }
        if (failIfMissing) {
            assertNotNull(found, "Не найден ruleCode=" + ruleCode + " для objectId=" + objectId + body(response));
        }
        return found;
    }

    protected void assertRuleMissingOrEmpty(ValidatableResponseWrapper response, String objectId, String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, false);
        if (rule == null) {
            return;
        }
        JsonNode resultExps = rule.path("resultExps");
        boolean empty = resultExps.isMissingNode()
                || resultExps.isNull()
                || (resultExps.isArray() && resultExps.isEmpty());
        assertTrue(empty, "Ожидали отсутствие или пустой resultExps для ruleCode=" + ruleCode + body(response));
    }

    protected void assertObjectEmptyOrAbsent(ValidatableResponseWrapper response, String objectId) {
        if (!hasObject(response, objectId)) {
            return;
        }
        JsonNode object = findObjectById(response, objectId);
        JsonNode objectResults = object.path("objectResults");
        assertTrue(isStrictlyEmptyObjectResults(objectResults),
                "После EXPLAB-2690 объект без выбранного MAIN должен выглядеть как несвязанный: "
                        + "objectResults отсутствует, null или является пустым массивом. "
                        + "Технический MAIN.resultExps=[] и диагностический ALL в REST-ответе не допускаются "
                        + "для objectId=" + objectId + body(response));
    }

    protected boolean isStrictlyEmptyObjectResults(JsonNode objectResults) {
        return objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
    }

    /**
     * Обратная совместимость с тестами, написанными до EXPLAB-2690.
     * Начиная с v1.3.0 флаг allow-result-without-main не разрешает технический пустой MAIN
     * в публичном REST-ответе, поэтому метод использует строгий empty-contract.
     */
    @Deprecated
    protected boolean isEmptyObjectResultsForAllowResultWithoutMain(JsonNode objectResults) {
        return isStrictlyEmptyObjectResults(objectResults);
    }

    protected void assertRuleAbsent(ValidatableResponseWrapper response, String objectId, String ruleCode) {
        assertTrue(findRule(response, objectId, ruleCode, false) == null,
                "Правило " + ruleCode + " должно отсутствовать для objectId=" + objectId + body(response));
    }

    protected boolean groupResultParamsEmpty(JsonNode exp) {
        JsonNode params = exp.path("groupResultParams");
        return params.isMissingNode()
                || params.isNull()
                || (params.isArray() && params.isEmpty());
    }

    protected void assertAllDoesNotContainNonFinalGroupsWithResultParams(ValidatableResponseWrapper response, String objectId) {
        JsonNode all = findRule(response, objectId, "ALL", false);
        if (all == null) {
            return;
        }
        JsonNode resultExps = all.path("resultExps");
        assertTrue(resultExps.isArray(), "ALL.resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            String expGroup = exp.path("expGroup").asText(null);
            String finalExpGroup = exp.path("finalExpGroup").asText(null);
            boolean nonFinalGroupWithParams = finalExpGroup != null
                    && expGroup != null
                    && !Objects.equals(expGroup, finalExpGroup)
                    && !groupResultParamsEmpty(exp);

            assertFalse(nonFinalGroupWithParams,
                    "Подтвержденный дефект: в REST ALL не должна оставаться строка с результатом "
                            + "несработавшей группы, когда expGroup отличается от finalExpGroup. "
                            + allCleanupContext(objectId, exp) + body(response));
        }
    }

    protected void assertRuleExpIdsExactly(ValidatableResponseWrapper response,
                                           String objectId,
                                           String ruleCode,
                                           Long... expectedExpIds) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        Set<Long> actual = new LinkedHashSet<>();
        for (JsonNode exp : resultExps) {
            actual.add(exp.path("expId").asLong(Long.MIN_VALUE));
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList(expectedExpIds)), actual,
                "Неверный набор expId в " + ruleCode + " для objectId=" + objectId + body(response));
    }

    protected void assertRuleResultSize(ValidatableResponseWrapper response,
                                        String objectId,
                                        String ruleCode,
                                        int expectedSize) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        assertEquals(expectedSize, resultExps.size(), body(response));
    }

    protected JsonNode firstRuleExp(ValidatableResponseWrapper response, String objectId, String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray() && !resultExps.isEmpty(),
                ruleCode + ".resultExps должен быть непустым" + body(response));
        return resultExps.get(0);
    }

    protected void assertFirstRuleExp(ValidatableResponseWrapper response,
                                      String objectId,
                                      String ruleCode,
                                      long expectedExpId,
                                      String expectedExpGroup,
                                      String expectedFinalExpGroup) {
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedExpId, exp.path("expId").asLong(Long.MIN_VALUE), body(response));
        assertEquals(expectedExpGroup, exp.path("expGroup").asText(null), body(response));
        assertFinalExpGroup(exp, expectedFinalExpGroup, response);
    }

    protected void assertFinalExpGroup(JsonNode exp, String expectedFinalExpGroup, ValidatableResponseWrapper response) {
        JsonNode finalExpGroup = exp.path("finalExpGroup");
        if (expectedFinalExpGroup == null) {
            assertTrue(finalExpGroup.isMissingNode() || finalExpGroup.isNull(),
                    "Ожидали finalExpGroup=null" + body(response));
        } else {
            assertEquals(expectedFinalExpGroup, finalExpGroup.asText(null), body(response));
        }
    }

    protected void assertFirstRuleExpConditionId(ValidatableResponseWrapper response,
                                                 String objectId,
                                                 String ruleCode,
                                                 int expectedConditionId) {
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedConditionId, exp.path("conditionId").asInt(Integer.MIN_VALUE), body(response));
    }

    protected void assertFirstRuleExpActionType(ValidatableResponseWrapper response,
                                                String objectId,
                                                String ruleCode,
                                                String expectedActionType) {
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedActionType, resultParamValue(exp, "actionType"), body(response));
    }

    protected void assertFirstRuleExpResultValue(ValidatableResponseWrapper response,
                                                 String objectId,
                                                 String ruleCode,
                                                 String expectedValue) {
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        assertEquals(expectedValue, resultParamValue(exp, "result"), body(response));
    }

    protected void assertFirstRuleExpHasEmptyResultParams(ValidatableResponseWrapper response,
                                                          String objectId,
                                                          String ruleCode) {
        JsonNode exp = firstRuleExp(response, objectId, ruleCode);
        JsonNode params = exp.path("groupResultParams");
        boolean empty = params.isMissingNode()
                || params.isNull()
                || (params.isArray() && params.isEmpty());
        assertTrue(empty, "Ожидали пустой groupResultParams" + body(response));
    }

    /**
     * Контрактная проверка REST ALL по актуальному решению команды.
     * ALL может оставаться диагностическим блоком, но каждая строка ALL, попавшая в API response,
     * должна иметь заполненную finalExpGroup и не должна расходиться по expGroup/finalExpGroup.
     */
    protected void assertAllResponseRowsAreWorkedGroups(ValidatableResponseWrapper response, String objectId) {
        JsonNode all = findRule(response, objectId, "ALL", false);
        if (all == null) {
            return;
        }
        JsonNode resultExps = all.path("resultExps");
        assertTrue(resultExps.isArray(), "ALL.resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            String expGroup = exp.path("expGroup").asText(null);
            String finalExpGroup = exp.path("finalExpGroup").asText(null);
            String context = allCleanupContext(objectId, exp);

            assertNotNull(finalExpGroup,
                    "В REST-ответе ALL не должно быть строк с finalExpGroup=null. "
                            + "Такие несработавшие группы должны быть вычищены из API response. " + context + body(response));
            assertEquals(expGroup, finalExpGroup,
                    "В REST-ответе ALL не должно быть строк, где привязанная группа expGroup отличается "
                            + "от сработавшей finalExpGroup. " + context + body(response));
        }
    }

    protected void assertAllResponseRowsAreWorkedGroupsForEveryObject(ValidatableResponseWrapper response) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки очистки ALL");
        JsonNode results = root.path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));
        for (JsonNode object : results) {
            String objectId = object.path("objectId").asText(null);
            assertNotNull(objectId, "objectId должен быть заполнен" + body(response));
            assertAllResponseRowsAreWorkedGroups(response, objectId);
        }
    }

    private String allCleanupContext(String objectId, JsonNode exp) {
        return "objectId=" + objectId
                + ", expId=" + exp.path("expId").asText(null)
                + ", conditionId=" + exp.path("conditionId").asText(null)
                + ", expGroup=" + exp.path("expGroup").asText(null)
                + ", finalExpGroup=" + exp.path("finalExpGroup").asText(null)
                + ", spreadValue=" + exp.path("spreadValue").asText(null)
                + ", exp=" + exp;
    }

    protected void assertNoResultDtInApiResponse(ValidatableResponseWrapper response) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки resultDt");
        assertFalse(hasFieldRecursively(root, "resultDt"),
                "SDK-derived: в API split response resultDt сейчас отсутствует; если поле появилось, нужно обновить тест-план" + body(response));
    }

    protected void assertJsonContainsField(String payload, String fieldName) {
        JsonNode root = readJson(payload);
        assertTrue(hasFieldRecursively(root, fieldName), "Ожидали поле " + fieldName + " в Kafka payload\n" + payload);
    }

    protected void assertJsonContainsText(String payload, String expectedText) {
        assertTrue(payload.contains(expectedText), "Ожидали текст '" + expectedText + "' в payload\n" + payload);
    }

    protected String findKafkaPayloadByRequestId(KafkaService kafkaService,
                                                 String requestId,
                                                 long sinceEpochMillis) {
        String env = System.getProperty("splitter.kap.kafka.env", TEST_CONFIG.env());
        String topic = System.getProperty("splitter.kap.topic", "explab-splitting-result");
        Duration timeout = Duration.ofSeconds(Long.parseLong(System.getProperty(
                "splitter.kap.timeout.seconds", String.valueOf(DEFAULT_KAFKA_TIMEOUT.toSeconds()))));

        Allure.parameter("splitter.kap.kafka.env", env);
        Allure.parameter("splitter.kap.topic", topic);
        Allure.parameter("splitter.kap.timeout", timeout.toString());

        var consumer = kafkaService.consumerClient(env, timeout);
        List<String> seen = new ArrayList<>();
        try {
            consumer.subscribe(topic);
            consumer.poll(Duration.ofMillis(300));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(300));
                try {
                    consumer.records().forEach(recordWrapper -> {
                        var record = recordWrapper.toConsumerRecord();
                        Object raw = record.value();
                        if (raw == null) {
                            return;
                        }
                        String payload = String.valueOf(raw);
                        long payloadTs = extractEpochMillisFromJson(payload, 0L);
                        long eventTs = payloadTs == 0L ? record.timestamp() : payloadTs;
                        if (eventTs < sinceEpochMillis) {
                            return;
                        }
                        seen.add(payload);
                        if (payload.contains(requestId)) {
                            throw new FoundPayload(payload);
                        }
                    });
                } catch (FoundPayload found) {
                    Allure.addAttachment("Kafka payload / requestId=" + requestId, "application/json", found.payload(), ".json");
                    return found.payload();
                }
            }
        } finally {
            try {
                consumer.unsubscribe();
            } catch (Throwable ignored) {
            }
        }
        String sample = String.join("\n---\n", seen.stream().limit(3).toList());
        fail("Не найдено Kafka payload по requestId=" + requestId
                + ", env=" + env
                + ", topic=" + topic
                + (sample.isBlank() ? "\nСообщений за период нет" : "\nПримеры сообщений:\n" + sample));
        return null;
    }

    protected JsonNode readJson(String payload) {
        try {
            return OBJECT_MAPPER.readTree(payload);
        } catch (JsonProcessingException exception) {
            fail("Не удалось распарсить payload как JSON: " + exception.getMessage() + "\n" + payload);
            return null;
        }
    }

    protected boolean hasFieldRecursively(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            if (node.has(fieldName)) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (hasFieldRecursively(fields.next().getValue(), fieldName)) {
                    return true;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasFieldRecursively(item, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }


    protected void assertBasicResponseContract(ValidatableResponseWrapper response,
                                               SplitRequestDto request,
                                               long expectedConfigVersion) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для базового контракта split response");
        assertEquals(request.getRequestId(), root.path("requestId").asText(null), body(response));
        assertEquals(request.getSplittingId(), root.path("splittingId").asText(null), body(response));
        assertEquals(expectedConfigVersion, root.path("splittingConfigVersion").asLong(Long.MIN_VALUE), body(response));
        assertNotNull(root.path("responseId").asText(null), "responseId должен быть заполнен" + body(response));
        assertFalse(root.path("responseId").asText("").isBlank(), "responseId должен быть непустым" + body(response));
        JsonNode results = root.path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));
    }

    protected void assertSplittingResultsHaveUniqueObjectIds(ValidatableResponseWrapper response) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки уникальности objectId");
        JsonNode results = root.path("splittingResults");
        assertTrue(results.isArray(), "splittingResults должен быть массивом" + body(response));
        Set<String> objectIds = new LinkedHashSet<>();
        for (JsonNode object : results) {
            String objectId = object.path("objectId").asText(null);
            assertNotNull(objectId, "objectId должен быть заполнен" + body(response));
            assertTrue(objectIds.add(objectId), "objectId не должен дублироваться: " + objectId + body(response));
        }
    }

    protected void assertObjectFlagsEmpty(ValidatableResponseWrapper response, String objectId) {
        JsonNode object = findObjectById(response, objectId);
        JsonNode flags = object.path("objectFlags");
        boolean empty = flags.isMissingNode()
                || flags.isNull()
                || (flags.isArray() && flags.isEmpty());
        assertTrue(empty, "Ожидали пустой objectFlags для objectId=" + objectId + body(response));
    }

    protected void assertFilteredFlag(ValidatableResponseWrapper response, String objectId, boolean expectedValue) {
        JsonNode object = findObjectById(response, objectId);
        JsonNode flags = object.path("objectFlags");
        assertTrue(flags.isArray(), "objectFlags должен быть массивом для objectId=" + objectId + body(response));
        for (JsonNode flag : flags) {
            if ("filtered".equals(flag.path("code").asText(null))) {
                assertEquals(Boolean.toString(expectedValue), flag.path("value").asText(null), body(response));
                return;
            }
        }
        fail("Не найден objectFlag filtered для objectId=" + objectId + body(response));
    }

    protected void assertRuleExpIdsExactlyInOrder(ValidatableResponseWrapper response,
                                                  String objectId,
                                                  String ruleCode,
                                                  Long... expectedExpIds) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        List<Long> actual = new ArrayList<>();
        for (JsonNode exp : resultExps) {
            actual.add(exp.path("expId").asLong(Long.MIN_VALUE));
        }
        assertEquals(Arrays.asList(expectedExpIds), actual,
                "Неверный порядок expId в " + ruleCode + " для objectId=" + objectId + body(response));
    }

    protected void assertRuleExpsHaveMandatoryFields(ValidatableResponseWrapper response,
                                                     String objectId,
                                                     String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            assertTrue(exp.path("conditionId").canConvertToLong(), "conditionId должен быть числом" + body(response));
            assertTrue(exp.path("expId").canConvertToLong(), "expId должен быть числом" + body(response));
            assertNotNull(exp.path("salt").asText(null), "salt должен быть заполнен" + body(response));
            assertTrue(exp.path("spreadValue").canConvertToLong(), "spreadValue должен быть числом" + body(response));
            assertTrue(exp.has("expGroup"), "expGroup должен присутствовать в ResultExp" + body(response));
            assertTrue(exp.has("finalExpGroup"), "finalExpGroup должен присутствовать в ResultExp" + body(response));
        }
    }

    protected void assertRuleExpsHaveSpreadValue(ValidatableResponseWrapper response,
                                                 String objectId,
                                                 String ruleCode,
                                                 long expectedSpreadValue) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), "resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            assertEquals(expectedSpreadValue, exp.path("spreadValue").asLong(Long.MIN_VALUE), body(response));
        }
    }

    protected void assertAllExpFlagsHaveAlternativeValue(ValidatableResponseWrapper response,
                                                         String objectId,
                                                         String expectedValue) {
        JsonNode all = findRule(response, objectId, "ALL", false);
        if (all == null) {
            return;
        }
        JsonNode resultExps = all.path("resultExps");
        assertTrue(resultExps.isArray(), "ALL.resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            JsonNode flags = exp.path("expFlags");
            assertTrue(flags.isArray(), "expFlags должен быть массивом для ALL" + body(response));
            boolean found = false;
            for (JsonNode flag : flags) {
                if ("isAlternative".equals(flag.path("code").asText(null))) {
                    assertEquals(expectedValue, flag.path("value").asText(null), body(response));
                    found = true;
                }
            }
            assertTrue(found, "В ALL.expFlags должен быть флаг isAlternative" + body(response));
        }
    }

    protected void assertMainHasNoExpFlags(ValidatableResponseWrapper response, String objectId) {
        assertRuleExpsHaveNoExpFlags(response, objectId, "MAIN");
    }

    protected void assertRuleExpsHaveNoExpFlags(ValidatableResponseWrapper response,
                                                 String objectId,
                                                 String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), ruleCode + ".resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            JsonNode flags = exp.path("expFlags");
            boolean empty = flags.isMissingNode()
                    || flags.isNull()
                    || (flags.isArray() && flags.isEmpty());
            assertTrue(empty,
                    "В " + ruleCode + " expFlags должны отсутствовать или быть null/empty. exp=" + exp + body(response));
        }
    }

    protected void assertRuleExpsUseWorkedGroups(ValidatableResponseWrapper response,
                                                  String objectId,
                                                  String ruleCode) {
        JsonNode rule = findRule(response, objectId, ruleCode, true);
        JsonNode resultExps = rule.path("resultExps");
        assertTrue(resultExps.isArray(), ruleCode + ".resultExps должен быть массивом" + body(response));
        for (JsonNode exp : resultExps) {
            String expGroup = exp.path("expGroup").asText(null);
            String finalExpGroup = exp.path("finalExpGroup").asText(null);
            assertNotNull(finalExpGroup,
                    "В " + ruleCode + " итоговый эксперимент должен иметь finalExpGroup. exp=" + exp + body(response));
            assertEquals(expGroup, finalExpGroup,
                    "В " + ruleCode + " для REACTIONS и обычного MAPPER-результата должна использоваться "
                            + "фактически сработавшая группа. exp=" + exp + body(response));
        }
    }

    protected void assertNoAlternativeTrueAnywhere(ValidatableResponseWrapper response) {
        JsonNode root = jsonBody(response, "Ожидали JSON body для проверки отсутствия альтернатив");
        assertFalse(hasAlternativeTrue(root),
                "Ответ не должен содержать isAlternative=true" + body(response));
    }

    private boolean hasAlternativeTrue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            if (Objects.equals("isAlternative", node.path("code").asText(null))
                    && Objects.equals("true", node.path("value").asText(null))) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (hasAlternativeTrue(fields.next().getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasAlternativeTrue(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void assertJsonPathText(JsonNode root, String fieldName, String expectedValue, String context) {
        assertTrue(hasFieldValueRecursively(root, fieldName, expectedValue),
                "Ожидали поле " + fieldName + " со значением " + expectedValue + " в " + context + "\n" + root);
    }

    protected void assertJsonContainsNullField(String payload, String fieldName) {
        JsonNode root = readJson(payload);
        assertTrue(hasNullFieldRecursively(root, fieldName),
                "Ожидали хотя бы одно поле " + fieldName + "=null в Kafka payload\n" + payload);
    }

    protected void assertKafkaReportMeta(String payload,
                                         SplitRequestDto request,
                                         long expectedConfigVersion) {
        JsonNode root = readJson(payload);
        assertJsonPathText(root, "requestId", request.getRequestId(), "Kafka payload");
        assertJsonPathText(root, "splittingId", request.getSplittingId(), "Kafka payload");
        assertJsonPathText(root, "splittingConfigVersion", String.valueOf(expectedConfigVersion), "Kafka payload");
        assertTrue(hasFieldRecursively(root, "resultDt"), "Kafka payload должен содержать resultDt\n" + payload);
        assertTrue(hasFieldRecursively(root, "splittingResults"), "Kafka payload должен содержать splittingResults\n" + payload);
    }

    protected boolean hasFieldValueRecursively(JsonNode node, String fieldName, String expectedValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            if (node.has(fieldName) && expectedValue.equals(node.path(fieldName).asText(null))) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (hasFieldValueRecursively(fields.next().getValue(), fieldName, expectedValue)) {
                    return true;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasFieldValueRecursively(item, fieldName, expectedValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean hasNullFieldRecursively(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        if (node.isObject()) {
            if (node.has(fieldName) && node.get(fieldName).isNull()) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (hasNullFieldRecursively(fields.next().getValue(), fieldName)) {
                    return true;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasNullFieldRecursively(item, fieldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resultParamValue(JsonNode exp, String paramCode) {
        JsonNode params = exp.path("groupResultParams");
        assertTrue(params.isArray(), "groupResultParams должен быть массивом");
        for (JsonNode param : params) {
            if (paramCode.equals(param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");
                assertTrue(values.isArray() && !values.isEmpty(), "paramValues должен быть непустым");
                return values.get(0).asText(null);
            }
        }
        fail("Не найден groupResultParams.paramCode=" + paramCode);
        return null;
    }

    private long extractEpochMillisFromJson(String payload, long defaultValue) {
        var matcher = TS_AT.matcher(payload);
        if (!matcher.find()) {
            matcher = TS_PLAIN.matcher(payload);
            if (!matcher.find()) {
                return defaultValue;
            }
        }
        try {
            long value = Long.parseLong(matcher.group(1));
            return value > 9_999_999_999L ? value : value * 1000;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static final class FoundPayload extends RuntimeException {
        private final String payload;

        private FoundPayload(String payload) {
            this.payload = payload;
        }

        private String payload() {
            return payload;
        }
    }
}


