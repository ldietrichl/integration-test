package steps.rest.splitter;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import constants.Endpoints;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import io.qameta.allure.Allure;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

public class SplitterRestSteps {
    private final RestClient client;

    public SplitterRestSteps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper loadConfig(Object body) {
        if (SplitterConfigLoadMode.isKafka()) {
            return Allure.step("Загрузка конфигурации сплиттера через Kafka", () ->
                    SplitterKafkaConfigLoadClient.load(body, "MAPPER")
            );
        }
        return Allure.step("Загрузка конфигурации сплиттера", () ->
                client.post(spec -> spec.body(body), mapperUrl(Endpoints.Splitter.SPLITTER_CONFIG))
        );
    }

    public ValidatableResponseWrapper split(Object body) {
        return Allure.step("Выполнить split", () ->
                client.post(spec -> spec.body(body), mapperUrl(Endpoints.Splitter.SPLITTER_SPLIT))
        );
    }

    public ValidatableResponseWrapper calculatePreliminary(Object body) {
        return Allure.step("Выполнить pre-calculate", () ->
                client.post(spec -> spec.body(body), mapperUrl(Endpoints.Splitter.SPLITTER_PRECALCULATE))
        );
    }

    public ValidatableResponseWrapper calculatePreliminary(SplitterPrecalcRequestDto body) {
        return Allure.step("Выполнить pre-calculate", () ->
                client.post(spec -> spec.body(body), mapperUrl(Endpoints.Splitter.SPLITTER_PRECALCULATE))
        );
    }

    public ValidatableResponseWrapper getVersion() {
        return Allure.step("Получить версию сплиттера", () ->
                client.get(spec -> spec, mapperUrl(Endpoints.Splitter.SPLITTER_VERSION))
        );
    }

    public ValidatableResponseWrapper loadReactionsConfig(Object body) {
        if (SplitterConfigLoadMode.isKafka()) {
            return Allure.step("Загрузка конфигурации сплиттера REACTIONS через Kafka", () ->
                    SplitterKafkaConfigLoadClient.load(body, "REACTIONS")
            );
        }
        return Allure.step("Загрузка конфигурации сплиттера через дополнительный endpoint", () ->
                client.post(spec -> spec.body(body), reactionsUrl(Endpoints.Splitter.SPLITTER_REACTIONS_CONFIG))
        );
    }

    public ValidatableResponseWrapper splitReactions(Object body) {
        return Allure.step("Выполнить split через дополнительный endpoint", () ->
                client.post(spec -> spec.body(body), reactionsUrl(Endpoints.Splitter.SPLITTER_REACTIONS_SPLIT))
        );
    }

    public ValidatableResponseWrapper calculateReactionsPreliminary(Object body) {
        return Allure.step("Выполнить pre-calculate через дополнительный endpoint", () ->
                client.post(spec -> spec.body(body), reactionsUrl(Endpoints.Splitter.SPLITTER_REACTIONS_PRECALCULATE))
        );
    }

    public ValidatableResponseWrapper getReactionsVersion() {
        return Allure.step("Получить версию сплиттера через дополнительный endpoint", () ->
                client.get(spec -> spec, reactionsUrl(Endpoints.Splitter.SPLITTER_REACTIONS_VERSION))
        );
    }

    private String mapperUrl(String path) {
        return RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER_MAPPER) + path;
    }

    private String reactionsUrl(String path) {
        return RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER_REACTIONS) + path;
    }
}
