package dto.experiments.v2;

import feeders.ExperimentsFeeder;

import java.util.List;

public class ExperimentV2PostRequestDtoBuilder {

    public static ExperimentV2PostRequestDto buildDefaultExperimentV2PostRequestDto() {
        return ExperimentV2PostRequestDto.builder()
                .name(ExperimentsFeeder.generateAqaMalilId())
                .splittingPointCode("MAPPER")
                .expTemplateCode("MODEL_TEST")
                .salt(ExperimentsFeeder.generateSalt())
                .creator("Тестович Иван_" + ExperimentsFeeder.generateSalt())
                .hypothesisDesc("AQA_malil_48F0w023_" + ExperimentsFeeder.generateSalt())
                .hashAlgorithm("MURMURHASH")
                .objectSelectConditions(List.of(buildDefaultObjectSelectCondition()))
                .quantum(10000L)
                .startDt(ExperimentsFeeder.startDt)
                .endDt(ExperimentsFeeder.endDt)
                .autoStart(false)
                .autoStop(false)
                .updateMetrics(false)
                .metrics(List.of(buildDefaultMetric()))
                .experimentGroups(List.of(buildDefaultExperimentGroups()))
                .build();
    }

    public static ExperimentV2PostRequestDto.Metric buildDefaultMetric() {
        return ExperimentV2PostRequestDto.Metric.builder()
                .id(7L)
                .targetFlag(true)
                .paramValues(List.of(ExperimentV2PostRequestDto.Metric.ParamValues.builder()
                        .id(1L)
                        .value(ExperimentV2PostRequestDto.Metric.ParamValues.Value.builder()
                                .dtInputType("EVENT_LINK")
                                .value("LAST_M_DAY_MIN_COMM_DT")
                                .shift("1")
                                .build())
                        .build()))
                .build();
    }

    public static ExperimentV2PostRequestDto.ObjectSelectCondition buildDefaultObjectSelectCondition() {
        return ExperimentV2PostRequestDto.ObjectSelectCondition.builder()
                .number(1)
                .userCondition("cjId in (42001)")
                .formCode("MAPPER_OBJECT_SELECT")
                .rules(List.of(List.of(
                        ExperimentV2PostRequestDto.ObjectSelectCondition.Rule.builder()
                                .dataType("INTEGER")
                                .parameterCode("cjId")
                                .operatorCode("in")
                                .values(List.of("42001"))
                                .build())))
                .build();
    }

    public static ExperimentV2PostRequestDto.ExperimentGroups buildDefaultExperimentGroups() {
        return ExperimentV2PostRequestDto.ExperimentGroups.builder()
                .name("Тестовая группа_" + ExperimentsFeeder.generateSalt())
                .symbolName("A")
                .shares(List.of(buildDefaultShare()))
                .size(5000)
                .baseline(true)
                .splittingResults(List.of(ExperimentV2PostRequestDto.ExperimentGroups.SplittingResult.builder()
                        .conditionNumber(1)
                        .userResult("actionType: 0")
                        .resultParams(List.of(
                                ExperimentV2PostRequestDto.ExperimentGroups.SplittingResult.ResultParam.builder()
                                        .paramCode("actionType")
                                        .paramValue(List.of("0"))
                                        .dataType("INTEGER")
                                        .build()
                        ))
                        .build()))
                .build();
    }

    public static ExperimentV2PostRequestDto.ExperimentGroups.Share buildDefaultShare() {
        return ExperimentV2PostRequestDto.ExperimentGroups.Share.builder()
                .shareFrom(0)
                .shareTo(5000)
                .build();
    }
}
