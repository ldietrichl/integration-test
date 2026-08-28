package ru.sber.qa.experiments.EXPLAB_2696;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.enums.ExperimentsStatusesV1;
import dto.experiments.v1.ExperimentsV1PostRequestDto;
import dto.experiments.v1.ExperimentsV1PostRequestDtoBuilder;
import feeders.ExperimentsFeeder;
import flow.Flows;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import request.splitter.splits.SplitsParams;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static util.TestAssertions.assertEquals;
import static util.TestAssertions.assertFalse;
import static util.TestAssertions.assertNotNull;
import static util.TestAssertions.assertTrue;
import static util.TestAssertions.fail;

abstract class AbstractRunningV1Cache2696FlowTest extends Flows {

    private static final String V2_CJ_TOGGLE_ENV = "EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED";
    private static final long RUNNING_CACHE_WAIT_TIMEOUT_MS = Long.parseLong(System.getProperty(
            "exlab2696.running.cache.wait.timeout.ms", "45000"));
    private static final long RUNNING_CACHE_WAIT_POLL_MS = Long.parseLong(System.getProperty(
            "exlab2696.running.cache.wait.poll.ms", "3000"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final List<Long> createdExperimentIds = new ArrayList<>();
    private final List<Long> createdSplitIds = new ArrayList<>();
    private final Map<Long, ExperimentsStatusesV1> createdExperimentStatuses = new HashMap<>();
    private final Map<Long, SplitsParams> createdSplitParams = new HashMap<>();

    @AfterEach
    void cleanupRunning2696Data() {
        if (createdExperimentIds.isEmpty() && createdSplitIds.isEmpty()) {
            return;
        }

        try {
            getFlowWithRest()
                    .step("EXPLAB-2696 cleanup: убираем тестовые данные через REST API", flow -> {
                        createdExperimentIds.forEach(id -> cleanupExperiment(flow, id));
                        createdSplitIds.forEach(id -> cleanupSplit(flow, id));
                    })
                    .run();
        } catch (RuntimeException ignored) {
            // Cleanup не должен маскировать исходное падение теста.
        } finally {
            createdExperimentIds.clear();
            createdSplitIds.clear();
            createdExperimentStatuses.clear();
            createdSplitParams.clear();
        }
    }

    protected void assumeV2CjExperimentsToggleDisabledStand() {
        Assumptions.assumeFalse(isV2CjExperimentsToggleMarkedEnabled(),
                "Сценарии режима v1 требуют стенд с %s=false или без явной JVM/env-метки true"
                        .formatted(V2_CJ_TOGGLE_ENV));
    }

    protected void assumeV2CjExperimentsToggleEnabledStand() {
        Assumptions.assumeTrue(isV2CjExperimentsToggleMarkedEnabled(),
                "Перед запуском включите %s=true в yaml сервиса, перезапустите pod'ы и передайте -D%s=true"
                        .formatted(V2_CJ_TOGGLE_ENV, V2_CJ_TOGGLE_ENV));
    }

    protected Long createExperiment(FlowWithRest flow, ExperimentsStatusesV1 status) {
        long startDt = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                .plusDays(1)
                .toInstant()
                .toEpochMilli();
        long endDt = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                .plusDays(31)
                .toInstant()
                .toEpochMilli();

        ExperimentsV1PostRequestDto request = ExperimentsV1PostRequestDtoBuilder.buildDtoDefaultWithCustomParams(
                "AQA_EXPLAB_2696_" + UUID.randomUUID().toString().substring(0, 8),
                startDt,
                endDt,
                "AQA2696" + ExperimentsFeeder.generateSalt(),
                "Эксперимент создан для EXPLAB-2696",
                "AQA EXPLAB-2696",
                List.of("103081")
        );

        Long experimentId = step("Создаем v1 эксперимент и переводим его в %s через REST API"
                .formatted(status.getValue()), () -> {
            ValidatableResponseWrapper response = flow.restCustomSteps().experimentsV1Steps()
                    .createCustomExperiment(request)
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            Long id = response.toJsonPath().getLong("id");
            assertNotNull(id, "После создания эксперимента должен вернуться id");
            createdExperimentIds.add(id);
            createdExperimentStatuses.put(id, ExperimentsStatusesV1.DRAFT);

            if (status != ExperimentsStatusesV1.DRAFT) {
                changeExperimentStatus(flow, id, status);
            }

            return id;
        });

        return experimentId;
    }

    protected void changeExperimentStatus(FlowWithRest flow, Long experimentId, ExperimentsStatusesV1 status) {
        ExperimentsStatusesV1 currentStatus = createdExperimentStatuses.getOrDefault(
                experimentId, ExperimentsStatusesV1.DRAFT);
        List<ExperimentsStatusesV1> statuses = routeExperimentStatuses(currentStatus, status);
        if (statuses.isEmpty()) {
            return;
        }

        flow.restCustomSteps().experimentsV1Steps().idStatusSteps()
                .conductExperimentThroughStatuses(
                        String.valueOf(experimentId),
                        statuses,
                        statuses.stream()
                                .map(nextStatus -> "AQA EXPLAB-2696: " + nextStatus.getValue())
                                .toList()
                );
        createdExperimentStatuses.put(experimentId, status);
    }

    private List<ExperimentsStatusesV1> routeExperimentStatuses(ExperimentsStatusesV1 currentStatus,
                                                                ExperimentsStatusesV1 targetStatus) {
        if (currentStatus == targetStatus) {
            return List.of();
        }

        if (targetStatus == ExperimentsStatusesV1.AGREED) {
            return currentStatus == ExperimentsStatusesV1.AGREEMENT
                    ? List.of(ExperimentsStatusesV1.AGREED)
                    : List.of(ExperimentsStatusesV1.AGREEMENT, ExperimentsStatusesV1.AGREED);
        }

        if (targetStatus == ExperimentsStatusesV1.IN_PROGRESS) {
            if (currentStatus == ExperimentsStatusesV1.DRAFT) {
                return List.of(
                        ExperimentsStatusesV1.AGREEMENT,
                        ExperimentsStatusesV1.AGREED,
                        ExperimentsStatusesV1.IN_PROGRESS
                );
            }
            if (currentStatus == ExperimentsStatusesV1.AGREEMENT) {
                return List.of(ExperimentsStatusesV1.AGREED, ExperimentsStatusesV1.IN_PROGRESS);
            }
            return List.of(ExperimentsStatusesV1.IN_PROGRESS);
        }

        return List.of(targetStatus);
    }

    protected Long createSplit(FlowWithRest flow, String status) {
        return createSplit(flow, status, 1);
    }

    protected Long createSplit(FlowWithRest flow, String status, int version) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        SplitsParams params = SplitsParams.builder()
                .name("AQA_EXPLAB_2696_SPLIT_" + unique)
                .salt("AQA2696S" + unique)
                .status(status)
                .version(version)
                .build();

        Long splitId = step("Создаем v1 сплит в статусе %s".formatted(status), () -> {
            ValidatableResponseWrapper response = flow.restCustomSteps().splitSteps()
                    .createSplit(params)
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            Long id = response.toJsonPath().getLong("id");
            assertNotNull(id, "После создания сплита должен вернуться id");
            return id;
        });

        createdSplitIds.add(splitId);
        createdSplitParams.put(splitId, params);
        return splitId;
    }

    protected ValidatableResponseWrapper waitForExperimentInRunning(FlowWithRest flow, Long experimentId) {
        return waitForExperimentPresence(flow, experimentId, true);
    }

    protected ValidatableResponseWrapper waitForExperimentAbsentFromRunning(FlowWithRest flow, Long experimentId) {
        return waitForExperimentPresence(flow, experimentId, false);
    }

    private void changeSplitStatus(FlowWithRest flow, SplitsParams originalParams, Long id, String status) {
        SplitsParams params = SplitsParams.builder()
                .id(id)
                .type(originalParams.getType())
                .name(originalParams.getName())
                .desc(originalParams.getDesc())
                .status(status)
                .salt(originalParams.getSalt())
                .hashAlgorithm(originalParams.getHashAlgorithm())
                .quantum(originalParams.getQuantum())
                .actionType(originalParams.getActionType())
                .startDt(originalParams.getStartDt())
                .endDt(originalParams.getEndDt())
                .autoStart(originalParams.isAutoStart())
                .autoStop(originalParams.isAutoStop())
                .realStartDt(originalParams.getRealStartDt())
                .realEndDt(originalParams.getRealEndDt())
                .createdDt(originalParams.getCreatedDt())
                .createdBy(originalParams.getCreatedBy())
                .updatedDt(originalParams.getUpdatedDt())
                .updatedBy(originalParams.getUpdatedBy())
                .priority(originalParams.getPriority())
                .version(originalParams.getVersion())
                .groups(originalParams.getGroups())
                .build();

        flow.restCustomSteps().splitSteps()
                .changeSplit(params)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    private void cleanupExperiment(FlowWithRest flow, Long id) {
        ExperimentsStatusesV1 status = createdExperimentStatuses.getOrDefault(id, ExperimentsStatusesV1.DRAFT);
        if (status == ExperimentsStatusesV1.IN_PROGRESS || status == ExperimentsStatusesV1.SUSPENDED) {
            try {
                changeExperimentStatus(flow, id, ExperimentsStatusesV1.STOPPED);
            } catch (RuntimeException ignored) {
                // Эксперимент мог уже быть остановлен или удален.
            }
            return;
        }

        if (status == ExperimentsStatusesV1.STOPPED) {
            return;
        }

        try {
            flow.restCustomSteps().experimentsV1Steps().deleteExperiment(id);
        } catch (RuntimeException ignored) {
            // Cleanup не должен маскировать исходное падение теста.
        }
    }

    private void cleanupSplit(FlowWithRest flow, Long id) {
        SplitsParams originalParams = createdSplitParams.getOrDefault(id, new SplitsParams());
        if ("IN_PROGRESS".equals(originalParams.getStatus())) {
            try {
                changeSplitStatus(flow, originalParams, id, "COMPLETED");
            } catch (RuntimeException ignored) {
                // Сплит мог уже быть переведен или удален.
            }
        }

        try {
            flow.restCustomSteps().splitSteps().deleteSplit(id);
        } catch (RuntimeException ignored) {
            // Cleanup не должен маскировать исходное падение теста.
        }
    }

    protected JsonNode jsonArray(ValidatableResponseWrapper response) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(response.toResponse().asString());
            assertTrue(root.isArray(), "Ожидали JSON-массив в body. Body=" + body(response));
            return root;
        } catch (Exception exception) {
            return fail("Не удалось прочитать JSON-массив из body: " + exception.getMessage() + body(response));
        }
    }

