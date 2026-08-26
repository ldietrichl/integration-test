package ru.sber.qa.splitter;

import constants.Endpoints;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.SplittingConfigMessageDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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

import static io.qameta.allure.Allure.addAttachment;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Класс для ручной exploratory-проверки сценариев Splitter.
 *
 * Внутри:
 * 1. Позитивный контрольный тест, который стабильно проходит на текущем стенде.
 * 2. Сценарий воспроизведения проблемы pre-calculate -> split -> пустой body.
 */
//@Disabled("Exploratory test for manual runs")
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class SplitterHappyPassPost_precalc_split_issue_v2 {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
    // Переключение стенда вручную
    String endpointConfig = Endpoints.Splitter.SPLITTER_CONFIG;
    String endpointSplit = Endpoints.Splitter.SPLITTER_SPLIT;
    String endpointPrecalc = Endpoints.Splitter.SPLITTER_PRECALCULATE;

    @Test
    @DisplayName("Exploratory: успешный контрольный split сценарий")
    void successfulControlSplitScenario(RestService restService) throws Exception {
        String configBody = resourceText("splitter/convertedIFT/base/6/config.json");
        String splitBody = resourceText("splitter/convertedIFT/base/6/req_pos.json");

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message = objectMapper.readValue(configBody, SplittingConfigMessageDto.class);

        step("Загружаем контрольный валидный конфиг", () -> {
            Response response = restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(message),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK)).toResponse();
                    //.extract().response();
            attachJson("01_success_config_request.json", configBody);
            attachJson("02_success_config_response.json", response.asPrettyString());
        });

        step("Выполняем контрольный split и убеждаемся, что body не пустой", () -> {
            Response response = restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(splitBody),
                            splitterBaseUri + endpointSplit)
                    .should(haveStatusCode(HttpStatus.SC_OK)).toResponse();
                    //.extract().response();

            String responseBody = response.asString();
            attachJson("03_success_split_request.json", splitBody);
            attachJson("04_success_split_response.json", responseBody);

            assertFalse(responseBody == null || responseBody.isBlank(),
                    "Control split response body must not be empty");
        });
    }

    @Test
    @DisplayName("Exploratory: successful pre-calculate then split returns empty body")
    void precalcThenSplitEmptyBodyIssue(RestService restService) throws Exception {
        String configBody = resourceText("splitter/convertedIFT/base/3/config.json");
        String precalcBody = precalcRequestBody();
        String splitAfterPrecalcBody = splitAfterPrecalcRequestBody();

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message = objectMapper.readValue(configBody, SplittingConfigMessageDto.class);

        step("Загружаем конфиг перед предрасчетом", () -> {
            Response response = restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(message),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK)).toResponse();
                    //.extract().response();
            attachJson("11_precalc_config_request.json", configBody);
            attachJson("12_precalc_config_response.json", response.asPrettyString());
        });

        step("Вызываем pre-calculate", () -> {
            Response response = restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(precalcBody),
                            splitterBaseUri + endpointPrecalc)
                    .should(haveStatusCode(HttpStatus.SC_OK)).toResponse();
                    //.extract().response();
            attachJson("13_precalc_request.json", precalcBody);
            attachJson("14_precalc_response.json", response.asPrettyString());
        });

        step("После предрасчета вызываем split и проверяем, что body не должен быть пустым", () -> {
            Response response = restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .header("Content-Type", "application/json")
                                    .accept("application/json")
                                    .body(splitAfterPrecalcBody),
                            splitterBaseUri + endpointSplit)
                    .should(haveStatusCode(HttpStatus.SC_OK)).toResponse();
                    //.extract().response();

            String responseBody = response.asString();
            attachJson("15_split_after_precalc_request.json", splitAfterPrecalcBody);
            attachJson("16_split_after_precalc_response.json", responseBody);

            assertFalse(responseBody == null || responseBody.isBlank(),
                    "Split response body after successful pre-calculate must not be empty");
        });
    }

    private String resourceText(String resourcePath) throws URISyntaxException, IOException {
        Path path = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(resourcePath)).toURI());
        return Files.readString(path);
    }

    private String precalcRequestBody() {
        return """
                {
                  "requestId": "%s",
                  "soConfigVersion": 1,
                  "splittingObjects": [
                    {
                      "uniqueConfigurationId": "precalc-object-001",
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

    private void attachJson(String name, String content) {
        addAttachment(name, "application/json", content, ".json");
    }
}

