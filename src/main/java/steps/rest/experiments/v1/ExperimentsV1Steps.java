package steps.rest.experiments.v1;

import constants.Endpoints;
import dto.experiments.v1.ExperimentsV1GetRequestDto;
import dto.experiments.v1.ExperimentsV1PostRequestDto;
import dto.experiments.v1.ExperimentsV1PostRequestDtoBuilder;
import feeders.ExperimentsFeeder;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import steps.rest.experiments.v1.id.IdV1StatusSteps;

import java.util.List;

import static constants.ConstantsForTests.AUTOTEST_FIELDS_MARKER;
import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

public class ExperimentsV1Steps {
    private final RestClient client;

    public ExperimentsV1Steps(RestClient client) {
        this.client = client;
    }

    public IdV1StatusSteps idStatusSteps() {
        return new IdV1StatusSteps(client);
    }

    public ValidatableResponseWrapper findExperimentsWithCustomParameters(ExperimentsV1GetRequestDto body) {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS;
        return step(("Поиск эксперимента; Эндпоинт: '%s'; По параметрам - страница: '%s'," +
                " количество результатов на странице: '%s', имя эксперимента: '%s', точное совпадение: '%s'," +
                " статусы: '%s', соль: '%s'")
                .formatted(
                        endpoint,
                        body.getPage()
                        , body.getSize()
                        , body.getName()
                        , body.getExact()
                        , body.getStatuses()
                        , body.getSalt()
                ), () ->
                client.get(
                        spec ->
                                spec.params(ExperimentsV1GetRequestDto.toMap(body))
                        , endpoint));
    }

    public ValidatableResponseWrapper findExperimentsWithCustomParametersStatusOk(ExperimentsV1GetRequestDto body) {
        return findExperimentsWithCustomParameters(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    public ValidatableResponseWrapper findExperimentsWithCustomParametersStatusBadRequest(
            ExperimentsV1GetRequestDto body) {
        return findExperimentsWithCustomParameters(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST));
    }

    public ValidatableResponseWrapper getExperimentsEnhanceRunning() {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_LIST_RUNNING;
        return step("Получение расширенных экспериментов в статусе IN_PROGRESS; Эндпоинт: '%s'"
                .formatted(endpoint), () -> client.get(spec -> spec, endpoint));
    }

    public ValidatableResponseWrapper getExperimentsEnhanceRunningStatusOk() {
        return getExperimentsEnhanceRunning().should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    public ValidatableResponseWrapper evictCash() {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_EVICT_CASH;
        return step("Обновление кэша running-экспериментов; Эндпоинт: '%s'"
                .formatted(endpoint), () -> client.delete(spec -> spec, endpoint));
    }

    public ValidatableResponseWrapper evictCashStatusOk() {
        return evictCash().should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    public ValidatableResponseWrapper deleteExperiment(Long id) {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_ID.formatted(id);
        return step("Удаление эксперимента; id: '%s'; Эндпоинт: '%s'"
                .formatted(id, endpoint), () -> client.delete(spec -> spec, endpoint));
    }

    public ValidatableResponseWrapper createCustomExperiment(ExperimentsV1PostRequestDto body) {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS;

        return step("Создание эксперимента; Эндпоинт: '%s'".formatted(endpoint), () -> {
                    ValidatableResponseWrapper response = step("Отправка запроса создания эксперимента", () ->
                            client
                                    .post(spec ->
                                                    spec.body(ExperimentsV1PostRequestDto.toJson(body))
                                            , endpoint)
                                    .should(
                                            haveStatusCode(HttpStatus.SC_OK))
                    );

                    stash().put("experimentId", response.toJsonPath().getString("id"));
                    stash().put("experimentName", response.toJsonPath().getString("name"));
                    stash().put("experimentSalt", response.toJsonPath().getString("salt"));

                    return response;
                }
        );
    }

    public ValidatableResponseWrapper createDefaultExperiment() {
        return createCustomExperiment(
                step("Формирование дефолтных параметров эксперимента", () ->
                        ExperimentsV1PostRequestDtoBuilder.buildDtoDefaultWithCustomParams(
                                ExperimentsFeeder.generateAqaMalilId()
                                , ExperimentsFeeder.startDt
                                , ExperimentsFeeder.endDt
                                , ExperimentsFeeder.generateSalt()
                                , "Эксперимент создан в рамках автокейса"
                                , "АБ тестовый"
                                , List.of("103081")
                        )
                )
        );
    }

    public void createTwoExperimentWithSimilarNames() {
        String aqaMalilId1 = ExperimentsFeeder.generateAqaMalilId();
        String aqaMalilId2 = aqaMalilId1 + AUTOTEST_FIELDS_MARKER.getValue();

        step("Создаем два эксперимента с похожими именами: 1: '%s', 2: '%s'"
                .formatted(aqaMalilId1, aqaMalilId2), () -> {
            ExperimentsV1PostRequestDto body = step("Формирование основных дефолтных параметров для эксперимента 1 и 2"
                    , () ->
                            ExperimentsV1PostRequestDtoBuilder.buildDtoDefaultWithCustomParams(
                                    aqaMalilId1
                                    , ExperimentsFeeder.startDt
                                    , ExperimentsFeeder.endDt
                                    , ExperimentsFeeder.generateSalt()
                                    , "Эксперимент создан в рамках автокейса"
                                    , "АБ тестовый"
                                    , List.of("103081")
                            )
            );

            ValidatableResponseWrapper response1 = step("Создание эксперимента с именем 1: '%s'"
                    .formatted(aqaMalilId1), () ->
                    createCustomExperiment(body)
            );

            String id1 = response1.toJsonPath().getString("id");
            String name1 = response1.toJsonPath().getString("name");
            String salt1 = response1.toJsonPath().getString("salt");

            step("Сохраняем в стеш данные 1 созданного эксперимента;" +
                    " experimentId1: '%s', experimentName1: '%s', experimentSalt1: '%s'."
                            .formatted(id1, name1, salt1), () -> {
                stash().put("experimentId1", id1);
                stash().put("experimentName1", name1);
                stash().put("experimentSalt1", salt1);
            });

            ExperimentsV1PostRequestDto body2 = step(("Изменяем в основных дефолтных параметрах имя для" +
                    " 2: '%s' эксперимента").formatted(aqaMalilId2), () ->
                    body.toBuilder().name(aqaMalilId2).build()
            );

            ValidatableResponseWrapper response2 = step("Создание эксперимента с именем 2: '%s'"
                    .formatted(aqaMalilId2), () ->
                    createCustomExperiment(body2)
            );

            String id2 = response2.toJsonPath().getString("id");
            String name2 = response2.toJsonPath().getString("name");
            String salt2 = response2.toJsonPath().getString("salt");

            step("Сохраняем в стеш данные 2 созданного эксперимента;" +
                    " experimentId2: '%s', experimentName2: '%s', experimentSalt2: '%s'."
                            .formatted(id2, name2, salt2), () -> {
                stash().put("experimentId2", id2);
                stash().put("experimentName2", name2);
                stash().put("experimentSalt2", salt2);
            });
        });
    }
}
