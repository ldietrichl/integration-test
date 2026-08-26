package ru.sber.qa.splitter.EXPLAB_2834;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2834. API config load")
public class SplitterConfigLoadRules2834FlowTest extends AbstractSplitterV9FlowTest {

    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
    private static final long EMPTY_RULES_EXP_ID = 283401L;
    private static final long REJECTED_EQUAL_VERSION_EXP_ID = 283402L;
    private static final long VALID_EXP_ID = 283403L;
    private static final long REJECTED_EXP_ID = 283404L;
    private static final long FORCE_EXP_ID = 283405L;
    private static final long IN_EMPTY_EXP_ID = 283406L;
    private static final long IN_ONE_EXP_ID = 283407L;
    private static final long LAYER_EXP_ID = 283408L;
    private static final String SALT = "EXPLAB-2834-SALT";
    private static final String OBJECT_ID = "explab-2834-object";

    @Test
    @DisplayName("EXPLAB-2834-API-01. Валидный config новой версии загружается и становится активным")
    void validNewVersionConfigShouldLoadAndBecomeActiveForSplit() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                simpleExperiment((int) VALID_EXP_ID, "1", "OK"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-01",
                objectWithUniqueId("explab-2834-api-01-uid",
                        OBJECT_ID,
                        param("marker", "OK", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config новой версии", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем базовый response contract load и последующий split", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", VALID_EXP_ID);
                })
                .run();
    }

    @Disabled("Требуется отдельный стендовый профиль с splitter.config.api-config-load=false")
    @Test
    @DisplayName("EXPLAB-2834-API-02. Manual/env: API-загрузка выключена")
    void apiLoadDisabledShouldRejectConfigWithoutChangingActiveVersion() {
    }

