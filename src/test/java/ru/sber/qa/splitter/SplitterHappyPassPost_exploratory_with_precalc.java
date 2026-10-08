package ru.sber.qa.splitter;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import constants.Endpoints;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.SplittingConfigMessageDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.services.rest.RestService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Exploratory-класс для ручной проверки спорных сценариев Splitter.
 *
 * Как использовать:
 * 1. Выберите стенд через переменную splitterBaseUri
 * 2. Снимите @Disabled у нужного метода
 * 3. Посмотрите фактические ответы сервиса
 *
 * ВАЖНО:
 * endpointPrecalc указан как наиболее вероятный вариант для эксперимента.
 * Если на вашем стенде путь отличается, поправьте только значение endpointPrecalc.
 */

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@AnyConfigLoadMode
public class SplitterHappyPassPost_exploratory_with_precalc {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
    // Переключение стенда вручную
    String endpointConfig = Endpoints.Splitter.SPLITTER_CONFIG;
    String endpointSplit = Endpoints.Splitter.SPLITTER_SPLIT;
    // TODO: при необходимости скорректируйте путь под ваш реальный precalc endpoint
    String endpointPrecalc = Endpoints.Splitter.SPLITTER_PRECALCULATE;


    @Test
    @DisplayName("Exploratory: спорные сценарии split для уточнения спецификации")
    void exploratoryScenarioForSpecClarification(RestService restService) throws Exception {
        String configBody = resourceText("splitter/convertedIFT/base/3/config.json");
        String requestParamsBody = requestParamsScenarioBody();
        String orScenarioBody = orScenarioBody();
        String alternativeConfig = resourceText("splitter/convertedIFT/base/6/config.json");
        String alternativeRequest = resourceText("splitter/convertedIFT/base/6/req_pos.json");
        String suppressionConfig = resourceText("splitter/convertedIFT/base/8/config.json");
        String suppressionRequest = resourceText("splitter/convertedIFT/base/8/req_pos.json");

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message = objectMapper.readValue(configBody, SplittingConfigMessageDto.class);

        step("Загружаем базовый конфиг для exploratory-проверок", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(message),
                                splitterBaseUri + endpointConfig)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );

        step("Проверяем REQUEST_PARAMS сценарий", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(requestParamsBody),
                                splitterBaseUri + endpointSplit)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );

        step("Проверяем OR-сценарий", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(orScenarioBody),
                                splitterBaseUri + endpointSplit)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );

        step("Проверяем сценарий с альтернативой", () -> {
            SplittingConfigMessageDto altConfig = objectMapper.readValue(alternativeConfig, SplittingConfigMessageDto.class);
            restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(altConfig),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));

            restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(alternativeRequest),
                            splitterBaseUri + endpointSplit)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем сценарий с полным подавлением", () -> {
            SplittingConfigMessageDto supConfig = objectMapper.readValue(suppressionConfig, SplittingConfigMessageDto.class);
            restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(supConfig),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));

            restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(suppressionRequest),
                            splitterBaseUri + endpointSplit)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });
    }


    @Test
    @DisplayName("Exploratory: простой сценарий предрасчета")
    void exploratorySimplePrecalcScenario(RestService restService) throws Exception {
        String configBody = resourceText("splitter/convertedIFT/base/3/config.json");
        String precalcBody = precalcRequestBody();
        String splitAfterPrecalcBody = splitAfterPrecalcRequestBody();

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message = objectMapper.readValue(configBody, SplittingConfigMessageDto.class);

        step("Загружаем конфиг перед предрасчетом", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(message),
                                splitterBaseUri + endpointConfig)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );

        step("Вызываем calculatePreliminary / precalc endpoint", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(precalcBody),
                                splitterBaseUri + endpointPrecalc)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );

        step("После предрасчета вызываем обычный split по тому же объекту", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body(splitAfterPrecalcBody),
                                splitterBaseUri + endpointSplit)
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );
    }

    private String resourceText(String resourcePath) throws URISyntaxException, IOException {
        Path path = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(resourcePath)).toURI());
        return Files.readString(path);
    }

    private String requestParamsScenarioBody() {
        return """
                {
                  "requestId": "%s",
                  "splittingId": "REQ-PARAMS-EXPLORATORY",
                  "requestParams": [
                    {
                      "paramCode": "age",
                      "paramValues": ["60"],
                      "dataType": "INTEGER"
                    },
                    {
                      "paramCode": "segment",
                      "paramValues": ["segment1"],
                      "dataType": "STRING"
                    }
                  ],
                  "splittingObjects": [
                    {
                      "objectId": "22222222-2222-2222-2222-222222222222",
                      "objectParams": [
                        {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"}
                      ]
                    },
                    {
                      "objectId": "99999999-9999-9999-9999-999999999999",
                      "objectParams": [
                        {"paramCode": "sellingProductId", "paramValues": ["1-SSSV6DD"], "dataType": "STRING"}
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());
    }

    private String orScenarioBody() {
        return """
                {
                  "requestId": "%s",
                  "splittingId": "OR-EXPLORATORY",
                  "requestParams": [
                    {
                      "paramCode": "age",
                      "paramValues": ["60"],
                      "dataType": "INTEGER"
                    },
                    {
                      "paramCode": "segment",
                      "paramValues": ["segment1"],
                      "dataType": "STRING"
                    }
                  ],
                  "splittingObjects": [
                    {
                      "objectId": "22222222-2222-2222-2222-222222222222",
                      "objectParams": [
                        {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"}
                      ]
                    },
                    {
                      "objectId": "99999999-9999-9999-9999-999999999999",
                      "objectParams": [
                        {"paramCode": "sellingProductId", "paramValues": ["1-SSSV6DD"], "dataType": "STRING"}
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());
    }

    private String precalcRequestBody() {
        return """
                {
                  "requestId": "%s",
                  "soConfigVersion": 1,
                  "splittingObjects": [
                    {
                      "uniqueConfigurationId": "precalc-object-001",
                      "objectId": "22222222-2222-2222-2222-222222222222",
                      "objectParams": [
                        {"paramCode": "configCommId", "paramValues": ["1002"], "dataType": "INTEGER"},
                        {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"},
                        {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                        {"paramCode": "templateId", "paramValues": ["120222"], "dataType": "INTEGER"}
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());
    }

    private String splitAfterPrecalcRequestBody() {
        return """
                {
                  "requestId": "%s",
                  "splittingId": "PRECALC-SPLIT-CHECK",
                  "requestParams": [],
                  "splittingObjects": [
                    {
                      "uniqueConfigurationId": "precalc-object-001",
                      "objectId": "22222222-2222-2222-2222-222222222222",
                      "objectParams": [
                        {"paramCode": "configCommId", "paramValues": ["1002"], "dataType": "INTEGER"},
                        {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"},
                        {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                        {"paramCode": "templateId", "paramValues": ["120222"], "dataType": "INTEGER"}
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID());
    }
}

