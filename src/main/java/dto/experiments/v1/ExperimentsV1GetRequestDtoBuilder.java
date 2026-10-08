package dto.experiments.v1;

import dto.enums.Boolean;
import dto.enums.ExperimentsStatusesV1;

import java.util.List;

import static io.qameta.allure.Allure.step;

public class ExperimentsV1GetRequestDtoBuilder {
    static public ExperimentsV1GetRequestDto buildDtoDefault() {
        return step("Формирование дефолтных параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDto.builder()
                        .page("0")
                        .size("10")
                        .name("")
                        .salt("")
                        .exact(Boolean.FALSE)
                        .statuses(ExperimentsStatusesV1.all())
                        .build());
    }

    static public ExperimentsV1GetRequestDto buildDtoEmpty() {
        return step("Формирование пустых параметров поиска экспериментов", () ->
                ExperimentsV1GetRequestDto.builder()
                        .page("0") // Обязательное поле
                        .size("10") // Обязательное поле
                        .build());
    }

    static public ExperimentsV1GetRequestDto buildDtoWithIncorrectStatuses() {
        return step("Формирование некорректных параметров поиска экспериментов", () ->
                buildDtoDefault()
                        .toBuilder()
                        .statuses(List.of(ExperimentsStatusesV1.INCORRECT_STATUS_FOR_TEST))
                        .build());
    }
}
