package ru.sber.qa.experiments.EXPLAB_2696;

import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import request.CreateExperimentParams;
import request.CreateExperimentRequestFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;

abstract class AbstractRunningV1Cache2696FlowTest {

    private static final String BASE_URL_PROPERTY = "experiment.service.base.url";
    private static final String BASE_URL_ENV = "EXPERIMENT_SERVICE_BASE_URL";
    private static final String DEFAULT_BASE_URL =
            "https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru";
    private static final String V2_CJ_TOGGLE_ENV = "EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED";
    private static final long RUNNING_CACHE_WAIT_TIMEOUT_MS = Long.parseLong(System.getProperty(
            "exlab2696.running.cache.wait.timeout.ms", "45000"));
    private static final long RUNNING_CACHE_WAIT_POLL_MS = Long.parseLong(System.getProperty(
            "exlab2696.running.cache.wait.poll.ms", "3000"));
    private static final String BASE_URL = resolveBaseUrl();
    private static final RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    .keyStore("src/test/resources/keystore.p12", "V_at_platform_2025")
                    .keystoreType("PKCS12")
                    .relaxedHTTPSValidation()
    );

    private final CreateExperimentRequestFactory experimentFactory = new CreateExperimentRequestFactory();
    private final List<Long> createdExperimentIds = new ArrayList<>();
    private final List<Long> createdSplitIds = new ArrayList<>();
    private final Map<Long, ExperimentStatus> createdExperimentStatuses = new LinkedHashMap<>();
    private final Map<Long, SplitData> createdSplitParams = new LinkedHashMap<>();

    @AfterEach
    void cleanupRunning2696Data(RestService restService) {
        if (createdExperimentIds.isEmpty() && createdSplitIds.isEmpty()) {
            return;
        }

        try {
            step("EXPLAB-2696 cleanup: убираем тестовые данные через REST API", () -> {
                createdExperimentIds.forEach(id -> cleanupExperiment(restService, id));
                createdSplitIds.forEach(id -> cleanupSplit(restService, id));
            });
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

    protected Long createExperiment(RestService restService, ExperimentStatus status) {
        long startDt = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                .plusDays(1)
                .toInstant()
                .toEpochMilli();
        long endDt = ZonedDateTime.now(ZoneId.of("Europe/Moscow"))
                .plusDays(31)
                .toInstant()
                .toEpochMilli();

        CreateExperimentParams params = CreateExperimentParams.builder()
                .name("AQA_EXPLAB_2696_" + UUID.randomUUID().toString().substring(0, 8))
                .salt("AQA2696" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .startDt(startDt)
                .endDt(endDt)
                .cjIds(List.of("103081"))
                .hypothesisDesc("Эксперимент создан для EXPLAB-2696")
                .creator("AQA EXPLAB-2696")
                .build();

        return step("Создаем v1 эксперимент и переводим его в %s через REST API"
                .formatted(status.value), () -> {
            String body = experimentFactory.toJson(experimentFactory.buildDto(params));
            ValidatableResponseWrapper response = restService.restClient()
                    .post(spec -> jsonSpec(spec).body(body), BASE_URL + "/api/v1/experiments")
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            Long id = response.toJsonPath().getLong("id");
            Assertions.assertNotNull(id, "После создания эксперимента должен вернуться id");
            createdExperimentIds.add(id);
            createdExperimentStatuses.put(id, ExperimentStatus.DRAFT);

            if (status != ExperimentStatus.DRAFT) {
                changeExperimentStatus(restService, id, status);
            }

            return id;
        });
    }

    protected void changeExperimentStatus(RestService restService, Long experimentId, ExperimentStatus status) {
        ExperimentStatus currentStatus = createdExperimentStatuses.getOrDefault(experimentId, ExperimentStatus.DRAFT);
        List<ExperimentStatus> statuses = routeExperimentStatuses(currentStatus, status);
        for (ExperimentStatus nextStatus : statuses) {
            String body = """
                    {
                      "status": "%s",
                      "comment": "AQA EXPLAB-2696: %s",
                      "slave": false,
                      "ignoreWarnings": true,
                      "startCampaigns": false
                    }
                    """.formatted(nextStatus.value, nextStatus.value);
            restService.restClient()
                    .put(spec -> jsonSpec(spec).body(body),
                            BASE_URL + "/api/v1/experiments/" + experimentId + "/status")
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            createdExperimentStatuses.put(experimentId, nextStatus);
        }
    }

    protected Long createSplit(RestService restService, String status) {
        return createSplit(restService, status, 1);
    }

    protected Long createSplit(RestService restService, String status, int version) {
        SplitData split = SplitData.create(status, version);

        Long splitId = step("Создаем v1 сплит в статусе %s".formatted(status), () -> {
            ValidatableResponseWrapper response = restService.restClient()
                    .post(spec -> jsonSpec(spec).body(split.toJson()),
                            BASE_URL + "/api/v1/experiments/refbook/splits")
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            Long id = response.toJsonPath().getLong("id");
            Assertions.assertNotNull(id, "После создания сплита должен вернуться id");
            return id;
        });

        createdSplitIds.add(splitId);
        createdSplitParams.put(splitId, split.withId(splitId));
        return splitId;
    }

    protected ValidatableResponseWrapper getRunningExperiments(RestService restService) {
        return restService.restClient()
                .get(this::jsonSpec, BASE_URL + "/api/v1/experiments/list/running")
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    protected ValidatableResponseWrapper getRunningSplits(RestService restService) {
        return restService.restClient()
                .get(this::jsonSpec, BASE_URL + "/api/v1/experiments/splits/list/running")
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    protected ValidatableResponseWrapper getRunningSplits(RestService restService, List<Long> ids) {
        return restService.restClient()
                .get(spec -> jsonSpec(spec).queryParam("ids", idsCsv(ids)),
                        BASE_URL + "/api/v1/experiments/splits/list/running")
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    protected ValidatableResponseWrapper getRunningSplitsWithRawIds(RestService restService, String ids) {
        return restService.restClient()
                .get(spec -> jsonSpec(spec).queryParam("ids", ids),
                        BASE_URL + "/api/v1/experiments/splits/list/running");
    }

    protected void evictCash(RestService restService) {
        restService.restClient()
                .delete(this::jsonSpec, BASE_URL + "/api/v1/experiments/evict-cash")
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    protected ValidatableResponseWrapper waitForExperimentInRunning(RestService restService, Long experimentId) {
        return waitForExperimentPresence(restService, experimentId, true);
    }

    protected ValidatableResponseWrapper waitForExperimentAbsentFromRunning(RestService restService, Long experimentId) {
        return waitForExperimentPresence(restService, experimentId, false);
    }

    protected void assertArrayContainsId(ValidatableResponseWrapper response, Long expectedId) {
        Assertions.assertTrue(idsFromArray(response).contains(expectedId),
                "Ожидали id=" + expectedId + " в ответе");
    }

    protected void assertArrayDoesNotContainId(ValidatableResponseWrapper response, Long unexpectedId) {
        Assertions.assertFalse(idsFromArray(response).contains(unexpectedId),
                "Не ожидали id=" + unexpectedId + " в ответе");
    }

    protected void assertArrayIsEmpty(ValidatableResponseWrapper response) {
        List<?> items = response.toJsonPath().getList("$");
        Assertions.assertTrue(items.isEmpty(), "Ожидали пустой JSON-массив");
    }

    protected void assertArrayContainsOnlyIds(ValidatableResponseWrapper response, List<Long> expectedIds) {
        List<Long> actualIds = idsFromArray(response);
        assertNoDuplicateIds(actualIds);
        for (Long actualId : actualIds) {
            Assertions.assertTrue(expectedIds.contains(actualId),
                    "Query ids должен ограничивать running-ответ. Не ожидали id=" + actualId + " в ответе");
        }
    }

    protected void assertArrayHasNoDuplicateIds(ValidatableResponseWrapper response) {
        assertNoDuplicateIds(idsFromArray(response));
    }

    protected void assertExperimentHasVersionAndStatus(ValidatableResponseWrapper response,
                                                       Long experimentId,
                                                       long expectedVersion,
                                                       String expectedStatus) {
        Map<String, Object> experiment = findById(response, experimentId);
        Assertions.assertEquals(expectedVersion, asLong(experiment.get("version")),
                "Некорректный version для experimentId=" + experimentId);
        Assertions.assertEquals(expectedStatus, nestedText(experiment, "status", "code"),
                "Некорректный status.code для experimentId=" + experimentId);
    }

    protected void assertExperimentHasExternalDtoContract(ValidatableResponseWrapper response, Long experimentId) {
        Map<String, Object> experiment = findById(response, experimentId);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "id");
        assertRequiredText(experiment, "experimentId=" + experimentId, "name");
        assertRequiredObject(experiment, "experimentId=" + experimentId, "status");
        Assertions.assertEquals("IN_PROGRESS", nestedText(experiment, "status", "code"),
                "Некорректный status.code для experimentId=" + experimentId);
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "startDt");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "endDt");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "createdDt");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "updatedDt");
        assertRequiredText(experiment, "experimentId=" + experimentId, "salt");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "quantum");
        assertRequiredAnyText(experiment, "experimentId=" + experimentId, "hashFunction", "hashAlgorithm");
        assertRequiredNumber(experiment, "experimentId=" + experimentId, "version");
        assertRequiredArray(experiment, "experimentId=" + experimentId, "groups");
        assertExperimentGroupsContract(asList(experiment.get("groups")), experimentId);
    }

    protected void assertSplitHasExternalDtoContract(ValidatableResponseWrapper response, Long splitId) {
        Map<String, Object> split = findById(response, splitId);
        assertRequiredNumber(split, "splitId=" + splitId, "id");
        assertRequiredText(split, "splitId=" + splitId, "name");
        assertRequiredObject(split, "splitId=" + splitId, "status");
        Assertions.assertEquals("IN_PROGRESS", nestedText(split, "status", "code"),
                "Некорректный status.code для splitId=" + splitId);
        assertRequiredNumber(split, "splitId=" + splitId, "startDt");
        assertRequiredNumber(split, "splitId=" + splitId, "endDt");
        assertRequiredNumber(split, "splitId=" + splitId, "createdDt");
        assertRequiredNumber(split, "splitId=" + splitId, "updatedDt");
        assertRequiredText(split, "splitId=" + splitId, "salt");
        assertRequiredNumber(split, "splitId=" + splitId, "quantum");
        assertRequiredAnyText(split, "splitId=" + splitId, "hashFunction", "hashAlgorithm");
        assertRequiredField(split, "splitId=" + splitId, "actionType");
        assertRequiredNumber(split, "splitId=" + splitId, "version");
        assertRequiredArray(split, "splitId=" + splitId, "groups");
        assertSplitGroupsContract(asList(split.get("groups")), splitId);
    }

    protected void assertEveryItemHasStatus(ValidatableResponseWrapper response, String status) {
        for (Map<String, Object> item : items(response)) {
            Assertions.assertEquals(status, nestedText(item, "status", "code"),
                    "Все элементы running-ответа должны быть в статусе " + status);
        }
    }

    private ValidatableResponseWrapper waitForExperimentPresence(RestService restService,
                                                                 Long experimentId,
                                                                 boolean shouldBePresent) {
        long deadline = System.currentTimeMillis() + RUNNING_CACHE_WAIT_TIMEOUT_MS;
        ValidatableResponseWrapper response = null;
        while (System.currentTimeMillis() <= deadline) {
            response = getRunningExperiments(restService);
            if (idsFromArray(response).contains(experimentId) == shouldBePresent) {
                return response;
            }
            waitBeforeNextRunningCachePoll(deadline);
        }

        Assertions.fail((shouldBePresent ? "Не дождались появления id=" : "Не дождались удаления id=")
                + experimentId + " в running-cache");
        return response;
    }

    private void cleanupExperiment(RestService restService, Long id) {
        try {
            if (createdExperimentStatuses.get(id) == ExperimentStatus.IN_PROGRESS) {
                changeExperimentStatus(restService, id, ExperimentStatus.STOPPED);
            }
            restService.restClient()
                    .delete(this::jsonSpec, BASE_URL + "/api/v1/experiments/" + id)
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
    }

    private void cleanupSplit(RestService restService, Long id) {
        SplitData original = createdSplitParams.get(id);
        try {
            if (original != null && "IN_PROGRESS".equals(original.status())) {
                SplitData completed = original.withStatus("COMPLETED");
                restService.restClient()
                        .put(spec -> jsonSpec(spec).queryParam("id", id).body(completed.toJson()),
                                BASE_URL + "/api/v1/experiments/refbook/splits")
                        .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
            }
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }

        try {
            restService.restClient()
                    .delete(spec -> jsonSpec(spec).queryParam("id", id),
                            BASE_URL + "/api/v1/experiments/refbook/splits")
                    .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
    }

    private List<ExperimentStatus> routeExperimentStatuses(ExperimentStatus currentStatus,
                                                           ExperimentStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return List.of();
        }

        if (targetStatus == ExperimentStatus.AGREED) {
            return currentStatus == ExperimentStatus.AGREEMENT
                    ? List.of(ExperimentStatus.AGREED)
                    : List.of(ExperimentStatus.AGREEMENT, ExperimentStatus.AGREED);
        }

        if (targetStatus == ExperimentStatus.IN_PROGRESS) {
            if (currentStatus == ExperimentStatus.DRAFT) {
                return List.of(ExperimentStatus.AGREEMENT, ExperimentStatus.AGREED, ExperimentStatus.IN_PROGRESS);
            }
            if (currentStatus == ExperimentStatus.AGREEMENT) {
                return List.of(ExperimentStatus.AGREED, ExperimentStatus.IN_PROGRESS);
            }
            return List.of(ExperimentStatus.IN_PROGRESS);
        }

        return List.of(targetStatus);
    }

    private RequestSpecification jsonSpec(RequestSpecification spec) {
        return spec.config(P12_CONFIG)
                .contentType(ContentType.JSON)
                .header("Content-Type", "application/json")
                .accept("*/*");
    }

    private static String resolveBaseUrl() {
        String propertyValue = System.getProperty(BASE_URL_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(BASE_URL_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return DEFAULT_BASE_URL;
    }

    private boolean isV2CjExperimentsToggleMarkedEnabled() {
        String propertyValue = System.getProperty(V2_CJ_TOGGLE_ENV);
        String envValue = System.getenv(V2_CJ_TOGGLE_ENV);
        return Boolean.parseBoolean(propertyValue != null ? propertyValue : envValue);
    }

    private String idsCsv(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> idsFromArray(ValidatableResponseWrapper response) {
        return items(response).stream()
                .map(item -> asLong(item.get("id")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(ValidatableResponseWrapper response) {
        return response.toJsonPath().getList("$");
    }

    private Map<String, Object> findById(ValidatableResponseWrapper response, Long id) {
        for (Map<String, Object> item : items(response)) {
            if (asLong(item.get("id")) == id) {
                return item;
            }
        }
        return Assertions.fail("Не найден id=" + id + " в ответе");
    }

    private void assertNoDuplicateIds(List<Long> actualIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(actualIds);
        Assertions.assertEquals(actualIds.size(), uniqueIds.size(),
                "В running-ответе не должно быть дублей по id. Фактические id=" + actualIds);
    }

    private void assertExperimentGroupsContract(List<?> groups, Long experimentId) {
        boolean hasGroupConfig = false;
        for (int index = 0; index < groups.size(); index++) {
            Map<String, Object> group = asMap(groups.get(index));
            String context = "experimentId=" + experimentId + ".groups[" + index + "]";
            assertRequiredText(group, context, "code");
            assertRequiredText(group, context, "name");
            assertRequiredField(group, context, "actionType");
            assertRequiredNumber(group, context, "shareFrom");
            assertRequiredAnyNumber(group, context, "shareTo", "size");

            Object groupConfig = group.get("groupConfig");
            if (groupConfig instanceof Map<?, ?>) {
                hasGroupConfig = true;
            }
        }

        Assertions.assertTrue(hasGroupConfig,
                "Хотя бы одна группа experiment DTO должна содержать groupConfig");
    }

    private void assertSplitGroupsContract(List<?> groups, Long splitId) {
        for (int index = 0; index < groups.size(); index++) {
            Map<String, Object> group = asMap(groups.get(index));
            String context = "splitId=" + splitId + ".groups[" + index + "]";
            assertRequiredText(group, context, "code");
            assertOptionalText(group, context, "name");
            assertRequiredNumber(group, context, "shareFrom");
            assertRequiredAnyNumber(group, context, "shareTo", "size");
        }
    }

    private void assertRequiredField(Map<String, Object> node, String context, String field) {
        Assertions.assertTrue(node.containsKey(field) && node.get(field) != null,
                "В DTO отсутствует обязательное поле " + context + "." + field);
    }

    private void assertRequiredText(Map<String, Object> node, String context, String field) {
        assertRequiredField(node, context, field);
        Object value = node.get(field);
        Assertions.assertTrue(value instanceof String text && !text.isBlank(),
                "Поле " + context + "." + field + " должно быть непустой строкой");
    }

    private void assertRequiredAnyText(Map<String, Object> node, String context, String... fields) {
        for (String field : fields) {
            Object value = node.get(field);
            if (value instanceof String text && !text.isBlank()) {
                return;
            }
        }
        Assertions.fail("В DTO ожидается одно из непустых текстовых полей "
                + String.join("/", fields) + " для " + context);
    }

    private void assertOptionalText(Map<String, Object> node, String context, String field) {
        Object value = node.get(field);
        if (value == null) {
            return;
        }
        Assertions.assertTrue(value instanceof String text && !text.isBlank(),
                "Поле " + context + "." + field + " должно быть непустой строкой");
    }

    private void assertRequiredNumber(Map<String, Object> node, String context, String field) {
        assertRequiredField(node, context, field);
        Assertions.assertTrue(node.get(field) instanceof Number,
                "Поле " + context + "." + field + " должно быть числом");
    }

    private void assertRequiredAnyNumber(Map<String, Object> node, String context, String... fields) {
        for (String field : fields) {
            if (node.get(field) instanceof Number) {
                return;
            }
        }
        Assertions.fail("В DTO ожидается одно из числовых полей "
                + String.join("/", fields) + " для " + context);
    }

    private void assertRequiredObject(Map<String, Object> node, String context, String field) {
        assertRequiredField(node, context, field);
        Assertions.assertTrue(node.get(field) instanceof Map<?, ?>,
                "Поле " + context + "." + field + " должно быть объектом");
    }

    private void assertRequiredArray(Map<String, Object> node, String context, String field) {
        assertRequiredField(node, context, field);
        Object value = node.get(field);
        Assertions.assertTrue(value instanceof List<?> list && !list.isEmpty(),
                "Поле " + context + "." + field + " должно быть непустым массивом");
    }

    private String nestedText(Map<String, Object> node, String objectField, String textField) {
        Map<String, Object> object = asMap(node.get(objectField));
        Object value = object.get(textField);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        Assertions.assertTrue(value instanceof Map<?, ?>, "Ожидали JSON object");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<?> asList(Object value) {
        Assertions.assertTrue(value instanceof List<?>, "Ожидали JSON array");
        return (List<?>) value;
    }

    private long asLong(Object value) {
        Assertions.assertTrue(value instanceof Number, "Ожидали числовой id/version");
        return ((Number) value).longValue();
    }

    private void waitBeforeNextRunningCachePoll(long deadline) {
        long sleepMs = Math.min(RUNNING_CACHE_WAIT_POLL_MS, Math.max(0, deadline - System.currentTimeMillis()));
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание running-cache было прервано", e);
        }
    }

    protected enum ExperimentStatus {
        DRAFT("DRAFT"),
        AGREEMENT("AGREEMENT"),
        AGREED("AGREED"),
        IN_PROGRESS("IN_PROGRESS"),
        STOPPED("STOPPED");

        private final String value;

        ExperimentStatus(String value) {
            this.value = value;
        }
    }

    private record SplitData(long id,
                             String type,
                             String name,
                             String desc,
                             String status,
                             String salt,
                             String hashAlgorithm,
                             int quantum,
                             int actionType,
                             long startDt,
                             long endDt,
                             boolean autoStart,
                             boolean autoStop,
                             long realStartDt,
                             long realEndDt,
                             long createdDt,
                             int createdBy,
                             long updatedDt,
                             int updatedBy,
                             int priority,
                             int version,
                             String groups) {

        static SplitData create(String status, int version) {
            String unique = UUID.randomUUID().toString().substring(0, 8);
            return new SplitData(
                    0,
                    "SCG555",
                    "AQA_EXPLAB_2696_SPLIT_" + unique,
                    "Статичная Контрольная Группа 2025",
                    status,
                    "AQA2696S" + unique,
                    "MURMURHASH",
                    10000,
                    3,
                    1704067200000L,
                    1729484318L,
                    false,
                    false,
                    1734713634687L,
                    0,
                    1688405817844L,
                    1,
                    1734713641334L,
                    1004,
                    10,
                    version,
                    "[{\"groupCode\": \"A\",\"shareFrom\": 0,\"size\": 75}]"
            );
        }

        SplitData withId(long nextId) {
            return new SplitData(nextId, type, name, desc, status, salt, hashAlgorithm, quantum, actionType,
                    startDt, endDt, autoStart, autoStop, realStartDt, realEndDt, createdDt, createdBy,
                    updatedDt, updatedBy, priority, version, groups);
        }

        SplitData withStatus(String nextStatus) {
            return new SplitData(id, type, name, desc, nextStatus, salt, hashAlgorithm, quantum, actionType,
                    startDt, endDt, autoStart, autoStop, realStartDt, realEndDt, createdDt, createdBy,
                    updatedDt, updatedBy, priority, version, groups);
        }

        String toJson() {
            return """
                    {
                      "id": %d,
                      "type": "%s",
                      "name": "%s",
                      "desc": "%s",
                      "status": "%s",
                      "salt": "%s",
                      "hashAlgorithm": "%s",
                      "quantum": %d,
                      "actionType": %d,
                      "startDt": %d,
                      "endDt": %d,
                      "autoStart": %s,
                      "autoStop": %s,
                      "realStartDt": %d,
                      "realEndDt": %d,
                      "createdDt": %d,
                      "createdBy": %d,
                      "updatedDt": %d,
                      "updatedBy": %d,
                      "priority": %d,
                      "version": %d,
                      "groups": "%s"
                    }
                    """.formatted(id, type, name, desc, status, salt, hashAlgorithm, quantum, actionType,
                    startDt, endDt, autoStart, autoStop, realStartDt, realEndDt, createdDt, createdBy,
                    updatedDt, updatedBy, priority, version, groups.replace("\"", "\\\""));
        }
    }
}
