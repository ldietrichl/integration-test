package ru.sber.qa.splitter.EXPLAB_2538;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.common.ParamDto;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.config.ShareDto;
import dto.splitter.config.SplittingConfigDto;
import dto.splitter.config.SplittingResultDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.UUID;

import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * EXPLAB-2538: точечная проверка gap из плана — predcalc должен сохранять тот же
 * conditionId и те же groupResultParams, которые runtime split выбирает по matched condition.
 *
 * REST-only test. Требует совместимую ConfigMap mapper-current.yml на стенде.
 */
@AnyConfigLoadMode
public class Explab2538PrecalcConditionBindingFlowTest extends AbstractAnalyticSplitterFlowTest {

    private static final int SO_CONFIG_VERSION = 1;
    private static final int EXP_ID = 253801;
    private static final String OBJECT_ID = "25380000-0000-0000-0000-000000000001";
    private static final String MARKER_PARAM = "explab2538Marker";

    @CriticalRegression
    @Test
    @AnalyticTag("EXPLAB-2538: pre-calculate должен сохранить точную связку conditionId/groupResultParams для выбранного matched condition.")
    @DisplayName("EXPLAB-2538-01. pre-calculate сохраняет conditionId и groupResultParams выбранного condition")
    void precalcShouldKeepConditionIdAndGroupResultParamsSelectedByRuntimeBinding() {
        long version = SplitterVersionProvider.next();
        String uniqueConfigurationId = "explab-2538-" + version + "-uc";
        LoadConfigRequestDto config = explab2538Config(version);

        SplitRequestDto runtimeSplit = splitRequest(
                "EXPLAB-2538-RUNTIME-" + version,
                object(OBJECT_ID,
                        param("segment", "VIP", "STRING"),
                        param("channel", "PUSH", "STRING"),
                        param("template", "TEMPLATE-A", "STRING")));

        SplitterPrecalcRequestDto precalc = precalcRequest(
                uniqueConfigurationId,
                precalcParam("segment", "VIP"),
                precalcParam("channel", "PUSH"),
                precalcParam("template", "TEMPLATE-A"));

        SplitRequestDto splitAfterPrecalc = splitRequest(
                "EXPLAB-2538-PRECALC-" + version,
                objectWithUniqueId(uniqueConfigurationId, OBJECT_ID,
                        param("runtimeOnly", "DO_NOT_MATCH_RUNTIME_CONDITIONS", "STRING")));

        getFlowWithRest()
                .step("Загружаем config EXPLAB-2538: один experiment, одна group A, три matched conditions и разные groupResultParams", flow ->
                        loadConfigStep(flow, config))
                .step("Runtime split выбирает минимальный matched conditionId=1 и marker=C1", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, runtimeSplit));
                    assertExpectedBinding(response);
                })
                .step("Выполняем pre-calculate для тех же параметров объекта", flow ->
                        shouldHaveSoConfigVersion(
                                shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                                SO_CONFIG_VERSION))
                .step("Split после predcalc по uniqueConfigurationId возвращает ту же связку conditionId/groupResultParams", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitAfterPrecalc));
                    assertExpectedBinding(response);
                })
                .run();
    }

    private LoadConfigRequestDto explab2538Config(long version) {
        ObjectSelectConditionDto condition1 = condition(1, "segment", "VIP");
        ObjectSelectConditionDto condition2 = condition(2, "channel", "PUSH");
        ObjectSelectConditionDto condition3 = condition(3, "template", "TEMPLATE-A");

        ExperimentDto experiment = ExperimentDto.builder()
                .id(EXP_ID)
                .purpose("DCG")
                .salt("EXPLAB-2538-SALT")
                .objectSelectConditions(List.of(condition1, condition2, condition3))
                .groups(List.of(GroupDto.builder()
                        .code("A")
                        .shares(List.of(ShareDto.builder().shareFrom(0).shareTo(10000).build()))
                        .splittingResults(List.of(
                                result(1, "0", "C1"),
                                result(2, "1", "C2"),
                                result(3, "5", "C3")))
                        .build()))
                .build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of(experiment))
                        .build())
                .build();
    }

    private ObjectSelectConditionDto condition(int id, String paramCode, String expectedValue) {
        return ObjectSelectConditionDto.builder()
                .id(id)
                .rules(List.of(List.of(RuleDto.builder()
                        .dataType("STRING")
                        .paramCode(paramCode)
                        .paramSource("SPLITTING_OBJECTS")
                        .operatorCode("equal")
                        .values(List.of(expectedValue))
                        .build())))
                .build();
    }

    private SplittingResultDto result(int conditionId, String actionType, String marker) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(List.of(
                        param("actionType", actionType, "INTEGER"),
                        param(MARKER_PARAM, marker, "STRING")))
                .build();
    }

    private static SplitterPrecalcParamDto precalcParam(String code, String value) {
        return new SplitterPrecalcParamDto(code, List.of(value), "STRING");
    }

    private static SplitterPrecalcRequestDto precalcRequest(String uniqueConfigurationId,
                                                            SplitterPrecalcParamDto... params) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_CONFIG_VERSION)
                .splittingObjects(List.of(SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(params))
                        .build()))
                .build();
    }

    private void assertExpectedBinding(ValidatableResponseWrapper response) {
        assertObjectHasMainAndAll(response, OBJECT_ID);
        assertMainExp(response, OBJECT_ID, EXP_ID);
        assertMainGroup(response, OBJECT_ID, "A");
        assertMainConditionId(response, OBJECT_ID, 1);
        assertMainActionType(response, OBJECT_ID, "0");
        assertAllExpConditionId(response, OBJECT_ID, EXP_ID, 1);
        assertAllExpActionType(response, OBJECT_ID, EXP_ID, "0");
        assertGroupResultParam(response, OBJECT_ID, "MAIN", EXP_ID, MARKER_PARAM, "C1");
        assertGroupResultParam(response, OBJECT_ID, "ALL", EXP_ID, MARKER_PARAM, "C1");
    }

    private void assertGroupResultParam(ValidatableResponseWrapper response,
                                        String objectId,
                                        String ruleCode,
                                        long expId,
                                        String paramCode,
                                        String expectedValue) {
        var root = util.splittercheck.SplitterResponseReader.snapshot(response)
                .requireJsonBody("Ожидали JSON body для проверки " + paramCode);
        var splittingResults = root.path("splittingResults");
        for (var objectResult : splittingResults) {
            if (!objectId.equals(objectResult.path("objectId").asText(null))) {
                continue;
            }
            for (var ruleResult : objectResult.path("objectResults")) {
                if (!ruleCode.equals(ruleResult.path("ruleCode").asText(null))) {
                    continue;
                }
                for (var resultExp : ruleResult.path("resultExps")) {
                    if (resultExp.path("expId").asLong(Long.MIN_VALUE) != expId) {
                        continue;
                    }
                    for (var param : resultExp.path("groupResultParams")) {
                        if (paramCode.equals(param.path("paramCode").asText(null))) {
                            var values = param.path("paramValues");
                            org.junit.jupiter.api.Assertions.assertTrue(values.isArray() && !values.isEmpty(),
                                    "Ожидали непустой paramValues для " + paramCode + body(response));
                            org.junit.jupiter.api.Assertions.assertEquals(expectedValue, values.get(0).asText(null),
                                    "Некорректный " + paramCode + " в " + ruleCode + body(response));
                            return;
                        }
                    }
                }
            }
        }
        org.junit.jupiter.api.Assertions.fail("Не найден " + paramCode + " в " + ruleCode + " для expId=" + expId + body(response));
    }
}
