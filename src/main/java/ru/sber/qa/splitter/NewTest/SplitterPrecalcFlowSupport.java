package ru.sber.qa.splitter.NewTest;

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
import flow.RestCustomFlow;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.UUID;

import static util.SplitterPrecalcAssertions.shouldHaveJsonBody;
import static util.SplitterPrecalcAssertions.shouldHaveResponseId;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

public final class SplitterPrecalcFlowSupport {

    private static final String MATCHING_OBJECT_ID = "22222222-2222-2222-2222-222222222222";

    private SplitterPrecalcFlowSupport() {
    }

    public static void assertPrecalcAccepted(ValidatableResponseWrapper response) {
        shouldHaveJsonBody(response);
        shouldHaveResponseId(response);
        shouldHaveSoConfigVersion(response, 1);
    }

    public static ValidatableResponseWrapper loadConfig(RestCustomFlow flow, LoadConfigRequestDto request) {
        return flow.restCustomSteps().splitterSteps().loadConfig(request);
    }

    public static ValidatableResponseWrapper split(RestCustomFlow flow, SplitRequestDto request) {
        return flow.restCustomSteps().splitterSteps().split(request);
    }

    public static ValidatableResponseWrapper calculatePreliminary(RestCustomFlow flow, SplitterPrecalcRequestDto request) {
        return flow.restCustomSteps().splitterSteps().calculatePreliminary(request);
    }

