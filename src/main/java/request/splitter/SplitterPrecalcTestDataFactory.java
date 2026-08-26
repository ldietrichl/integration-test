package request.splitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static request.splitter.SplitterPrecalcScenarioObjectFactory.multiConditionPrecalcObject;
import static request.splitter.SplitterPrecalcScenarioObjectFactory.multiConditionSplitObject;
import static request.splitter.SplitterPrecalcScenarioObjectFactory.sameShapePrecalcObject;
import static request.splitter.SplitterPrecalcScenarioObjectFactory.sameShapeSplitObject;
import static request.splitter.SplitterPrecalcScenarioObjectFactory.singleProductPrecalcObject;
import static request.splitter.SplitterPrecalcScenarioObjectFactory.singleProductSplitObject;

public final class SplitterPrecalcTestDataFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String MATCHING_OBJECT_ID = "22222222-2222-2222-2222-222222222222";
    public static final long GUARANTEED_POSITIVE_EXP_ID = 5001L;
    public static final String GUARANTEED_POSITIVE_SALT = "MANDATORY-PRECALC-SALT-5001";
    public static final String GUARANTEED_POSITIVE_PRODUCT = "GP-PRODUCT-1";
    public static final String GUARANTEED_NEGATIVE_PRODUCT = "GN-PRODUCT-1";

    public static final long MULTI_CONDITION_EXP_ID = 7001L;
    public static final int MULTI_CONDITION_RESULT_CONDITION_ID = 2;
    public static final String MULTI_CONDITION_SALT = "RISK-MULTI-CONDITION-SALT-7001";
    public static final String MULTI_CONDITION_PRODUCT = "MC-PRODUCT-1";
    public static final String MULTI_CONDITION_TEMPLATE = "220001";

    public static final long RELOAD_BASELINE_EXP_ID = 8001L;
    public static final long RELOAD_CHANGED_EXP_ID = 8002L;
    public static final String RELOAD_BASELINE_SALT = "RELOAD-SALT-V1-8001";
    public static final String RELOAD_CHANGED_SALT = "RELOAD-SALT-V2-8002";
    public static final String RELOAD_PRODUCT = "RELOAD-PRODUCT-1";

    private SplitterPrecalcTestDataFactory() {
    }

    public static LoadConfigRequestDto matchingMapperConfigMessage(long version) {
        RuleDto ruleChannel = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("channel")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(List.of("19"))
                .build();

        RuleDto ruleProduct = RuleDto.builder()
                .dataType("STRING")
                .paramCode("sellingProductId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(List.of("1-AVDHSSS"))
                .build();

        ObjectSelectConditionDto condition = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(ruleChannel, ruleProduct)))
                .build();

        ParamDto actionType = ParamDto.builder()
                .paramCode("actionType")
                .paramValues(List.of("6"))
                .dataType("INTEGER")
                .build();

        SplittingResultDto splittingResult = SplittingResultDto.builder()
                .conditionId(1)
                .resultParams(List.of(actionType))
                .build();

        GroupDto group = GroupDto.builder()
                .code("A")
                .shares(List.of(ShareDto.builder().shareFrom(0).shareTo(10000).build()))
                .splittingResults(List.of(splittingResult))
                .build();

        ExperimentDto experiment = ExperimentDto.builder()
                .id(2)
                .purpose("DCG")
                .salt("24096d2M1e")
                .layerId(1)
                .objectSelectConditions(List.of(condition))
                .groups(List.of(group))
                .build();

        return loadConfigRequest(version, List.of(experiment));
    }

    public static LoadConfigRequestDto minimalGuaranteedPositiveConfig(long version) {
        return loadConfigRequest(version, List.of(singleProductExperiment(
                (int) GUARANTEED_POSITIVE_EXP_ID,
                GUARANTEED_POSITIVE_SALT,
                GUARANTEED_POSITIVE_PRODUCT,
                "0"
        )));
    }

    public static LoadConfigRequestDto multiConditionSingleExperimentConfig(long version) {
        RuleDto condition1Rule = RuleDto.builder()
                .dataType("STRING")
                .paramCode("sellingProductId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(MULTI_CONDITION_PRODUCT))
                .build();

        RuleDto condition2Rule = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("templateId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(MULTI_CONDITION_TEMPLATE))
                .build();

        ObjectSelectConditionDto condition1 = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(condition1Rule)))
                .build();

        ObjectSelectConditionDto condition2 = ObjectSelectConditionDto.builder()
                .id(MULTI_CONDITION_RESULT_CONDITION_ID)
                .rules(List.of(List.of(condition2Rule)))
                .build();

        ParamDto actionType = ParamDto.builder()
                .paramCode("actionType")
                .paramValues(List.of("0"))
                .dataType("INTEGER")
                .build();

        SplittingResultDto resultForCondition2 = SplittingResultDto.builder()
                .conditionId(MULTI_CONDITION_RESULT_CONDITION_ID)
                .resultParams(List.of(actionType))
                .build();

        GroupDto group = GroupDto.builder()
                .code("A")
                .shares(List.of(ShareDto.builder().shareFrom(0).shareTo(10000).build()))
                .splittingResults(List.of(resultForCondition2))
                .build();

        ExperimentDto experiment = ExperimentDto.builder()
                .id((int) MULTI_CONDITION_EXP_ID)
                .purpose("DCG")
                .salt(MULTI_CONDITION_SALT)
                .objectSelectConditions(List.of(condition1, condition2))
                .groups(List.of(group))
                .build();

        return loadConfigRequest(version, List.of(experiment));
    }

    public static LoadConfigRequestDto reloadSensitiveConfigV1(long version) {
        return loadConfigRequest(version, List.of(singleProductExperiment(
                (int) RELOAD_BASELINE_EXP_ID,
                RELOAD_BASELINE_SALT,
                RELOAD_PRODUCT,
                "0"
        )));
    }

    public static LoadConfigRequestDto reloadSensitiveConfigV2(long version) {
        return loadConfigRequest(version, List.of(singleProductExperiment(
                (int) RELOAD_CHANGED_EXP_ID,
                RELOAD_CHANGED_SALT,
                RELOAD_PRODUCT,
                "1"
        )));
    }

    public static SplitterPrecalcRequestDto matchingPrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion,
                SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(
                                new SplitterPrecalcParamDto("configCommId", List.of("1002"), "INTEGER"),
                                new SplitterPrecalcParamDto("sellingProductId", List.of("1-AVDHSSS"), "STRING"),
                                new SplitterPrecalcParamDto("channel", List.of("19"), "INTEGER"),
                                new SplitterPrecalcParamDto("templateId", List.of("120222"), "INTEGER")
                        ))
                        .build()
        );
    }

    public static SplitterPrecalcRequestDto nonMatchingPrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion,
                SplitterPrecalcObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectParams(List.of(
                                new SplitterPrecalcParamDto("configCommId", List.of("1002"), "INTEGER"),
                                new SplitterPrecalcParamDto("sellingProductId", List.of("1-XCDV6TA"), "STRING"),
                                new SplitterPrecalcParamDto("channel", List.of("20"), "INTEGER"),
                                new SplitterPrecalcParamDto("templateId", List.of("120222"), "INTEGER")
                        ))
                        .build()
        );
    }

    public static SplitterPrecalcRequestDto minimalGuaranteedPositivePrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion, minimalGuaranteedPositivePrecalcObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto minimalGuaranteedNegativePrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion, minimalGuaranteedNegativePrecalcObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto changedGuaranteedPositivePrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion, changedGuaranteedPositivePrecalcObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto multiConditionPrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion, multiConditionMatchingPrecalcObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto reloadSensitivePrecalcRequest(String uniqueConfigurationId, int soConfigVersion) {
        return precalcRequest(soConfigVersion, reloadSensitivePrecalcObject(uniqueConfigurationId));
    }

    public static SplitterPrecalcRequestDto emptyPrecalcRequest(int soConfigVersion) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(List.of())
                .build();
    }

    public static SplitRequestDto matchingSplitRequest(String uniqueConfigurationId) {
        return splitRequest(
                SplittingObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectId(MATCHING_OBJECT_ID)
                        .objectParams(List.of(
                                param("configCommId", "1002", "INTEGER"),
                                param("sellingProductId", "1-AVDHSSS", "STRING"),
                                param("channel", "19", "INTEGER"),
                                param("templateId", "120222", "INTEGER")
                        ))
                        .build()
        );
    }

    public static SplitRequestDto nonMatchingSplitRequest(String uniqueConfigurationId) {
        return splitRequest(
                SplittingObjectDto.builder()
                        .uniqueConfigurationId(uniqueConfigurationId)
                        .objectId(MATCHING_OBJECT_ID)
                        .objectParams(List.of(
                                param("configCommId", "1002", "INTEGER"),
                                param("sellingProductId", "1-XCDV6TA", "STRING"),
                                param("channel", "20", "INTEGER"),
                                param("templateId", "120222", "INTEGER")
                        ))
                        .build()
        );
    }

    public static SplitRequestDto minimalGuaranteedPositiveSplitRequest(String uniqueConfigurationId) {
        return splitRequest(minimalGuaranteedPositiveSplitObject(uniqueConfigurationId));
    }

    public static SplitRequestDto minimalGuaranteedNegativeSplitRequest(String uniqueConfigurationId) {
        return splitRequest(minimalGuaranteedNegativeSplitObject(uniqueConfigurationId));
    }

    public static SplitRequestDto changedGuaranteedPositiveSplitRequest(String uniqueConfigurationId) {
        return splitRequest(changedGuaranteedPositiveSplitObject(uniqueConfigurationId));
    }

    public static SplitRequestDto multiConditionSplitRequest(String uniqueConfigurationId, String objectId) {
        return splitRequest(multiConditionMatchingSplitObject(uniqueConfigurationId, objectId));
    }

    public static SplitRequestDto reloadSensitiveSplitRequest(String uniqueConfigurationId, String objectId) {
        return splitRequest(reloadSensitiveSplitObject(uniqueConfigurationId, objectId));
    }

    public static SplitterPrecalcRequestDto precalcRequest(int soConfigVersion, SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(soConfigVersion)
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    public static SplitRequestDto splitRequest(SplittingObjectDto... objects) {
        return SplitRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .splittingId("PRECALC-SPLIT-CHECK")
                .requestParams(List.of())
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    public static SplitterPrecalcObjectDto minimalGuaranteedPositivePrecalcObject(String uniqueConfigurationId) {
        return singleProductPrecalcObject(uniqueConfigurationId, GUARANTEED_POSITIVE_PRODUCT);
    }

    public static SplitterPrecalcObjectDto minimalGuaranteedNegativePrecalcObject(String uniqueConfigurationId) {
        return singleProductPrecalcObject(uniqueConfigurationId, GUARANTEED_NEGATIVE_PRODUCT);
    }

    public static SplitterPrecalcObjectDto changedGuaranteedPositivePrecalcObject(String uniqueConfigurationId) {
        return minimalGuaranteedNegativePrecalcObject(uniqueConfigurationId);
    }

    public static SplitterPrecalcObjectDto sameShapeMatchingPrecalcObject(String uniqueConfigurationId) {
        return sameShapePrecalcObject(uniqueConfigurationId, "1-AVDHSSS", "19", "120222");
    }

    public static SplitterPrecalcObjectDto sameShapeNonMatchingPrecalcObject(String uniqueConfigurationId) {
        return sameShapePrecalcObject(uniqueConfigurationId, "1-XCDV6TA", "20", "120223");
    }

    public static SplitterPrecalcObjectDto multiConditionMatchingPrecalcObject(String uniqueConfigurationId) {
        return multiConditionPrecalcObject(uniqueConfigurationId, MULTI_CONDITION_PRODUCT, MULTI_CONDITION_TEMPLATE);
    }

    public static SplitterPrecalcObjectDto reloadSensitivePrecalcObject(String uniqueConfigurationId) {
        return singleProductPrecalcObject(uniqueConfigurationId, RELOAD_PRODUCT);
    }

    public static SplittingObjectDto minimalGuaranteedPositiveSplitObject(String uniqueConfigurationId) {
        return singleProductSplitObject(uniqueConfigurationId, MATCHING_OBJECT_ID, GUARANTEED_POSITIVE_PRODUCT);
    }

    public static SplittingObjectDto minimalGuaranteedNegativeSplitObject(String uniqueConfigurationId) {
        return singleProductSplitObject(uniqueConfigurationId, MATCHING_OBJECT_ID, GUARANTEED_NEGATIVE_PRODUCT);
    }

    public static SplittingObjectDto changedGuaranteedPositiveSplitObject(String uniqueConfigurationId) {
        return minimalGuaranteedNegativeSplitObject(uniqueConfigurationId);
    }

    public static SplittingObjectDto sameShapeMatchingSplitObject(String uniqueConfigurationId, String objectId) {
        return sameShapeSplitObject(uniqueConfigurationId, objectId, "1-AVDHSSS", "19", "120222");
    }

    public static SplittingObjectDto sameShapeNonMatchingSplitObject(String uniqueConfigurationId, String objectId) {
        return sameShapeSplitObject(uniqueConfigurationId, objectId, "1-XCDV6TA", "20", "120223");
    }

    public static SplittingObjectDto multiConditionMatchingSplitObject(String uniqueConfigurationId, String objectId) {
        return multiConditionSplitObject(uniqueConfigurationId, objectId, MULTI_CONDITION_PRODUCT, MULTI_CONDITION_TEMPLATE);
    }

    public static SplittingObjectDto reloadSensitiveSplitObject(String uniqueConfigurationId, String objectId) {
        return singleProductSplitObject(uniqueConfigurationId, objectId, RELOAD_PRODUCT);
    }

    public static SplitRequestDto minimalGuaranteedPositiveSplitRequestForObjectId(String uniqueConfigurationId, String objectId) {
        return splitRequest(minimalGuaranteedPositiveSplitObjectWithObjectId(uniqueConfigurationId, objectId));
    }

    public static SplittingObjectDto minimalGuaranteedPositiveSplitObjectWithObjectId(String uniqueConfigurationId, String objectId) {
        return singleProductSplitObject(uniqueConfigurationId, objectId, GUARANTEED_POSITIVE_PRODUCT);
    }

    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать DTO в JSON", e);
        }
    }

    private static LoadConfigRequestDto loadConfigRequest(long version, List<ExperimentDto> experiments) {
        return LoadConfigRequestDto.builder()
                .messageId(UUID.randomUUID().toString())
                .requestId(UUID.randomUUID().toString())
                .configVersion(version)
                .forceConfigLoad(true)
                .splittingPointCode("MAPPER")
                .splittingConfig(SplittingConfigDto.builder()
                        .experiments(experiments)
                        .build())
                .build();
    }

    private static ExperimentDto singleProductExperiment(int expId,
                                                         String salt,
                                                         String product,
                                                         String actionTypeValue) {
        RuleDto productRule = RuleDto.builder()
                .dataType("STRING")
                .paramCode("sellingProductId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(product))
                .build();

        ObjectSelectConditionDto condition = ObjectSelectConditionDto.builder()
                .id(1)
                .rules(List.of(List.of(productRule)))
                .build();

        ParamDto actionType = ParamDto.builder()
                .paramCode("actionType")
                .paramValues(List.of(actionTypeValue))
                .dataType("INTEGER")
                .build();

        SplittingResultDto splittingResult = SplittingResultDto.builder()
                .conditionId(1)
                .resultParams(List.of(actionType))
                .build();

        GroupDto group = GroupDto.builder()
                .code("A")
                .shares(List.of(ShareDto.builder().shareFrom(0).shareTo(10000).build()))
                .splittingResults(List.of(splittingResult))
                .build();

        return ExperimentDto.builder()
                .id(expId)
                .purpose("DCG")
                .salt(salt)
                .objectSelectConditions(List.of(condition))
                .groups(List.of(group))
                .build();
    }

    private static ParamDto param(String code, String value, String dataType) {
        return ParamDto.builder()
                .paramCode(code)
                .paramValues(List.of(value))
                .dataType(dataType)
                .build();
    }
}
