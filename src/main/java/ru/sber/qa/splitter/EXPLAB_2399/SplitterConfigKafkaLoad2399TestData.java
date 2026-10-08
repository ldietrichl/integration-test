package ru.sber.qa.splitter.EXPLAB_2399;

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

import java.util.List;
import java.util.UUID;

final class SplitterConfigKafkaLoad2399TestData {

    static final String SPLITTING_POINT = "MAPPER";
    static final int SO_CONFIG_VERSION = 1;
    static final String MATCH_PARAM_CODE = "explab2399Param";
    static final String REQUEST_PARAM_CODE = "explab2399RequestParam";

    private SplitterConfigKafkaLoad2399TestData() {
    }

    static LoadConfigRequestDto validObjectParamConfig(long version, int expId, String markerValue) {
        return config(version, true, experiment(expId,
                "EXPLAB-2399-SALT-" + expId,
                condition(1, objectRule(MATCH_PARAM_CODE, markerValue)),
                group("A", 0, 10000, result(1, "0"))));
    }

    static LoadConfigRequestDto oldVersionConfig(long version, int expId, String markerValue) {
        return config(version, false, experiment(expId,
                "EXPLAB-2399-OLD-SALT-" + expId,
                condition(1, objectRule(MATCH_PARAM_CODE, markerValue)),
                group("A", 0, 10000, result(1, "0"))));
    }

    static LoadConfigRequestDto requestParamsConfig(long version, int expId) {
        return config(version, true, experiment(expId,
                "EXPLAB-2399-REQUEST-PARAMS-SALT",
                condition(1, requestRule(REQUEST_PARAM_CODE, "2399")),
                group("A", 0, 10000, result(1, "0"))));
    }

    static LoadConfigRequestDto invalidNoTrafficRulesConfig(long version, int expId) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(expId)
                .purpose("DCG")
                .salt("EXPLAB-2399-INVALID-NO-RULES")
                .objectSelectConditions(List.of())
                .groups(List.of(group("A", 0, 10000, result(1, "0"))))
                .build();

        return config(version, true, experiment);
    }

    static SplitterPrecalcRequestDto precalcRequest(String uniqueConfigurationId, String markerValue) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_CONFIG_VERSION)
                .splittingObjects(List.of(SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(new SplitterPrecalcParamDto(
                                MATCH_PARAM_CODE,
                                List.of(markerValue),
                                "STRING")))
                        .build()))
                .build();
    }

    static String invalidStructureMessage(String messageId, long configVersion) {
        return "{\n"
                + "  \"messageId\": \"" + messageId + "\",\n"
                + "  \"requestId\": \"" + UUID.randomUUID() + "\",\n"
                + "  \"configVersion\": " + configVersion + ",\n"
                + "  \"forceConfigLoad\": true,\n"
                + "  \"splittingPointCode\": \"" + SPLITTING_POINT + "\",\n"
                + "  \"splittingConfig\": {\n"
                + "    \"experiments\": \"EXPLAB-2399-not-an-array\"\n"
                + "  }\n"
                + "}";
    }

    private static LoadConfigRequestDto config(long version, boolean forceConfigLoad, ExperimentDto experiment) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(forceConfigLoad)
                .splittingPointCode(SPLITTING_POINT)
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(List.of(experiment))
                        .build())
                .build();
    }

    private static ExperimentDto experiment(int id,
                                            String salt,
                                            ObjectSelectConditionDto condition,
                                            GroupDto group) {
        return ExperimentDto.builder()
                .id(id)
                .purpose("DCG")
                .salt(salt)
                .objectSelectConditions(List.of(condition))
                .groups(List.of(group))
                .build();
    }

    private static ObjectSelectConditionDto condition(int id, RuleDto rule) {
        return ObjectSelectConditionDto.builder()
                .id(id)
                .rules(List.of(List.of(rule)))
                .build();
    }

    private static RuleDto objectRule(String paramCode, String expectedValue) {
        return rule(paramCode, "SPLITTING_OBJECTS", expectedValue);
    }

    private static RuleDto requestRule(String paramCode, String expectedValue) {
        return rule(paramCode, "REQUEST_PARAMS", expectedValue);
    }

    private static RuleDto rule(String paramCode, String paramSource, String expectedValue) {
        return RuleDto.builder()
                .dataType("STRING")
                .paramCode(paramCode)
                .paramSource(paramSource)
                .operatorCode("equal")
                .values(List.of(expectedValue))
                .build();
    }

    private static GroupDto group(String code, int from, int to, SplittingResultDto result) {
        return GroupDto.builder()
                .code(code)
                .shares(List.of(ShareDto.builder()
                        .shareFrom(from)
                        .shareTo(to)
                        .build()))
                .splittingResults(List.of(result))
                .build();
    }

    private static SplittingResultDto result(int conditionId, String actionType) {
        return SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(List.of(ParamDto.builder()
                        .paramCode("actionType")
                        .paramValues(List.of(actionType))
                        .dataType("INTEGER")
                        .build()))
                .build();
    }
}
