package dto.experiments.id.status;

import dto.enums.Boolean;
import dto.enums.ExperimentsStatusesV1;

import java.util.List;

import static io.qameta.allure.Allure.step;


public class ExperimentsPutRequestDtoBuilder {
    static public ExperimentsPutRequestDto buildDtoDefaultWithStatusAndComment(
            List<ExperimentsStatusesV1> status, String comment) {
        return step(("Формирование дефолтных параметров обновления экспериментов на статус: '%s'," +
                        " с комментарием: '%s'").formatted(status, comment)
                , () ->
                        ExperimentsPutRequestDto.builder()
                                .status(status)
                                .comment(comment)
                                .slave(Boolean.FALSE)
                                .ignoreWarnings(Boolean.TRUE)
                                .startCampaigns(Boolean.FALSE)
                                .build());
    }

    static public ExperimentsPutRequestDto buildDtoDefaultDraft() {
        ExperimentsStatusesV1 status = ExperimentsStatusesV1.DRAFT;
        String comment = "Черновик";
        return step(("Формирование дефолтных параметров обновления экспериментов на статус: '%s'," +
                        " с комментарием: '%s'").formatted(status, comment)
                , () ->
                        buildDtoDefaultWithStatusAndComment(
                                List.of(status)
                                , comment));
    }

    static public ExperimentsPutRequestDto buildDtoDefaultAgreement() {
        ExperimentsStatusesV1 status = ExperimentsStatusesV1.AGREEMENT;
        String comment = "На согласовании";
        return step(("Формирование дефолтных параметров обновления экспериментов на статус: '%s'," +
                        " с комментарием: '%s'").formatted(status, comment)
                , () ->
                        buildDtoDefaultWithStatusAndComment(
                                List.of(status)
                                , comment));
    }

    static public ExperimentsPutRequestDto buildDtoDefaultAgreed() {
        ExperimentsStatusesV1 status = ExperimentsStatusesV1.AGREED;
        String comment = "Согласовано";
        return step(("Формирование дефолтных параметров обновления экспериментов на статус: '%s'," +
                        " с комментарием: '%s'").formatted(status, comment)
                , () ->
                        buildDtoDefaultWithStatusAndComment(
                                List.of(status)
                                , comment));
    }

    static public ExperimentsPutRequestDto buildDtoDefaultInProgress() {
        ExperimentsStatusesV1 status = ExperimentsStatusesV1.IN_PROGRESS;
        String comment = "Выполняется";
        return step(("Формирование дефолтных параметров обновления экспериментов на статус: '%s'," +
                        " с комментарием: '%s'").formatted(status, comment)
                , () -> buildDtoDefaultWithStatusAndComment(
                        List.of(status)
                        , comment));
    }
}