    @Test
    @DisplayName("EXPLAB-2834-API-03. Старая версия без forceConfigLoad не перезаписывает активный config")
    void olderVersionWithoutForceShouldNotOverrideActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long oldVersion = SplitterVersionProvider.nextOlderThan(activeVersion);
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "ACTIVE"));
        LoadConfigRequestDto oldConfig = config(oldVersion,
                false,
                simpleExperiment((int) REJECTED_EXP_ID, "6", "ACTIVE"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-03",
                objectWithUniqueId("explab-2834-api-03-uid",
                        OBJECT_ID,
                        param("marker", "ACTIVE", "STRING")));

        getFlowWithRest()
                .step("Загружаем активную версию N", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Пытаемся загрузить N-1 с forceConfigLoad=false", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), oldConfig));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа OLD_VERSION");
                    assertEquals("OLD_VERSION", root.path("result").asText(null), body(response));
                    assertEquals(activeVersion, root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                })
                .step("Проверяем, что активным остался config версии N", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", VALID_EXP_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-04. forceConfigLoad=true позволяет загрузить версию ниже активной")
    void forcedOldVersionShouldOverrideActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long oldVersion = SplitterVersionProvider.nextOlderThan(activeVersion);
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "FORCE"));
        LoadConfigRequestDto forcedConfig = config(oldVersion,
                true,
                simpleExperiment((int) FORCE_EXP_ID, "7", "FORCE"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-04",
                object(OBJECT_ID, param("marker", "FORCE", "STRING")));

        getFlowWithRest()
                .step("Загружаем активную версию N", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Загружаем N-1 с forceConfigLoad=true", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, forcedConfig))
                .step("Проверяем, что активной стала forced-версия", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, oldVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", FORCE_EXP_ID, "A", "A");
                    assertFirstRuleExpActionType(response, OBJECT_ID, "MAIN", "7");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-05. Config с чужим splittingPointCode не подменяет MAPPER config")
    void foreignSplittingPointShouldNotOverrideMapperConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long foreignVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "FOREIGN"));
        LoadConfigRequestDto foreignConfig = config(foreignVersion,
                true,
                simpleExperiment((int) REJECTED_EXP_ID, "6", "FOREIGN"));
        foreignConfig.setSplittingPointCode("REACTIONS");
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-05",
                objectWithUniqueId("explab-2834-api-05-uid",
                        OBJECT_ID,
                        param("marker", "FOREIGN", "STRING")));

        getFlowWithRest()
                .step("Загружаем активный MAPPER config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем config с splittingPointCode=REACTIONS в MAPPER endpoint", flow ->
                        shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), foreignConfig)))
                .step("Проверяем, что MAPPER split остался на исходной версии", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-06. REQUEST_PARAMS в rules отклоняется при включенном pre-calculate")
    void requestParamsRulesShouldBeRejectedWhenPrecalcEnabled() {
        long activeVersion = SplitterVersionProvider.next();
        long rejectedVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "REQUEST-PARAMS"));
        LoadConfigRequestDto requestParamsConfig = config(rejectedVersion,
                true,
                requestParamsExperiment((int) REJECTED_EXP_ID));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-06",
                objectWithUniqueId("explab-2834-api-06-uid",
                        OBJECT_ID,
                        param("marker", "REQUEST-PARAMS", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный текущий config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем config с paramSource=REQUEST_PARAMS", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), requestParamsConfig));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа rejection");
                    assertEquals("REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED", root.path("result").asText(null), body(response));
                    assertEquals(activeVersion, root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                })
                .step("Проверяем, что активный config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-07. API-загрузка принимает config с objectSelectConditions[].rules=[]")
    void apiLoadShouldAcceptConfigWithEmptyRulesAndActivateItForSplit() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment((int) EMPTY_RULES_EXP_ID, "0"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-07",
                objectWithUniqueId("explab-2834-empty-rules-uid",
                        OBJECT_ID,
                        param("unrelated", "value", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с пустым rules[] через API", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что загруженный config стал активным и rules=[] матчит объект", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertFirstRuleExpActionType(response, OBJECT_ID, "MAIN", "0");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", EMPTY_RULES_EXP_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-08. rules=[[param in []]] валиден, но не матчит объект")
    void inEmptyValuesRulesShouldBeValidButShouldNotMatchObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                inValuesExperiment((int) IN_EMPTY_EXP_ID, List.of()),
                inValuesExperiment((int) IN_ONE_EXP_ID, List.of("1")));
        SplitRequestDto notMatchedRequest = splitRequest("EXPLAB-2834-API-08-NOT-MATCHED",
                objectWithUniqueId("explab-2834-api-08-no",
                        OBJECT_ID,
                        param("configCommId", "2", "INTEGER")));
        SplitRequestDto matchedRequest = splitRequest("EXPLAB-2834-API-08-MATCHED",
                objectWithUniqueId("explab-2834-api-08-yes",
                        OBJECT_ID,
                        param("configCommId", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем config с values=[] и values=[1]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что values=[] не работает как rules=[]", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, notMatchedRequest);
                    assertBasicResponseContract(response, notMatchedRequest, version);
                    assertObjectEmptyOrAbsent(response, OBJECT_ID);
                })
                .step("Контрольный объект матчится только на values=[1]", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, matchedRequest);
                    assertBasicResponseContract(response, matchedRequest, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", IN_ONE_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", IN_ONE_EXP_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-09. Malformed JSON не активирует config")
    void malformedJsonShouldNotBecomeActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "MALFORMED"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-09",
                objectWithUniqueId("explab-2834-api-09-uid",
                        OBJECT_ID,
                        param("marker", "MALFORMED", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем syntactically invalid JSON", flow ->
                        flow.restCustomSteps().splitterSteps().loadConfig("{\"messageId\":\"broken\",\"splittingConfig\":{")
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активный config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-10. Config без splittingPointCode не активируется")
    void missingSplittingPointShouldNotBecomeActive() {
        long activeVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "NO-SP"));
        LoadConfigRequestDto invalidConfig = config(invalidVersion,
                true,
                simpleExperiment((int) REJECTED_EXP_ID, "6", "NO-SP"));
        invalidConfig.setSplittingPointCode(null);
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-10",
                objectWithUniqueId("explab-2834-api-10-uid",
                        OBJECT_ID,
                        param("marker", "NO-SP", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем config без splittingPointCode", flow ->
                        EndpointMode.MAPPER.load(flow.restCustomSteps(), invalidConfig)
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активный config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-11. Config без обязательной salt не активируется")
    void missingSaltShouldNotBecomeActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "NO-SALT"));
        LoadConfigRequestDto invalidConfig = config(invalidVersion,
                true,
                ExperimentDto.builder()
                        .id((int) REJECTED_EXP_ID)
                        .purpose("DCG")
                        .salt(null)
                        .objectSelectConditions(List.of(markerCondition(1, "NO-SALT")))
                        .groups(List.of(fullRangeActionTypeGroup("A", 1, "6")))
                        .build());
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-11",
                objectWithUniqueId("explab-2834-api-11-uid",
                        OBJECT_ID,
                        param("marker", "NO-SALT", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем config без salt", flow ->
                        EndpointMode.MAPPER.load(flow.restCustomSteps(), invalidConfig)
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активный config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-12. Layer-based config без salt загружается")
    void layerBasedConfigWithoutSaltShouldLoad() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                ExperimentDto.builder()
                        .id((int) LAYER_EXP_ID)
                        .purpose("DCG")
                        .salt(null)
                        .layerId(2834)
                        .layerPriority(1)
                        .objectSelectConditions(List.of(markerCondition(1, "LAYER")))
                        .groups(List.of(fullRangeActionTypeGroup("A", 1, "1")))
                        .build());
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-12",
                objectWithUniqueId("explab-2834-api-12-uid",
                        OBJECT_ID,
                        param("marker", "LAYER", "STRING")));

        getFlowWithRest()
                .step("Загружаем layer-based config без salt", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что config участвует в split", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", LAYER_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-13. Некорректные shares отклоняются и не меняют active config")
    void overlappingSharesShouldNotBecomeActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = configFor(EndpointMode.MAPPER,
                activeVersion,
                simpleExperiment((int) VALID_EXP_ID, "0", "SHARES"));
        LoadConfigRequestDto invalidConfig = config(invalidVersion,
                true,
                experiment((int) REJECTED_EXP_ID,
                        SALT,
                        List.of(markerCondition(1, "SHARES")),
                        List.of(group("A",
                                List.of(share(0, 7000), share(6000, 10000)),
                                List.of(resultWithParams(1, param("actionType", "6", "INTEGER")))))));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-13",
                objectWithUniqueId("explab-2834-api-13-uid",
                        OBJECT_ID,
                        param("marker", "SHARES", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, activeConfig))
                .step("Отправляем config с пересекающимися shares", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), invalidConfig));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа CONFIG_ERROR");
                    assertEquals("CONFIG_ERROR", root.path("result").asText(null), body(response));
                    assertTrue(root.path("resultDetails").asText("").contains("invalid group ranges"), body(response));
                    assertEquals(activeVersion, root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                })
                .step("Проверяем, что активный config не изменился", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, activeVersion);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-14. Загрузка новой версии после существующей precalc-таблицы успешна")
    void validReloadAfterExistingPrecalcTableShouldBeAccepted() {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        SplitterPrecalcRequestDto precalc = precalcRequest("explab-2834-api-14-uid",
                "PRECALC",
                SO_VERSION.incrementAndGet());
        LoadConfigRequestDto seedConfig = configFor(EndpointMode.MAPPER,
                versions[0],
                simpleExperiment((int) VALID_EXP_ID, "0", "PRECALC"));
        LoadConfigRequestDto reloadConfig = configFor(EndpointMode.MAPPER,
                versions[1],
                simpleExperiment((int) FORCE_EXP_ID, "1", "PRECALC"));

        getFlowWithRest()
                .step("Загружаем seed config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, seedConfig))
                .step("Создаем таблицу pre-calculate", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                                precalc.getSoConfigVersion()))
                .step("Загружаем новую версию после существующей precalc-таблицы", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), reloadConfig));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа load after precalc");
                    assertTrue(List.of("LOADED", "LOADED_WITH_PRECALC").contains(root.path("result").asText(null)),
                            "Ожидали LOADED или LOADED_WITH_PRECALC" + body(response));
                    assertEquals(versions[1], root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-15. Валидный config загружается, затем pre-calculate строит таблицу")
    void loadedConfigShouldAllowSubsequentPreCalculate() {
        long version = SplitterVersionProvider.next();
        SplitterPrecalcRequestDto precalc = precalcRequest("explab-2834-api-15-uid",
                "PC15",
                SO_VERSION.incrementAndGet());
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                simpleExperiment((int) VALID_EXP_ID, "0", "PC15"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-15",
                objectWithUniqueId(precalc.getSplittingObjects().get(0).getUniqueConfigurationId(),
                        OBJECT_ID,
                        param("runtimeOnly", "NO_MATCH", "STRING")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем pre-calculate после загрузки", flow ->
                        shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                                precalc.getSoConfigVersion()))
                .step("Проверяем split по uniqueConfigurationId из таблицы", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", VALID_EXP_ID, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-16. experiments=[] загружается и split возвращает пустой результат")
    void emptyExperimentsConfigShouldLoadAndReturnEmptySplitResults() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of())
                        .build())
                .build();
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-16",
                objectWithUniqueId("explab-2834-api-16-uid",
                        OBJECT_ID,
                        param("marker", "EMPTY", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с пустым experiments[]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Проверяем, что split не падает и не возвращает experiment results", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertObjectEmptyOrAbsent(response, OBJECT_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-17. Равная версия без forceConfigLoad не перезаписывает активный config")
    void equalVersionWithoutForceShouldNotOverrideActiveConfig() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto currentConfig = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment((int) EMPTY_RULES_EXP_ID, "0"));
        LoadConfigRequestDto rejectedConfig = config(version,
                false,
                emptyRulesExperiment((int) REJECTED_EQUAL_VERSION_EXP_ID, "6"));
        SplitRequestDto request = splitRequest("EXPLAB-2834-API-15",
                objectWithUniqueId("explab-2834-equal-version-uid",
                        OBJECT_ID,
                        param("unrelated", "value", "STRING")));

        getFlowWithRest()
                .step("Загружаем текущий config версии N", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, currentConfig))
                .step("Пытаемся загрузить другую конфигурацию той же версии с forceConfigLoad=false", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), rejectedConfig));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа загрузки config");
                    assertEquals("OLD_VERSION", root.path("result").asText(null), body(response));
                    assertEquals(version, root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                })
                .step("Проверяем, что активным остался первый config", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "MAIN", EMPTY_RULES_EXP_ID);
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", EMPTY_RULES_EXP_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2834-API-18. Response contract содержит result, resultDetails и currentConfigVersion")
    void loadResponseShouldExposeStableContractFields() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                simpleExperiment((int) VALID_EXP_ID, "0", "CONTRACT"));

        getFlowWithRest()
                .step("Загружаем валидный config и проверяем поля ответа", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), config));
                    JsonNode root = jsonBody(response, "Ожидали JSON body ответа load config");
                    assertEquals("LOADED", root.path("result").asText(null), body(response));
                    assertTrue(root.has("resultDetails"), "resultDetails должен присутствовать" + body(response));
                    assertEquals(version, root.path("currentConfigVersion").asLong(Long.MIN_VALUE), body(response));
                    assertNotNull(root.path("currentConfigVersion"), "currentConfigVersion должен присутствовать" + body(response));
                })
                .run();
    }

    private LoadConfigRequestDto config(long version, boolean forceConfigLoad, ExperimentDto experiment) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(forceConfigLoad)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of(experiment))
                        .build())
                .build();
    }

    private SplitterPrecalcRequestDto precalcRequest(String uniqueConfigurationId, String marker, int soConfigVersion) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(List.of(SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(new SplitterPrecalcParamDto("marker", List.of(marker), "STRING")))
                        .build()))
                .build();
    }

    private ExperimentDto emptyRulesExperiment(int id, String actionType) {
        return experiment(id,
                SALT,
                List.of(condition(1, List.of())),
                List.of(group("A",
                        List.of(share(0, 10000)),
                        List.of(resultWithParams(1, param("actionType", actionType, "INTEGER"))))));
    }

    private ExperimentDto simpleExperiment(int id, String actionType, String marker) {
        return experiment(id,
                SALT,
                List.of(markerCondition(1, marker)),
                List.of(fullRangeActionTypeGroup("A", 1, actionType)));
    }

    private ExperimentDto requestParamsExperiment(int id) {
        RuleDto rule = RuleDto.builder()
                .dataType("STRING")
                .paramCode("requestMarker")
                .paramSource("REQUEST_PARAMS")
                .operatorCode("equal")
                .values(List.of("REQ"))
                .build();
        return experiment(id,
                SALT,
                List.of(condition(1, List.of(List.of(rule)))),
                List.of(fullRangeActionTypeGroup("A", 1, "6")));
    }

    private ExperimentDto inValuesExperiment(int id, List<String> values) {
        RuleDto rule = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("configCommId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(values)
                .build();
        return experiment(id,
                SALT,
                List.of(condition(1, List.of(List.of(rule)))),
                List.of(fullRangeActionTypeGroup("A", 1, "0")));
    }

    private ObjectSelectConditionDto markerCondition(int id, String marker) {
        return condition(id, List.of(List.of(RuleDto.builder()
                .dataType("STRING")
                .paramCode("marker")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(marker))
                .build())));
    }

    private GroupDto fullRangeActionTypeGroup(String code, int conditionId, String actionType) {
        return group(code,
                Arrays.asList(share(0, 10000)),
                List.of(resultWithParams(conditionId, param("actionType", actionType, "INTEGER"))));
    }
}
