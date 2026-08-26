package request.splitter;

import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;

import java.util.Collections;
import java.util.List;

import static request.splitter.SplitterDtoFactory.config;
import static request.splitter.SplitterDtoFactory.condition;
import static request.splitter.SplitterDtoFactory.group;
import static request.splitter.SplitterDtoFactory.loadConfigRequest;
import static request.splitter.SplitterDtoFactory.param;
import static request.splitter.SplitterDtoFactory.result;
import static request.splitter.SplitterDtoFactory.rule;
import static request.splitter.SplitterDtoFactory.share;

public final class SplitterConfigTestDataFactory {

    private SplitterConfigTestDataFactory() {
    }

    public static LoadConfigRequestDto validSingleExperimentConfig(long version, boolean forceConfigLoad) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(101)
                .purpose("DCG")
                .salt("SALT-101")
                .layerId(null)
                .layerPriority(null)
                .objectSelectConditions(List.of(matchCondition()))
                .groups(List.of(groupWithActionType(1)))
                .build();

        return buildConfig(version, forceConfigLoad, List.of(experiment));
    }

    public static LoadConfigRequestDto emptyExperimentsConfig(long version) {
        return buildConfig(version, false, Collections.emptyList());
    }

    public static LoadConfigRequestDto validLayerExperimentConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(108)
                .purpose("LAYER")
                .salt(null)
                .layerId(10)
                .layerPriority(1)
                .objectSelectConditions(List.of(matchCondition()))
                .groups(List.of(groupWithActionType(1)))
                .build();

        return buildConfig(version, false, List.of(experiment));
    }

    public static LoadConfigRequestDto invalidNoSaltOrLayerConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(901)
                .purpose(null)
                .salt(null)
                .layerId(null)
                .layerPriority(null)
                .objectSelectConditions(List.of(matchCondition()))
                .groups(List.of(groupWithActionType(1)))
                .build();

        return buildConfig(version, false, List.of(experiment));
    }

    public static LoadConfigRequestDto invalidNoConditionsConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(902)
                .purpose(null)
                .salt("SALT-902")
                .layerId(null)
                .layerPriority(null)
                .objectSelectConditions(Collections.emptyList())
                .groups(List.of(GroupDto.builder()
                        .code("A")
                        .shares(List.of(share(0, 10000)))
                        .splittingResults(Collections.emptyList())
                        .build()))
                .build();

        return buildConfig(version, false, List.of(experiment));
    }

    public static LoadConfigRequestDto invalidRangesConfig(long version) {
        ExperimentDto experiment = ExperimentDto.builder()
                .id(903)
                .purpose("DCG")
                .salt("SALT-903")
                .layerId(null)
                .layerPriority(null)
                .objectSelectConditions(List.of(matchCondition()))
                .groups(List.of(
                        GroupDto.builder()
                                .code("A")
                                .shares(List.of(share(0, 7000)))
                                .splittingResults(Collections.emptyList())
                                .build(),
                        GroupDto.builder()
                                .code("B")
                                .shares(List.of(share(6000, 10000)))
                                .splittingResults(Collections.emptyList())
                                .build()
                ))
                .build();

        return buildConfig(version, false, List.of(experiment));
    }

    private static LoadConfigRequestDto buildConfig(long version, boolean forceConfigLoad, List<ExperimentDto> experiments) {
        return loadConfigRequest(version, forceConfigLoad, config(experiments));
    }

    private static ObjectSelectConditionDto matchCondition() {
        return condition(1, List.of(List.of(rule("STRING", "sellingProductId", "SPLITTING_OBJECTS", "equal", "1-XCDV6TA"))));
    }

    private static GroupDto groupWithActionType(int actionType) {
        return group("A", List.of(share(0, 10000)), List.of(result(1, param("actionType", String.valueOf(actionType), "INTEGER"))));
    }
}
