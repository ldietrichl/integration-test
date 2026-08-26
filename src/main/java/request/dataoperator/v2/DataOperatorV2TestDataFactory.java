package request.dataoperator.v2;

import dto.dataoperator.v2.ExplicitNullReturnIdsRequestDto;
import dto.dataoperator.v2.InvalidSplittingObjectsRequestDto;
import dto.dataoperator.v2.SoFieldValuesDictRequestDto;
import dto.dataoperator.v2.SplittingObjectRuleDto;
import dto.dataoperator.v2.SplittingObjectsRequestDto;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DataOperatorV2TestDataFactory {

    public static final String DEFAULT_SPLITTING_POINT = "MAPPER";
    public static final int OBJECT_IDS_LIMIT = 20_000;

    private static final Map<String, String> KNOWN_DATA_TYPES = Map.ofEntries(
            Map.entry("modelId", "INTEGER"),
            Map.entry("channelId", "INTEGER"),
            Map.entry("templateId", "INTEGER"),
            Map.entry("cjId", "INTEGER"),
            Map.entry("configCommId", "INTEGER"),
            Map.entry("isProm", "BOOLEAN"),
            Map.entry("productId", "STRING"),
            Map.entry("productName", "STRING"),
            Map.entry("productCrmId", "STRING"),
            Map.entry("modelName", "STRING"),
            Map.entry("channelName", "STRING")
    );

    private DataOperatorV2TestDataFactory() {
    }

    public static SplittingObjectsRequestDto allObjectsRequest(Boolean returnIds) {
        return allObjectsRequest(returnIds, 0, 15, false);
    }

    public static SplittingObjectsRequestDto allObjectsRequest(
            Boolean returnIds,
            int page,
            int size,
            Boolean parent
    ) {
        return SplittingObjectsRequestDto.builder()
                .page(page)
                .size(size)
                .returnIds(returnIds)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(parent)
                .rules(List.of())
                .build();
    }

    public static SplittingObjectsRequestDto allObjectsRequestWithoutReturnIds(int page, int size) {
        return SplittingObjectsRequestDto.builder()
                .page(page)
                .size(size)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(false)
                .rules(List.of())
                .build();
    }

    public static ExplicitNullReturnIdsRequestDto allObjectsRequestWithExplicitNullReturnIds() {
        return ExplicitNullReturnIdsRequestDto.builder()
                .page(0)
                .size(15)
                .returnIds(null)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(false)
                .rules(List.of())
                .build();
    }

    public static SplittingObjectsRequestDto requestWithRule(
            Boolean returnIds,
            int page,
            int size,
            SplittingObjectRuleDto rule
    ) {
        return requestWithRules(returnIds, page, size, false, List.of(List.of(rule)));
    }

    public static SplittingObjectsRequestDto requestWithRules(
            Boolean returnIds,
            int page,
            int size,
            Boolean parent,
            List<List<SplittingObjectRuleDto>> rules
    ) {
        return SplittingObjectsRequestDto.builder()
                .page(page)
                .size(size)
                .returnIds(returnIds)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(parent)
                .rules(rules)
                .build();
    }

    public static SplittingObjectRuleDto equalRule(String parameterCode, String value) {
        return SplittingObjectRuleDto.builder()
                .dataType(resolveDataType(parameterCode, value))
                .parameterCode(parameterCode)
                .operatorCode("equal")
                .values(List.of(value))
                .build();
    }

    public static SplittingObjectRuleDto rule(
            String dataType,
            String parameterCode,
            String operatorCode,
            List<String> values
    ) {
        return SplittingObjectRuleDto.builder()
                .dataType(dataType)
                .parameterCode(parameterCode)
                .operatorCode(operatorCode)
                .values(values)
                .build();
    }

    /**
     * Практически гарантированно пустая выборка на валидной точке MAPPER.
     * Используется вместо запроса к несуществующей точке, который по реализации
     * корректно приводит к ошибке отсутствия справочника, а не к пустому 200-ответу.
     */
    public static SplittingObjectsRequestDto emptyResultRequest(Boolean returnIds) {
        SplittingObjectRuleDto impossibleModel = rule(
                "INTEGER", "modelId", "equal", List.of("-9223372036854775808"));
        SplittingObjectRuleDto impossibleChannel = rule(
                "INTEGER", "channelId", "equal", List.of("-9223372036854775808"));
        return requestWithRules(returnIds, 0, 10, false,
                List.of(List.of(impossibleModel, impossibleChannel)));
    }

    public static InvalidSplittingObjectsRequestDto invalidReturnIdsTypeRequest() {
        return InvalidSplittingObjectsRequestDto.builder()
                .page(0)
                .size(10)
                .returnIds(List.of("true"))
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(false)
                .rules(List.of())
                .build();
    }

    public static SplittingObjectsRequestDto requestWithoutRequiredPage() {
        return SplittingObjectsRequestDto.builder()
                .size(10)
                .returnIds(true)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(false)
                .rules(List.of())
                .build();
    }

    public static SplittingObjectsRequestDto requestWithoutRequiredSize() {
        return SplittingObjectsRequestDto.builder()
                .page(0)
                .returnIds(true)
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .parent(false)
                .rules(List.of())
                .build();
    }

    public static SoFieldValuesDictRequestDto modelDictionaryRequest() {
        return SoFieldValuesDictRequestDto.builder()
                .splittingPointCode(DEFAULT_SPLITTING_POINT)
                .dictParamCode("cacheModels")
                .search("")
                .responseLimit(2)
                .build();
    }

    public static boolean hasKnownDataType(String parameterCode) {
        return KNOWN_DATA_TYPES.containsKey(parameterCode);
    }

    private static String resolveDataType(String parameterCode, String value) {
        String known = KNOWN_DATA_TYPES.get(parameterCode);
        if (known != null) {
            return known;
        }
        if (value != null && ("true".equals(value.toLowerCase(Locale.ROOT))
                || "false".equals(value.toLowerCase(Locale.ROOT)))) {
            return "BOOLEAN";
        }
        try {
            Long.parseLong(value);
            return "INTEGER";
        } catch (Exception ignored) {
            return "STRING";
        }
    }
}