    protected void assertArrayContainsId(ValidatableResponseWrapper response, Long expectedId) {
        if (arrayContainsId(response, expectedId)) {
            return;
        }
        fail("Ожидали id=" + expectedId + " в ответе" + body(response));
    }

    protected void assertArrayIsEmpty(ValidatableResponseWrapper response) {
        JsonNode root = jsonArray(response);
        assertTrue(root.isEmpty(), "Ожидали пустой JSON-массив" + body(response));
    }

    protected void assertArrayContainsOnlyIds(ValidatableResponseWrapper response, List<Long> expectedIds) {
        JsonNode root = jsonArray(response);
        List<Long> actualIds = idsFromArray(root);
        assertNoDuplicateIds(actualIds, response);
        for (Long actualId : actualIds) {
            assertTrue(expectedIds.contains(actualId),
                    "Query ids должен ограничивать running-ответ. Не ожидали id=" + actualId
                            + " в ответе" + body(response));
        }
    }

    protected void assertArrayDoesNotContainId(ValidatableResponseWrapper response, Long unexpectedId) {
        assertFalse(arrayContainsId(response, unexpectedId),
                "Не ожидали id=" + unexpectedId + " в ответе" + body(response));
    }

    protected void assertArrayHasNoDuplicateIds(ValidatableResponseWrapper response) {
        assertNoDuplicateIds(idsFromArray(jsonArray(response)), response);
    }

