package request.splitter;

import dto.splitter.common.ParamDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.split.SplittingObjectDto;

import java.util.List;

public final class SplitterPrecalcScenarioObjectFactory {

    private SplitterPrecalcScenarioObjectFactory() {
    }

    public static SplitterPrecalcObjectDto minimalGuaranteedPositivePrecalcObject(String uniqueConfigurationId, String product) {
        return precalcObject(uniqueConfigurationId,
                precalcParam("sellingProductId", product, "STRING"));
    }

    public static SplitterPrecalcObjectDto sameShapePrecalcObject(String uniqueConfigurationId, String product, String channel, String templateId) {
        return precalcObject(uniqueConfigurationId,
                precalcParam("configCommId", "1002", "INTEGER"),
                precalcParam("sellingProductId", product, "STRING"),
                precalcParam("channel", channel, "INTEGER"),
                precalcParam("templateId", templateId, "INTEGER"));
    }

    public static SplitterPrecalcObjectDto multiConditionPrecalcObject(String uniqueConfigurationId, String product, String templateId) {
        return precalcObject(uniqueConfigurationId,
                precalcParam("sellingProductId", product, "STRING"),
                precalcParam("templateId", templateId, "INTEGER"));
    }

    public static SplitterPrecalcObjectDto singleProductPrecalcObject(String uniqueConfigurationId, String product) {
        return precalcObject(uniqueConfigurationId,
                precalcParam("sellingProductId", product, "STRING"));
    }

    public static SplittingObjectDto singleProductSplitObject(String uniqueConfigurationId, String objectId, String product) {
        return splitObject(uniqueConfigurationId, objectId,
                splitParam("sellingProductId", product, "STRING"));
    }

    public static SplittingObjectDto sameShapeSplitObject(String uniqueConfigurationId, String objectId, String product, String channel, String templateId) {
        return splitObject(uniqueConfigurationId, objectId,
                splitParam("configCommId", "1002", "INTEGER"),
                splitParam("sellingProductId", product, "STRING"),
                splitParam("channel", channel, "INTEGER"),
                splitParam("templateId", templateId, "INTEGER"));
    }

    public static SplittingObjectDto multiConditionSplitObject(String uniqueConfigurationId, String objectId, String product, String templateId) {
        return splitObject(uniqueConfigurationId, objectId,
                splitParam("sellingProductId", product, "STRING"),
                splitParam("templateId", templateId, "INTEGER"));
    }

    private static SplitterPrecalcObjectDto precalcObject(String uniqueConfigurationId, SplitterPrecalcParamDto... params) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(params))
                .build();
    }

    private static SplitterPrecalcParamDto precalcParam(String code, String value, String dataType) {
        return new SplitterPrecalcParamDto(code, List.of(value), dataType);
    }

    private static SplittingObjectDto splitObject(String uniqueConfigurationId, String objectId, ParamDto... params) {
        return SplittingObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectId(objectId)
                .objectParams(List.of(params))
                .build();
    }

    private static ParamDto splitParam(String code, String value, String dataType) {
        return SplitterDtoFactory.param(code, value, dataType);
    }
}
