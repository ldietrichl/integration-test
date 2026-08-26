package dto.experiments.v2.layers;

import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;

import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;

public class LayerRegistryV2RequestDtoBuilder {

    public static LayerRegistryV2RequestDto buildDefaultDto() {
        return step("Формируем дефолтный запрос реестра слоев v2", () -> LayerRegistryV2RequestDto.builder()
                .page(0)
                .size(10)
                .search("")
                .splittingPointCode("MAPPER")
                .filters(new ArrayList<>())
                .sorts(new ArrayList<>())
                .build());
    }

    public static LayerRegistryV2RequestDto buildDefaultDtoWithNameFilter(String namePart) {
        return buildDefaultDto().toBuilder()
                .filters(List.of(List.of(filter("name", OperatorCodeExpression.LIKE, namePart))))
                .build();
    }

    public static LayerRegistryV2RequestDto.Filter filter(
            String paramCode,
            OperatorCodeExpression operatorCode,
            String... values) {
        return filter(paramCode, operatorCode.getValue(), List.of(values));
    }

    public static LayerRegistryV2RequestDto.Filter filter(
            String paramCode,
            String operatorCode,
            List<String> values) {
        return step("Формируем фильтр реестра слоев v2: %s %s %s".formatted(paramCode, operatorCode, values), () ->
                LayerRegistryV2RequestDto.Filter.builder()
                        .paramCode(paramCode)
                        .operatorCode(operatorCode)
                        .paramValues(values)
                        .build());
    }

    public static LayerRegistryV2RequestDto.Sort sort(String paramCode, SortsDirections direction) {
        return sort(paramCode, direction.getValue());
    }

    public static LayerRegistryV2RequestDto.Sort sort(String paramCode, String direction) {
        return step("Формируем сортировку реестра слоев v2: %s %s".formatted(paramCode, direction), () ->
                LayerRegistryV2RequestDto.Sort.builder()
                        .paramCode(paramCode)
                        .direction(direction)
                        .build());
    }
}