    public static LoadConfigRequestDto matchingMapperConfigMessage(long version) {
        RuleDto ruleChannel = rule("INTEGER", "channel", "19", "SPLITTING_OBJECTS");
        RuleDto ruleProduct = rule("STRING", "sellingProductId", "1-AVDHSSS", "SPLITTING_OBJECTS");

        ObjectSelectConditionDto condition = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(ruleChannel, ruleProduct)))
                .build();

        GroupDto group = groupWithActionType(1, "A", "6");
        ExperimentDto experiment = ExperimentDto.builder()
                .id(2).purpose("DCG").salt("24096d2M1e").layerId(1)
                .objectSelectConditions(List.of(condition)).groups(List.of(group)).build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder().experiments(List.of(experiment)).build())
                .build();
    }

    public static LoadConfigRequestDto requestParamsConfig(long version) {
        RuleDto requestParamRule = rule("STRING", "clientSegment", "VIP", "REQUEST_PARAMS");
        ObjectSelectConditionDto condition = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(requestParamRule)))
                .build();
        ExperimentDto experiment = ExperimentDto.builder()
                .id(2).purpose("DCG").salt("request-param-salt").layerId(1)
                .objectSelectConditions(List.of(condition)).groups(List.of(groupWithActionType(1, "A", "6"))).build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder().experiments(List.of(experiment)).build())
                .build();
    }

    public static LoadConfigRequestDto oneExperimentTwoConditionsConfig(long version) {
        ObjectSelectConditionDto c1 = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(
                        rule("INTEGER", "channel", "19", "SPLITTING_OBJECTS"),
                        rule("STRING", "sellingProductId", "1-AVDHSSS", "SPLITTING_OBJECTS")
                )))
                .build();
        ObjectSelectConditionDto c2 = ObjectSelectConditionDto.builder()
                .id(2)
                .rules(List.of(List.of(rule("INTEGER", "templateId", "120222", "SPLITTING_OBJECTS"))))
                .build();

        ExperimentDto experiment = ExperimentDto.builder()
                .id(2).purpose("DCG").salt("24096d2M1e").layerId(1)
                .objectSelectConditions(List.of(c1, c2)).groups(List.of(groupWithActionType(1, "A", "6"))).build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder().experiments(List.of(experiment)).build())
                .build();
    }

    public static LoadConfigRequestDto twoExperimentsMatchingSameObjectConfig(long version) {
        ObjectSelectConditionDto condition = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(
                        rule("INTEGER", "channel", "19", "SPLITTING_OBJECTS"),
                        rule("STRING", "sellingProductId", "1-AVDHSSS", "SPLITTING_OBJECTS")
                )))
                .build();

        ExperimentDto exp1 = ExperimentDto.builder()
                .id(2).purpose("DCG").salt("salt-1").layerId(1)
                .objectSelectConditions(List.of(condition)).groups(List.of(groupWithActionType(1, "A", "6"))).build();

        ExperimentDto exp2 = ExperimentDto.builder()
                .id(3).purpose("DCG").salt("salt-2").layerId(2)
                .objectSelectConditions(List.of(condition)).groups(List.of(groupWithActionType(1, "B", "5"))).build();

        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder().experiments(List.of(exp1, exp2)).build())
                .build();
    }

    public static SplitterPrecalcRequestDto matchingPrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(List.of(matchingPrecalcObject(uniqueConfigurationId)))
                .build();
    }

    public static SplitterPrecalcRequestDto nonMatchingPrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(List.of(nonMatchingPrecalcObject(uniqueConfigurationId)))
                .build();
    }

    public static SplitterPrecalcRequestDto multiObjectPrecalcRequest(int soConfigVersion, SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(List.of(objects))
                .build();
    }

    public static SplitterPrecalcObjectDto matchingPrecalcObject(String uniqueConfigurationId) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(
                        new SplitterPrecalcParamDto("configCommId", List.of("1002"), "INTEGER"),
                        new SplitterPrecalcParamDto("sellingProductId", List.of("1-AVDHSSS"), "STRING"),
                        new SplitterPrecalcParamDto("channel", List.of("19"), "INTEGER"),
                        new SplitterPrecalcParamDto("templateId", List.of("120222"), "INTEGER")
                ))
                .build();
    }

    public static SplitterPrecalcObjectDto nonMatchingPrecalcObject(String uniqueConfigurationId) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(
                        new SplitterPrecalcParamDto("configCommId", List.of("1002"), "INTEGER"),
                        new SplitterPrecalcParamDto("sellingProductId", List.of("1-XCDV6TA"), "STRING"),
                        new SplitterPrecalcParamDto("channel", List.of("20"), "INTEGER"),
                        new SplitterPrecalcParamDto("templateId", List.of("120222"), "INTEGER")
                ))
                .build();
    }

    public static SplitRequestDto matchingSplitRequest(String uniqueConfigurationId) {
        return multiObjectSplitRequest(matchingSplitObject(uniqueConfigurationId));
    }

    public static SplitRequestDto nonMatchingSplitRequest(String uniqueConfigurationId) {
        return multiObjectSplitRequest(nonMatchingSplitObject(uniqueConfigurationId));
    }

    public static SplitRequestDto multiConditionMatchingSplitRequest(String uniqueConfigurationId) {
        return multiObjectSplitRequest(matchingSplitObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto multiConditionMatchingPrecalcRequest(String uniqueConfigurationId) {
        return matchingPrecalcRequest(uniqueConfigurationId, 1);
    }

    public static SplitRequestDto multiObjectSplitRequest(SplittingObjectDto... objects) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId("PRECALC-SPLIT-CHECK")
                .requestParams(List.of())
                .splittingObjects(List.of(objects))
                .build();
    }

    public static SplittingObjectDto matchingSplitObject(String uniqueConfigurationId) {
        return matchingSplitObject(uniqueConfigurationId, MATCHING_OBJECT_ID);
    }

    public static SplittingObjectDto matchingSplitObject(String uniqueConfigurationId, String objectId) {
        return SplittingObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectId(objectId)
                .objectParams(List.of(
                        param("configCommId", "1002", "INTEGER"),
                        param("sellingProductId", "1-AVDHSSS", "STRING"),
                        param("channel", "19", "INTEGER"),
                        param("templateId", "120222", "INTEGER")
                ))
                .build();
    }

    public static SplittingObjectDto nonMatchingSplitObject(String uniqueConfigurationId) {
        return nonMatchingSplitObject(uniqueConfigurationId, MATCHING_OBJECT_ID);
    }

    public static SplittingObjectDto nonMatchingSplitObject(String uniqueConfigurationId, String objectId) {
        return SplittingObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectId(objectId)
                .objectParams(List.of(
                        param("configCommId", "1002", "INTEGER"),
                        param("sellingProductId", "1-XCDV6TA", "STRING"),
                        param("channel", "20", "INTEGER"),
                        param("templateId", "120222", "INTEGER")
                ))
                .build();
    }

    public static String objectIdFromSeed(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes()).toString();
    }

    public static SplitRequestDto splitWithoutUniqueConfigurationId() {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId("RUNTIME-SPLIT-WITHOUT-PRECALC-ID")
                .requestParams(List.of())
                .splittingObjects(List.of(
                        SplittingObjectDto.builder()
                                .uniqueConfigurationId(null)
                                .objectId(MATCHING_OBJECT_ID)
                                .objectParams(List.of(
                                        param("configCommId", "1002", "INTEGER"),
                                        param("sellingProductId", "1-AVDHSSS", "STRING"),
                                        param("channel", "19", "INTEGER"),
                                        param("templateId", "120222", "INTEGER")
                                ))
                                .build()
                ))
                .build();
    }

    private static GroupDto groupWithActionType(int conditionId, String code, String actionTypeValue) {
        ParamDto actionType = ParamDto.builder()
                .paramCode("actionType")
                .paramValues(List.of(actionTypeValue))
                .dataType("INTEGER")
                .build();

        SplittingResultDto result = SplittingResultDto.builder()
                .conditionId(conditionId)
                .resultParams(List.of(actionType))
                .build();

        return GroupDto.builder()
                .code(code)
                .shares(List.of(ShareDto.builder().shareFrom(0).shareTo(10000).build()))
                .splittingResults(List.of(result))
                .build();
    }

    private static RuleDto rule(String dataType, String paramCode, String value, String source) {
        return RuleDto.builder()
                .dataType(dataType)
                .paramCode(paramCode)
                .paramSource(source)
                .operatorCode("in")
                .values(List.of(value))
                .build();
    }

    private static ParamDto param(String code, String value, String dataType) {
        return ParamDto.builder().paramCode(code).paramValues(List.of(value)).dataType(dataType).build();
    }
}
