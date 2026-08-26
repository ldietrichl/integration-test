package ru.sber.qa.splitter.EXPLAB_2400;

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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class SplitterConfigLoad2400TestData {

    static final String SPLITTING_POINT = "MAPPER";
    static final String PARAM_CODE = "explab2400Marker";
    static final String REQUEST_PARAM_CODE = "explab2400RequestMarker";
    static final String VALUE_EXP_1 = "EXP_1";
    static final String VALUE_EXP_2 = "EXP_2";

    private SplitterConfigLoad2400TestData() {
    }

    static LoadConfigRequestDto singleExperimentConfig(long version,
                                                       boolean forceConfigLoad,
                                                       int expId,
                                                       String markerValue) {
        return config(version, forceConfigLoad, experiment(expId, PARAM_CODE, "SPLITTING_OBJECTS", markerValue));
    }

    static LoadConfigRequestDto twoExperimentConfig(long version,
                                                    boolean forceConfigLoad,
                                                    int firstExpId,
                                                    String firstMarker,
                                                    int secondExpId,
                                                    String secondMarker) {
        return config(version,
                forceConfigLoad,
                experiment(firstExpId, PARAM_CODE, "SPLITTING_OBJECTS", firstMarker),
                experiment(secondExpId, PARAM_CODE, "SPLITTING_OBJECTS", secondMarker));
    }

    static LoadConfigRequestDto requestParamsConfig(long version, int expId) {
        return config(version,
                true,
                experiment(expId, REQUEST_PARAM_CODE, "REQUEST_PARAMS", "2400"));
    }

    static LoadConfigRequestDto invalidWithoutSaltOrLayerConfig(long version, int expId) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(expId)
                .purpose("DCG")
                .salt(null)
                .layerId(null)
                .layerPriority(null)
                .objectSelectConditions(List.of(condition(1,
                        rule(PARAM_CODE, "SPLITTING_OBJECTS", VALUE_EXP_1))))
                .groups(List.of(group("A", 1, "0")))
                .build();
        return config(version, true, experiment);
    }

    static SplitterPrecalcRequestDto precalcRequest(int soConfigVersion,
                                                    SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    static SplitterPrecalcObjectDto precalcObject(String uniqueConfigurationId, String markerValue) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(new SplitterPrecalcParamDto(
                        PARAM_CODE,
                        List.of(markerValue),
                        "STRING")))
                .build();
    }

    private static LoadConfigRequestDto config(long version,
                                               boolean forceConfigLoad,
                                               ExperimentDto... experiments) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(forceConfigLoad)
                .splittingPointCode(SPLITTING_POINT)
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(Arrays.asList(experiments))
                        .build())
                .build();
    }

    private static ExperimentDto experiment(int expId,
                                            String paramCode,
                                            String paramSource,
                                            String markerValue) {
        return ExperimentDto.builder()
                .id(expId)
                .purpose("DCG")
                .salt("EXPLAB-2400-SALT-" + expId)
                .objectSelectConditions(List.of(condition(1,
                        rule(paramCode, paramSource, markerValue))))
                .groups(List.of(group("A", 1, "0")))
                .build();
    }

    private static ObjectSelectConditionDto condition(int id, RuleDto rule) {
        return ObjectSelectConditionDto.builder()
                .id(id)
                .rules(List.of(List.of(rule)))
                .build();
    }

    private static RuleDto rule(String paramCode, String paramSource, String markerValue) {
        return RuleDto.builder()
                .dataType("STRING")
                .paramCode(paramCode)
                .paramSource(paramSource)
                .operatorCode("equal")
                .values(List.of(markerValue))
                .build();
    }

    private static GroupDto group(String code, int conditionId, String actionType) {
        return GroupDto.builder()
                .code(code)
                .shares(List.of(ShareDto.builder()
                        .shareFrom(0)
                        .shareTo(10000)
                        .build()))
                .splittingResults(List.of(SplittingResultDto.builder()
                        .conditionId(conditionId)
                        .resultParams(List.of(ParamDto.builder()
                                .paramCode("actionType")
                                .paramValues(List.of(actionType))
                                .dataType("INTEGER")
                                .build()))
                        .build()))
                .build();
    }
}
