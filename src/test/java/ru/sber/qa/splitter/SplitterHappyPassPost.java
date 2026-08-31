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
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static io.qameta.allure.Allure.step;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@AnyConfigLoadMode
public class SplitterHappyPassPost {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);

    /**
     * Переключение стенда вручную.
     * Для DEV: String splitterBaseUri = urlDEV;
     * Для LT:  String splitterBaseUri = urlLT;
     */
    String endpointConfig = Endpoints.Splitter.SPLITTER_CONFIG;
    String endpointReq = Endpoints.Splitter.SPLITTER_SPLIT;

    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                    .keystoreType("PKCS12")
                    .relaxedHTTPSValidation()
    );

    //@Disabled("Ручной exploratory-сценарий. Включать точечно для исследования фактического поведения сервиса.")
    @Test
    @DisplayName("EXP-01. Exploratory: REQUEST_PARAMS, OR, alternatives, suppression, no-config")
    void exploratoryScenarioForSpecClarification() throws Exception {
        step("1. Split без предварительной загрузки конфига", () -> {
            String requestBody = twoObjectsSplitRequestJson();
            Response response = postJson(splitterBaseUri + endpointReq, requestBody);
            attach("no-config_split_status", String.valueOf(response.statusCode()));
            attach("no-config_split_response", response.asPrettyString());
        });

        step("2. REQUEST_PARAMS-only condition -> оба объекта должны попасть в результат", () -> {
            String configBody = requestParamsOnlyConfigJson();
            String requestBody = twoObjectsSplitRequestJson();
            Response loadResponse = postConfigRaw(configBody);
            attach("request_params_load_status", String.valueOf(loadResponse.statusCode()));
            attach("request_params_load_response", loadResponse.asPrettyString());

            Response splitResponse = postJson(splitterBaseUri + endpointReq, requestBody);
            attach("request_params_split_status", String.valueOf(splitResponse.statusCode()));
            attach("request_params_split_response", splitResponse.asPrettyString());
        });

        step("3. OR-блоки -> разные ветки должны выбрать разные объекты", () -> {
            String configBody = orRulesConfigJson();
            String requestBody = twoObjectsSplitRequestJson();
            Response loadResponse = postConfigRaw(configBody);
            attach("or_rules_load_status", String.valueOf(loadResponse.statusCode()));
            attach("or_rules_load_response", loadResponse.asPrettyString());

            Response splitResponse = postJson(splitterBaseUri + endpointReq, requestBody);
            attach("or_rules_split_status", String.valueOf(splitResponse.statusCode()));
            attach("or_rules_split_response", splitResponse.asPrettyString());
        });

        step("4. Альтернатива: базовый resource-кейс", () -> {
            String configBody = resourceText("splitter/convertedIFT/base/6/config.json");
            String requestBody = resourceText("splitter/convertedIFT/base/6/req_pos.json");
            Response loadResponse = postConfigRaw(configBody);
            attach("alternative_load_status", String.valueOf(loadResponse.statusCode()));
            attach("alternative_load_response", loadResponse.asPrettyString());

            Response splitResponse = postJson(splitterBaseUri + endpointReq, requestBody);
            attach("alternative_split_status", String.valueOf(splitResponse.statusCode()));
            attach("alternative_split_response", splitResponse.asPrettyString());
        });

        step("5. Полное подавление: resource-кейс", () -> {
            String configBody = resourceText("splitter/convertedIFT/base/8/config.json");
            String requestBody = resourceText("splitter/convertedIFT/base/8/req_pos.json");
            Response loadResponse = postConfigRaw(configBody);
            attach("suppression_load_status", String.valueOf(loadResponse.statusCode()));
            attach("suppression_load_response", loadResponse.asPrettyString());

            Response splitResponse = postJson(splitterBaseUri + endpointReq, requestBody);
            attach("suppression_split_status", String.valueOf(splitResponse.statusCode()));
            attach("suppression_split_response", splitResponse.asPrettyString());
        });
    }

    private Response postConfigRaw(String configBody) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message = objectMapper.readValue(configBody, SplittingConfigMessageDto.class);
        return RestAssured.given()
                .config(P12_CONFIG)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Content-Type", "application/json")
                .body(message)
                .when()
                .post(splitterBaseUri + endpointConfig)
                .then()
                .extract()
                .response();
    }

    private Response postJson(String targetUrl, String body) {
        return RestAssured.given()
                .config(P12_CONFIG)
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .header("Content-Type", "application/json")
                .body(body)
                .when()
                .post(targetUrl)
                .then()
                .extract()
                .response();
    }

    private void attach(String name, String value) {
        Allure.addAttachment(name, "application/json", value, ".json");
    }

    private String resourceText(String resourcePath) throws URISyntaxException, IOException {
        Path path = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource(resourcePath)).toURI());
        return Files.readString(path);
    }

    private String twoObjectsSplitRequestJson() {
        return """
                {
                  \"requestId\": \"exp-req-001\",
                  \"splittingId\": \"123456\",
                  \"requestParams\": [
                    {
                      \"paramCode\": \"age\",
                      \"paramValues\": [\"60\"],
                      \"dataType\": \"INTEGER\"
                    },
                    {
                      \"paramCode\": \"segment\",
                      \"paramValues\": [\"segment1\"],
                      \"dataType\": \"STRING\"
                    }
                  ],
                  \"splittingObjects\": [
                    {
                      \"objectId\": \"22222222-2222-2222-2222-222222222222\",
                      \"objectParams\": [
                        {\"paramCode\": \"configCommId\", \"paramValues\": [\"1002\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"cjId\", \"paramValues\": [\"123\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"sellingProductId\", \"paramValues\": [\"1-XCDV6TA\"], \"dataType\": \"STRING\"},
                        {\"paramCode\": \"channelId\", \"paramValues\": [\"20\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"templateId\", \"paramValues\": [\"120222\"], \"dataType\": \"INTEGER\"}
                      ]
                    },
                    {
                      \"objectId\": \"99999999-9999-9999-9999-999999999999\",
                      \"objectParams\": [
                        {\"paramCode\": \"configCommId\", \"paramValues\": [\"1003\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"cjId\", \"paramValues\": [\"123\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"sellingProductId\", \"paramValues\": [\"1-SSSV6DD\"], \"dataType\": \"STRING\"},
                        {\"paramCode\": \"channelId\", \"paramValues\": [\"20\"], \"dataType\": \"INTEGER\"},
                        {\"paramCode\": \"templateId\", \"paramValues\": [\"120333\"], \"dataType\": \"INTEGER\"}
                      ]
                    }
                  ]
                }
                """;
    }

    private String requestParamsOnlyConfigJson() {
        return """
                {
                  \"messageId\": \"exp-msg-001\",
                  \"requestId\": \"exp-load-001\",
                  \"configVersion\": 500001,
                  \"forceConfigLoad\": true,
                  \"splittingPointCode\": \"MAPPER\",
                  \"splittingConfig\": {
                    \"experiments\": [
                      {
                        \"id\": 301,
                        \"purpose\": \"REQ\",
                        \"salt\": \"SALT-301\",
                        \"objectSelectConditions\": [
                          {
                            \"id\": 1,
                            \"rules\": [
                              [
                                {
                                  \"dataType\": \"INTEGER\",
                                  \"paramCode\": \"age\",
                                  \"paramSource\": \"REQUEST_PARAMS\",
                                  \"operatorCode\": \"equal\",
                                  \"values\": [\"60\"]
                                }
                              ]
                            ]
                          }
                        ],
                        \"groups\": [
                          {
                            \"code\": \"A\",
                            \"shares\": [{\"shareFrom\": 0, \"shareTo\": 10000}],
                            \"splittingResults\": [
                              {
                                \"conditionId\": 1,
                                \"resultParams\": [
                                  {\"paramCode\": \"actionType\", \"paramValues\": [\"1\"], \"dataType\": \"INTEGER\"}
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;
    }

    private String orRulesConfigJson() {
        return """
                {
                  \"messageId\": \"exp-msg-002\",
                  \"requestId\": \"exp-load-002\",
                  \"configVersion\": 500002,
                  \"forceConfigLoad\": true,
                  \"splittingPointCode\": \"MAPPER\",
                  \"splittingConfig\": {
                    \"experiments\": [
                      {
                        \"id\": 401,
                        \"purpose\": \"AND_OR\",
                        \"salt\": \"SALT-401\",
                        \"objectSelectConditions\": [
                          {
                            \"id\": 1,
                            \"rules\": [
                              [
                                {
                                  \"dataType\": \"STRING\",
                                  \"paramCode\": \"sellingProductId\",
                                  \"paramSource\": \"SPLITTING_OBJECTS\",
                                  \"operatorCode\": \"equal\",
                                  \"values\": [\"1-XCDV6TA\"]
                                },
                                {
                                  \"dataType\": \"INTEGER\",
                                  \"paramCode\": \"age\",
                                  \"paramSource\": \"REQUEST_PARAMS\",
                                  \"operatorCode\": \"equal\",
                                  \"values\": [\"60\"]
                                }
                              ],
                              [
                                {
                                  \"dataType\": \"STRING\",
                                  \"paramCode\": \"sellingProductId\",
                                  \"paramSource\": \"SPLITTING_OBJECTS\",
                                  \"operatorCode\": \"equal\",
                                  \"values\": [\"1-SSSV6DD\"]
                                },
                                {
                                  \"dataType\": \"STRING\",
                                  \"paramCode\": \"segment\",
                                  \"paramSource\": \"REQUEST_PARAMS\",
                                  \"operatorCode\": \"equal\",
                                  \"values\": [\"segment1\"]
                                }
                              ]
                            ]
                          }
                        ],
                        \"groups\": [
                          {
                            \"code\": \"A\",
                            \"shares\": [{\"shareFrom\": 0, \"shareTo\": 10000}],
                            \"splittingResults\": [
                              {
                                \"conditionId\": 1,
                                \"resultParams\": [
                                  {\"paramCode\": \"actionType\", \"paramValues\": [\"1\"], \"dataType\": \"INTEGER\"}
                                ]
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """;
    }
}

