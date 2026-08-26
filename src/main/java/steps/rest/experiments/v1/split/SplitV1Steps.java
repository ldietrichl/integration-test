package steps.rest.experiments.v1.split;

import constants.Endpoints;
import dto.splitter.splits.SplitRequestDto;
import org.apache.http.HttpStatus;
import request.splitter.splits.SplitsParams;
import request.splitter.splits.SplitsRequestFactory;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.qameta.allure.Allure.step;

public class SplitV1Steps {
    private final RestClient client;
    private final SplitsRequestFactory factory = new SplitsRequestFactory();

    public SplitV1Steps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper createSplit(SplitsParams params) {
        SplitRequestDto dto = factory.build(params);
        String body = factory.toJson(dto);
        return client
                .post(spec -> spec.body(body), "/api/v1/experiments/refbook/splits");
    }

    public ValidatableResponseWrapper changeSplit(SplitsParams params) {
        SplitRequestDto dto = factory.build(params);
        String body = factory.toJson(dto);
        return client
                .put(spec -> spec.queryParam("id", params.getId()).body(body), "/api/v1/experiments/refbook/splits");
    }

    public ValidatableResponseWrapper deleteSplit(Object id) {
        return client
                .delete(spec -> spec.queryParam("id", id), "/api/v1/experiments/refbook/splits");
    }

    public ValidatableResponseWrapper getCjLinks(String body) {
        return client
                .post(spec -> spec.body(body), "/api/v1/experiments/refbook/cj_links");
    }

    public ValidatableResponseWrapper getRefBookSplits(Map<String, Object> params) {
        return client
                .get(spec -> spec.queryParam("page", params.get("page"))
                                .queryParam("size", params.get("size")),
                        "/api/v1/experiments/refbook/splits");
    }

    public ValidatableResponseWrapper getSplitsEnhanceRunning() {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_SPLITS_LIST_RUNNING;
        return step("Получение расширенных сплитов в статусе IN_PROGRESS; Эндпоинт: '%s'"
                .formatted(endpoint), () -> client.get(spec -> spec, endpoint));
    }

    public ValidatableResponseWrapper getSplitsEnhanceRunning(List<Long> ids) {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_SPLITS_LIST_RUNNING;
        return step("Получение расширенных running-сплитов по ids: '%s'; Эндпоинт: '%s'"
                .formatted(ids, endpoint), () -> client.get(spec -> spec.queryParam("ids", idsCsv(ids)), endpoint));
    }

    public ValidatableResponseWrapper getSplitsEnhanceRunningWithRawIds(String ids) {
        String endpoint = Endpoints.ExperimentsV1.V1_EXPERIMENTS_SPLITS_LIST_RUNNING;
        return step("Получение расширенных running-сплитов с raw ids: '%s'; Эндпоинт: '%s'"
                .formatted(ids, endpoint), () -> client.get(spec -> spec.queryParam("ids", ids), endpoint));
    }

    public ValidatableResponseWrapper getSplitsEnhanceRunningStatusOk() {
        return getSplitsEnhanceRunning().should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    public ValidatableResponseWrapper getSplitsEnhanceRunningStatusOk(List<Long> ids) {
        return getSplitsEnhanceRunning(ids).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK));
    }

    private String idsCsv(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
