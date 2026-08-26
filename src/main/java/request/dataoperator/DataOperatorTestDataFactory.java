package request.dataoperator;

import dto.dataoperator.DataOperatorRuleDto;
import dto.dataoperator.request.SplittingObjectIdsRequestDto;
import dto.dataoperator.request.SplittingObjectsRequestDto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.qameta.allure.Allure.step;

public final class DataOperatorTestDataFactory {

    public static final String DEFAULT_SPLITTING_POINT =
            System.getProperty("dataOperator.splittingPoint", "MAPPER");

    private DataOperatorTestDataFactory() {
    }

    public static SplittingObjectIdsRequestDto idsRequest(Boolean parent,
                                                           List<List<DataOperatorRuleDto>> rules) {
        return idsRequest(DEFAULT_SPLITTING_POINT, parent, rules);
    }

    public static SplittingObjectIdsRequestDto idsRequest(String splittingPointCode,
                                                           Boolean parent,
                                                           List<List<DataOperatorRuleDto>> rules) {
        return step("Формируем запрос получения идентификаторов объектов сплиттования", () ->
                SplittingObjectIdsRequestDto.builder()
                        .splittingPointCode(splittingPointCode)
                        .parent(parent)
                        .rules(rules)
                        .build());
    }

    public static SplittingObjectsRequestDto registryRequest(Boolean parent,
                                                              List<List<DataOperatorRuleDto>> rules) {
        return step("Формируем контрольный запрос получения объектов сплиттования с returnIds=true", () ->
                SplittingObjectsRequestDto.builder()
                        .page(0)
                        .size(50)
                        .returnIds(true)
                        .splittingPointCode(DEFAULT_SPLITTING_POINT)
                        .parent(parent)
                        .rules(rules)
                        .build());
    }

    public static DataOperatorRuleDto rule(String dataType,
                                           String parameterCode,
                                           String operatorCode,
                                           String... values) {
        List<String> valueList = values == null ? null : List.of(values);
        return step("Формируем rule: %s %s %s".formatted(parameterCode, operatorCode, valueList), () ->
                DataOperatorRuleDto.builder()
                        .dataType(dataType)
                        .parameterCode(parameterCode)
                        .operatorCode(operatorCode)
                        .values(valueList)
                        .build());
    }

    public static DataOperatorRuleDto unaryRule(String dataType,
                                                String parameterCode,
                                                String operatorCode) {
        return DataOperatorRuleDto.builder()
                .dataType(dataType)
                .parameterCode(parameterCode)
                .operatorCode(operatorCode)
                .values(Collections.emptyList())
                .build();
    }

    public static List<List<DataOperatorRuleDto>> emptyRules() {
        return Collections.emptyList();
    }

    public static List<List<DataOperatorRuleDto>> andGroup(DataOperatorRuleDto... rules) {
        return List.of(List.of(rules));
    }

    @SafeVarargs
    public static List<List<DataOperatorRuleDto>> orGroups(List<DataOperatorRuleDto>... groups) {
        return List.of(groups);
    }

    public static String impossibleValue() {
        return "EXPLAB-2729-NOT-FOUND-" + UUID.randomUUID();
    }
}
