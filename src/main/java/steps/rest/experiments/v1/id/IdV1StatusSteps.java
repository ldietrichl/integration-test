package steps.rest.experiments.v1.id;

import dto.enums.ExperimentsStatusesV1;
import dto.experiments.id.status.ExperimentsPutRequestDto;
import dto.experiments.id.status.ExperimentsPutRequestDtoBuilder;
import org.apache.http.HttpStatus;
import ru.sber.qa.services.rest.RestClient;

import java.util.List;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

public class IdV1StatusSteps {
    private final RestClient client;

    public IdV1StatusSteps(RestClient client) {
        this.client = client;
    }

    public void conductExperimentThroughStatuses(
            String id
            , List<ExperimentsStatusesV1> statuses
            , List<String> comments) {
        step("Проводим эксперимент с id: '%s' через статусы '%s'".formatted(id, statuses), () -> {
                    if (statuses.size() != comments.size()) {
                        throw new IllegalArgumentException(
                                "Размер переданного списка статусов не равен переданному списку комментариев.");
                    }

                    for (int i = 0; i < statuses.size(); ++i) {
                        ExperimentsStatusesV1 status = statuses.get(i);
                        String comment = comments.get(i);

                        ExperimentsPutRequestDto body = step("Формирование параметров изменения статуса эксперимента;" +
                                " статус '%s', комментарий: '%s'".formatted(status, comment), () ->
                                ExperimentsPutRequestDtoBuilder
                                        .buildDtoDefaultWithStatusAndComment(List.of(status), comment)
                        );

                        step("Изменяем статус эксперимента с id: '%s'; статус: '%s', комментарий: '%s'"
                                .formatted(id, status, comment), () ->
                                client
                                        .put(
                                                spec -> spec
                                                        .body(body)
                                                        .pathParam("id", id)
                                                , "/api/v1/experiments/{id}/status")
                                        .should(
                                                haveStatusCode(HttpStatus.SC_OK))
                        );
                    }
                }
        );
    }
}