    protected void assertExperimentHasVersionAndStatus(ValidatableResponseWrapper response,
                                                       Long experimentId,
                                                       long expectedVersion,
                                                       String expectedStatus) {
        JsonNode experiment = findById(response, experimentId);
        assertEquals(expectedVersion, experiment.path("version").asLong(Long.MIN_VALUE),
                "Некорректная version для experimentId=" + experimentId + body(response));
        assertEquals(expectedStatus, experiment.path("status").path("code").asText(null),
                "Некорректный status.code для experimentId=" + experimentId + body(response));
    }

    protected void assertExperimentHasExternalDtoContract(ValidatableResponseWrapper response, Long experimentId) {
        JsonNode experiment = findById(response, experimentId);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "id", response);
        assertRequiredText(experiment, "experimentId=" + experimentId, "name", response);
        assertRequiredObject(experiment, "experimentId=" + experimentId, "status", response);
        assertEquals("IN_PROGRESS", experiment.path("status").path("code").asText(null),
                "Некорректный status.code для experimentId=" + experimentId + body(response));
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "startDt", response);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "endDt", response);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "createdDt", response);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "updatedDt", response);
        assertRequiredText(experiment, "experimentId=" + experimentId, "salt", response);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "quantum", response);
        assertRequiredAnyText(experiment, "experimentId=" + experimentId, response, "hashFunction", "hashAlgorithm");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "version", response);
        assertRequiredArray(experiment, "experimentId=" + experimentId, "groups", response);
        assertExperimentGroupsContract(experiment.path("groups"), experimentId, response);
    }

    protected void assertSplitHasExternalDtoContract(ValidatableResponseWrapper response, Long splitId) {
        JsonNode split = findById(response, splitId);
        assertRequiredNumber(split, "splitId=" + splitId, "id", response);
        assertRequiredText(split, "splitId=" + splitId, "name", response);
        assertRequiredObject(split, "splitId=" + splitId, "status", response);
        assertEquals("IN_PROGRESS", split.path("status").path("code").asText(null),
                "Некорректный status.code для splitId=" + splitId + body(response));
        assertRequiredNumber(split, "splitId=" + splitId, "startDt", response);
        assertRequiredNumber(split, "splitId=" + splitId, "endDt", response);
        assertRequiredNumber(split, "splitId=" + splitId, "createdDt", response);
        assertRequiredNumber(split, "splitId=" + splitId, "updatedDt", response);
        assertRequiredText(split, "splitId=" + splitId, "salt", response);
        assertRequiredNumber(split, "splitId=" + splitId, "quantum", response);
        assertRequiredAnyText(split, "splitId=" + splitId, response, "hashFunction", "hashAlgorithm");
        assertRequiredField(split, "splitId=" + splitId, "actionType", response);
        assertRequiredNumber(split, "splitId=" + splitId, "version", response);
        assertRequiredArray(split, "splitId=" + splitId, "groups", response);
        assertSplitGroupsContract(split.path("groups"), splitId, response);
    }

    protected void assertEveryItemHasStatus(ValidatableResponseWrapper response, String status) {
        JsonNode root = jsonArray(response);
        for (JsonNode item : root) {
            assertEquals(status, item.path("status").path("code").asText(null),
                    "В running-ответе найден элемент не в статусе " + status + body(response));
        }
    }

    protected String body(ValidatableResponseWrapper response) {
        return "\nResponse body:\n" + response.toResponse().asString();
    }

    private boolean isV2CjExperimentsToggleMarkedEnabled() {
        String value = System.getProperty(V2_CJ_TOGGLE_ENV);
        if (value == null || value.isBlank()) {
            value = System.getenv(V2_CJ_TOGGLE_ENV);
        }
        return Boolean.parseBoolean(value);
    }

    private ValidatableResponseWrapper waitForExperimentPresence(FlowWithRest flow,
                                                                 Long experimentId,
                                                                 boolean expectedPresence) {
        String action = expectedPresence ? "появления" : "исчезновения";
        return step("Ждем %s experimentId=%s в running-кэше".formatted(action, experimentId), () -> {
            long deadline = System.currentTimeMillis() + RUNNING_CACHE_WAIT_TIMEOUT_MS;
            ValidatableResponseWrapper lastResponse = null;

            do {
                lastResponse = flow.restCustomSteps().experimentsV1Steps()
                        .getExperimentsEnhanceRunningStatusOk();
                if (arrayContainsId(lastResponse, experimentId) == expectedPresence) {
                    return lastResponse;
                }
                waitBeforeNextRunningCachePoll(deadline);
            } while (System.currentTimeMillis() < deadline);

            return fail("Не дождались %s experimentId=%s в running-кэше за %d ms"
                    .formatted(action, experimentId, RUNNING_CACHE_WAIT_TIMEOUT_MS)
                    + (lastResponse == null ? "" : body(lastResponse)));
        });
    }

    private void waitBeforeNextRunningCachePoll(long deadline) {
        long pauseMs = Math.min(RUNNING_CACHE_WAIT_POLL_MS, deadline - System.currentTimeMillis());
        if (pauseMs <= 0) {
            return;
        }

        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Ожидание актуализации running-кэша было прервано");
        }
    }

    private boolean arrayContainsId(ValidatableResponseWrapper response, Long expectedId) {
        if (expectedId == null) {
            return false;
        }

        JsonNode root = jsonArray(response);
        for (JsonNode item : root) {
            if (item.path("id").asLong(Long.MIN_VALUE) == expectedId) {
                return true;
            }
        }
        return false;
    }

    private List<Long> idsFromArray(JsonNode root) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : root) {
            ids.add(item.path("id").asLong(Long.MIN_VALUE));
        }
        return ids;
    }

    private void assertNoDuplicateIds(List<Long> actualIds, ValidatableResponseWrapper response) {
        Set<Long> uniqueIds = new LinkedHashSet<>(actualIds);
        assertEquals(actualIds.size(), uniqueIds.size(),
                "В running-ответе не должно быть дублей по id. Фактические id=" + actualIds + body(response));
    }

    private JsonNode findById(ValidatableResponseWrapper response, Long id) {
        JsonNode root = jsonArray(response);
        for (JsonNode item : root) {
            if (item.path("id").asLong(Long.MIN_VALUE) == id) {
                return item;
            }
        }
        return fail("Не найден id=" + id + " в ответе" + body(response));
    }

    private void assertExperimentGroupsContract(JsonNode groups,
                                                Long experimentId,
                                                ValidatableResponseWrapper response) {
        boolean hasGroupConfig = false;
        for (int index = 0; index < groups.size(); index++) {
            JsonNode group = groups.get(index);
            String context = "experimentId=" + experimentId + ".groups[" + index + "]";
            assertRequiredText(group, context, "code", response);
            assertRequiredText(group, context, "name", response);
            assertRequiredField(group, context, "actionType", response);
            assertRequiredNumber(group, context, "shareFrom", response);
            assertRequiredAnyNumber(group, context, response, "shareTo", "size");

            JsonNode groupConfig = group.path("groupConfig");
            if (!groupConfig.isMissingNode() && !groupConfig.isNull()) {
                assertTrue(groupConfig.isObject(),
                        "Поле " + context + ".groupConfig должно быть объектом" + body(response));
                hasGroupConfig = true;
            }
        }

        assertTrue(hasGroupConfig,
                "Хотя бы одна группа experiment DTO должна содержать groupConfig" + body(response));
    }

    private void assertSplitGroupsContract(JsonNode groups, Long splitId, ValidatableResponseWrapper response) {
        for (int index = 0; index < groups.size(); index++) {
            JsonNode group = groups.get(index);
            String context = "splitId=" + splitId + ".groups[" + index + "]";
            assertRequiredText(group, context, "code", response);
            assertOptionalText(group, context, "name", response);
            assertRequiredNumber(group, context, "shareFrom", response);
            assertRequiredAnyNumber(group, context, response, "shareTo", "size");
        }
    }

    private void assertRequiredField(JsonNode node,
                                     String context,
                                     String field,
                                     ValidatableResponseWrapper response) {
        JsonNode value = node == null ? null : node.path(field);
        assertTrue(value != null && !value.isMissingNode() && !value.isNull(),
                "В DTO отсутствует обязательное поле " + context + "." + field + body(response));
    }

    private void assertRequiredText(JsonNode node,
                                    String context,
                                    String field,
                                    ValidatableResponseWrapper response) {
        assertRequiredField(node, context, field, response);
        JsonNode value = node.path(field);
        assertTrue(value.isTextual() && !value.asText().isBlank(),
                "Поле " + context + "." + field + " должно быть непустой строкой" + body(response));
    }

    private void assertRequiredAnyText(JsonNode node,
                                       String context,
                                       ValidatableResponseWrapper response,
                                       String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && value.isTextual() && !value.asText().isBlank()) {
                return;
            }
        }
        fail("В DTO ожидается одно из непустых текстовых полей "
                + String.join("/", fields) + " для " + context + body(response));
    }

    private void assertOptionalText(JsonNode node,
                                    String context,
                                    String field,
                                    ValidatableResponseWrapper response) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        assertTrue(value.isTextual() && !value.asText().isBlank(),
                "Поле " + context + "." + field + " должно быть непустой строкой" + body(response));
    }

    private void assertRequiredNumber(JsonNode node,
                                      String context,
                                      String field,
                                      ValidatableResponseWrapper response) {
        assertRequiredField(node, context, field, response);
        assertTrue(node.path(field).isNumber(),
                "Поле " + context + "." + field + " должно быть числом" + body(response));
    }

    private void assertRequiredAnyNumber(JsonNode node,
                                         String context,
                                         ValidatableResponseWrapper response,
                                         String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && value.isNumber()) {
                return;
            }
        }
        fail("В DTO ожидается одно из числовых полей "
                + String.join("/", fields) + " для " + context + body(response));
    }

    private void assertRequiredObject(JsonNode node,
                                      String context,
                                      String field,
                                      ValidatableResponseWrapper response) {
        assertRequiredField(node, context, field, response);
        assertTrue(node.path(field).isObject(),
                "Поле " + context + "." + field + " должно быть объектом" + body(response));
    }

    private void assertRequiredArray(JsonNode node,
                                     String context,
                                     String field,
                                     ValidatableResponseWrapper response) {
        assertRequiredField(node, context, field, response);
        JsonNode value = node.path(field);
        assertTrue(value.isArray() && !value.isEmpty(),
                "Поле " + context + "." + field + " должно быть непустым массивом" + body(response));
    }

}
