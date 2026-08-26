package dto.experiments.v2.registry;

import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;

import java.util.List;

import static io.qameta.allure.Allure.step;

public class ExperimentsV2RegistryPostRequestDtoBuilder {
    static public ExperimentsV2RegistryPostRequestDto.Sorts sortsDefault() {
        return step(
                "Формирование дефолтных параметров сортировки для корневого объекта запроса реестра экспериментов"
                , () -> ExperimentsV2RegistryPostRequestDto.Sorts.builder()
                        .paramCode("id")
                        .direction(SortsDirections.DESC)
                        .build()
        );
    }

    static public ExperimentsV2RegistryPostRequestDto.Filters filtersDefault() {
        return step(
                "Формирование дефолтных параметров фильтров для корневого объекта запроса реестра экспериментов"
                , () -> ExperimentsV2RegistryPostRequestDto.Filters.builder()
                        .paramCode("name")
                        .operatorCode(OperatorCodeExpression.LIKE)
                        .paramValues(List.of("AQA"))
                        .build()
        );
    }

    static public ExperimentsV2RegistryPostRequestDto buildDtoDefault() {
        return ExperimentsV2RegistryPostRequestDto.builder()
                .page(0)
                .size(10)
                .search("")
                .build();
    }

    static public ExperimentsV2RegistryPostRequestDto buildDtoDefaultWithFilters() {
        return ExperimentsV2RegistryPostRequestDto.builder()
                .page(0)
                .size(10)
                .search("")
                .filters(List.of(List.of(filtersDefault())))
                .build();
    }

    static public ExperimentsV2RegistryPostRequestDto buildDtoDefaultWithSorts() {
        return ExperimentsV2RegistryPostRequestDto.builder()
                .page(0)
                .size(10)
                .search("")
                .sorts(List.of(sortsDefault()))
                .build();
    }

    static public ExperimentsV2RegistryPostRequestDto buildDtoDefaultWithFiltersAndSorts() {
        return ExperimentsV2RegistryPostRequestDto.builder()
                .page(0)
                .size(10)
                .search("")
                .filters(List.of(List.of(filtersDefault())))
                .sorts(List.of(sortsDefault()))
                .build();
    }
}
