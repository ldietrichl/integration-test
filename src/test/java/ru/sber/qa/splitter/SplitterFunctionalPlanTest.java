package ru.sber.qa.splitter;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import constants.Endpoints;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.SplittingConfigMessageDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.allure.CriticalRegression;
import util.support.SplitterVersionProvider;

import java.util.UUID;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.SplitterAssertions.shouldBeBadRequestError;

/**
 * Набор интеграционных тестов, собранный по функциональному тест-плану для AB Splitter.
 *
 * В классе реализованы стабильные сценарии, которые укладываются в текущий REST-паттерн проекта:
 * load config -> split -> проверки через evaluatable JsonPath.
 *
 * Не включены отдельные сценарии alternative/rollback-alternative и return-suppressed,
 * потому что они завязаны на runtime-настройки правил сплиттера и в текущем проекте нет
 * единого хелпера/фикстуры для переключения этих параметров окружения.
 */
@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@AnyConfigLoadMode
public class SplitterFunctionalPlanTest {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);

    private final ObjectMapper objectMapper = new ObjectMapper();
    String endpointConfig = Endpoints.Splitter.SPLITTER_CONFIG;
    String endpointReq = Endpoints.Splitter.SPLITTER_SPLIT;

    private static final long BASE_VERSION = SplitterVersionProvider.nextVersion();

    private static final String OBJECT_ONE = """
            {
              "objectId": "11111111-1111-1111-1111-111111111111",
              "objectParams": [
                {"paramCode": "configCommId", "paramValues": ["1001"], "dataType": "INTEGER"},
                {"paramCode": "cjId", "paramValues": ["123"], "dataType": "INTEGER"},
                {"paramCode": "sellingProductId", "paramValues": ["1-SSSV6DD"], "dataType": "STRING"},
                {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                {"paramCode": "templateId", "paramValues": ["120321"], "dataType": "INTEGER"}
              ]
            }
            """;

    private static final String OBJECT_TWO = """
            {
              "objectId": "22222222-2222-2222-2222-222222222222",
              "objectParams": [
                {"paramCode": "configCommId", "paramValues": ["1002"], "dataType": "INTEGER"},
                {"paramCode": "cjId", "paramValues": ["123"], "dataType": "INTEGER"},
                {"paramCode": "sellingProductId", "paramValues": ["1-XCDV6TA"], "dataType": "STRING"},
                {"paramCode": "channelId", "paramValues": ["20"], "dataType": "INTEGER"},
                {"paramCode": "templateId", "paramValues": ["120222"], "dataType": "INTEGER"}
              ]
            }
            """;

    private SplittingConfigMessageDto configMessage(long version, boolean forceConfigLoad, String splittingConfigJson) {
        try {
            JsonNode splittingConfig = objectMapper.readTree(splittingConfigJson);
            String uuid = UUID.randomUUID().toString();
            return new SplittingConfigMessageDto(
                    uuid,
                    uuid,
                    version,
                    forceConfigLoad,
                    "MAPPER",
                    splittingConfig
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось распарсить splittingConfigJson", e);
        }
    }

    private String splitRequest(String requestId, String splittingId, String requestParamsJson, String splittingObjectsJson) {
        return """
                {
                  "requestId": "%s",
                  "splittingId": "%s",
                  "requestParams": %s,
                  "splittingObjects": %s
                }
                """.formatted(requestId, splittingId, requestParamsJson, splittingObjectsJson);
    }

    private String objectResultPath(String objectId, String ruleCode) {
        return "splittingResults.find { it.objectId == '%s' }.objectResults.find { it.ruleCode == '%s' }"
                .formatted(objectId, ruleCode);
    }

    private String objectPath(String objectId) {
        return "splittingResults.find { it.objectId == '%s' }".formatted(objectId);
    }

    private String baseConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 101,
                      "purpose": "DCG",
                      "salt": "SALT-101",
                      "objectSelectConditions": [
                        {
                          "id": 1,
                          "rules": [
                            [
                              {
                                "dataType": "STRING",
                                "paramCode": "sellingProductId",
                                "paramSource": "SPLITTING_OBJECTS",
                                "operatorCode": "equal",
                                "values": ["1-XCDV6TA"]
                              }
                            ]
                          ]
                        }
                      ],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 10000}],
                          "splittingResults": [
                            {
                              "conditionId": 1,
                              "resultParams": [
                                {"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String requestParamsConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 301,
                      "salt": "SALT-301",
                      "objectSelectConditions": [
                        {
                          "id": 1,
                          "rules": [
                            [
                              {
                                "dataType": "INTEGER",
                                "paramCode": "age",
                                "paramSource": "REQUEST_PARAMS",
                                "operatorCode": "more_equal",
                                "values": ["50"]
                              }
                            ]
                          ]
                        }
                      ],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 10000}],
                          "splittingResults": [
                            {
                              "conditionId": 1,
                              "resultParams": [
                                {"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String andOrConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 401,
                      "salt": "SALT-401",
                      "objectSelectConditions": [
                        {
                          "id": 1,
                          "rules": [
                            [
                              {
                                "dataType": "INTEGER",
                                "paramCode": "age",
                                "paramSource": "REQUEST_PARAMS",
                                "operatorCode": "equal",
                                "values": ["50"]
                              },
                              {
                                "dataType": "STRING",
                                "paramCode": "sellingProductId",
                                "paramSource": "SPLITTING_OBJECTS",
                                "operatorCode": "equal",
                                "values": ["1-XCDV6TA"]
                              }
                            ],
                            [
                              {
                                "dataType": "STRING",
                                "paramCode": "segment",
                                "paramSource": "REQUEST_PARAMS",
                                "operatorCode": "equal",
                                "values": ["segment1"]
                              },
                              {
                                "dataType": "STRING",
                                "paramCode": "sellingProductId",
                                "paramSource": "SPLITTING_OBJECTS",
                                "operatorCode": "equal",
                                "values": ["1-SSSV6DD"]
                              }
                            ]
                          ]
                        }
                      ],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 10000}],
                          "splittingResults": [
                            {
                              "conditionId": 1,
                              "resultParams": [
                                {"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String priorityConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 601,
                      "salt": "SALT-601",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]}]
                    },
                    {
                      "id": 602,
                      "salt": "SALT-602",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                    },
                    {
                      "id": 603,
                      "salt": "SALT-603",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["2"], "dataType": "INTEGER"}]}]}]
                    }
                  ]
                }
                """;
    }

    private String tieConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 701,
                      "salt": "SALT-701",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                    },
                    {
                      "id": 702,
                      "salt": "SALT-702",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["3"], "dataType": "INTEGER"}]}]}]
                    }
                  ]
                }
                """;
    }

    private String invalidActionConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 801,
                      "salt": "SALT-801",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "unknownAction", "paramValues": ["999"], "dataType": "INTEGER"}]}]}]
                    },
                    {
                      "id": 802,
                      "salt": "SALT-802",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]}]
                    }
                  ]
                }
                """;
    }

    private String filterConfigJson(int experimentId, int actionType) {
        return """
                {
                  "experiments": [
                    {
                      "id": %d,
                      "salt": "SALT-%d",
                      "objectSelectConditions": [{"id": 1, "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]}],
                      "groups": [{"code": "A", "shares": [{"shareFrom": 0, "shareTo": 10000}], "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["%d"], "dataType": "INTEGER"}]}]}]
                    }
                  ]
                }
                """.formatted(experimentId, experimentId, actionType);
    }

    private String invalidNoSaltOrLayerConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 901,
                      "objectSelectConditions": [
                        {
                          "id": 1,
                          "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]
                        }
                      ],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 10000}],
                          "splittingResults": [{"conditionId": 1, "resultParams": [{"paramCode": "actionType", "paramValues": ["1"], "dataType": "INTEGER"}]}]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String invalidNoConditionsConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 902,
                      "salt": "SALT-902",
                      "objectSelectConditions": [],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 10000}],
                          "splittingResults": []
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String invalidRangesConfigJson() {
        return """
                {
                  "experiments": [
                    {
                      "id": 903,
                      "salt": "SALT-903",
                      "objectSelectConditions": [
                        {
                          "id": 1,
                          "rules": [[{"dataType": "STRING", "paramCode": "sellingProductId", "paramSource": "SPLITTING_OBJECTS", "operatorCode": "equal", "values": ["1-XCDV6TA"]}]]
                        }
                      ],
                      "groups": [
                        {
                          "code": "A",
                          "shares": [{"shareFrom": 0, "shareTo": 7000}],
                          "splittingResults": []
                        },
                        {
                          "code": "B",
                          "shares": [{"shareFrom": 6000, "shareTo": 10000}],
                          "splittingResults": []
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    @Test
    @Disabled("Тест зависит от полностью чистого состояния стенда; на текущем окружении конфигурация уже загружена")
    @Order(1)
    @DisplayName("SPL-16. Ошибка NO_SPLIT_CONFIG при вызове split без предварительной загрузки конфигурации")
    void splitShouldReturnNoConfigErrorWhenConfigWasNotLoaded(RestService restService) {
        String requestBody = splitRequest(
                UUID.randomUUID().toString(),
                "123456",
                "[]",
                "[" + OBJECT_TWO + "]"
        );

        step("Вызываем split без предварительной загрузки конфигурации", () -> {
            restService.restClient()
                    .post(spec -> spec
                                    .contentType(ContentType.JSON)
                                    .accept("application/json")
                                    .body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("errorCode == 'NO_SPLIT_CONFIG'"));
        });
    }

    @CriticalRegression
    @Test
    @Order(2)
    @DisplayName("CFG-01. Валидный конфиг принимается и начинает применяться в split")
    void configShouldBeLoaded(RestService restService) {
        long version = BASE_VERSION + 1;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Отправляем валидный конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем через split, что активна нужная версия конфига", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version)));
        });
    }

    @CriticalRegression
    @Test
    @Order(3)
    @DisplayName("CFG-02. Более старая версия конфига не должна менять активную версию")
    void oldConfigVersionShouldBeRejected(RestService restService) {
        long actualVersion = BASE_VERSION + 2;
        long oldVersion = BASE_VERSION + 1;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем новую версию конфига", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(actualVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Пытаемся загрузить более старую версию", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(oldVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем через split, что активная версия не изменилась", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(actualVersion)));
        });
    }

    @CriticalRegression
    @Test
    @Order(4)
    @DisplayName("CFG-03. Более старая версия загружается при forceConfigLoad=true")
    void oldConfigVersionShouldBeLoadedWhenForceEnabled(RestService restService) {
        long actualVersion = BASE_VERSION + 4;
        long oldVersion = BASE_VERSION + 3;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем новую версию конфига", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(actualVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Загружаем более старую версию с forceConfigLoad=true", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(oldVersion, true, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем через split, что активной стала принудительно загруженная версия", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(oldVersion)));
        });
    }

    @CriticalRegression
    @Test
    @Order(5)
    @DisplayName("CFG-04. Конфиг без salt и без layer не должен становиться активным")
    void configWithoutSaltOrLayerShouldBeRejected(RestService restService) {
        long validVersion = BASE_VERSION + 5;
        long invalidVersion = BASE_VERSION + 6;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем валидный конфиг как опорную версию", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(validVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Отправляем невалидный конфиг без salt и без layer", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(invalidVersion, false, invalidNoSaltOrLayerConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST));
        });

        step("Проверяем через split, что активная версия не изменилась", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion)));
        });
    }

    @CriticalRegression
    @Test
    @Order(6)
    @DisplayName("CFG-05. Конфиг без objectSelectConditions не должен становиться активным")
    void configWithoutConditionsShouldBeRejected(RestService restService) {
        long validVersion = BASE_VERSION + 7;
        long invalidVersion = BASE_VERSION + 8;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем валидный конфиг как опорную версию", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(validVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Отправляем невалидный конфиг без objectSelectConditions", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(invalidVersion, false, invalidNoConditionsConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST));
        });

        step("Проверяем через split, что активная версия не изменилась", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion)));
        });
    }

    @CriticalRegression
    @Test
    @Order(7)
    @DisplayName("CFG-06. Конфиг с пересечением диапазонов не должен становиться активным")
    void configWithIntersectingRangesShouldBeRejected(RestService restService) {
        long validVersion = BASE_VERSION + 8;
        long invalidVersion = BASE_VERSION + 7;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем валидный конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(validVersion, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Отправляем конфиг с пересечением диапазонов групп", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(invalidVersion, false, invalidRangesConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем через split, что активная версия не изменилась", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(validVersion)));
        });
    }

    @CriticalRegression
    @Test
    @Order(8)
    @DisplayName("SPL-01. Базовый happy path: один объект, один эксперимент, MAIN и ALL заполнены")
    void splitShouldReturnMainAndAllForMatchedObject(RestService restService) {
        long version = BASE_VERSION + 8;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем валидный базовый конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что объект вернулся и для него рассчитаны MAIN и ALL", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("22222222-2222-2222-2222-222222222222") + " != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + " != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "ALL") + " != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps.size() == 1"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "ALL") + ".resultExps.size() == 1"));
        });
    }

    @CriticalRegression
    @Test
    @Order(9)
    @DisplayName("SPL-02. Объект без совпадения по условиям возвращается без objectResults")
    void splitShouldReturnObjectWithoutResultsWhenNoExperimentMatched(RestService restService) {
        long version = BASE_VERSION + 9;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_ONE + "]");

        step("Загружаем базовый конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что объект вернулся без результатов сплиттования", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '11111111-1111-1111-1111-111111111111'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0"));
        });
    }

    @Test
    @Disabled("На текущем стенде конфиги с REQUEST_PARAMS не активируются через /mapper/config")
    @Order(10)
    @DisplayName("SPL-03. Привязка по REQUEST_PARAMS применяется ко всем объектам запроса")
    void splitShouldBindObjectsUsingRequestParams(RestService restService) {
        long version = BASE_VERSION + 10;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456",
                """
                [{"paramCode":"age","paramValues":["60"],"dataType":"INTEGER"}]
                """,
                "[" + OBJECT_ONE + "," + OBJECT_TWO + "]");

        step("Загружаем конфиг с REQUEST_PARAMS", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, requestParamsConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что конфиг с REQUEST_PARAMS действительно применился на стенде", () -> {
            var response = restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq);
            response.should(haveStatusCode(HttpStatus.SC_OK),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version)));
            response.should(
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("11111111-1111-1111-1111-111111111111", "MAIN") + ".resultExps[0].expId == 301"),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 301"));
        });
    }

    @Test
    @Disabled("На текущем стенде конфиги с REQUEST_PARAMS/AND-OR не активируются через /mapper/config")
    @Order(11)
    @DisplayName("SPL-04. Логика AND/OR в objectSelectConditions возвращает оба объекта")
    void splitShouldProcessAndOrConditions(RestService restService) {
        long version = BASE_VERSION + 11;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456",
                """
                [{"paramCode":"age","paramValues":["50"],"dataType":"INTEGER"},{"paramCode":"segment","paramValues":["segment1"],"dataType":"STRING"}]
                """,
                "[" + OBJECT_ONE + "," + OBJECT_TWO + "]");

        step("Загружаем конфиг с AND/OR условиями", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, andOrConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что конфиг с AND/OR действительно применился на стенде", () -> {
            var response = restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq);
            response.should(haveStatusCode(HttpStatus.SC_OK),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == %s".formatted(version)));
            response.should(
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("11111111-1111-1111-1111-111111111111") + " != null"),
                    RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("22222222-2222-2222-2222-222222222222") + " != null"));
        });
    }

    @CriticalRegression
    @Test
    @Order(12)
    @DisplayName("SPL-06. Итоговый эксперимент выбирается по приоритету actionType")
    void splitShouldChooseMainExperimentByActionTypePriority(RestService restService) {
        long version = BASE_VERSION + 12;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем конфиг с несколькими экспериментами и разными actionType", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, priorityConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что в MAIN выбран эксперимент с expId=603", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 603"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("22222222-2222-2222-2222-222222222222") + ".objectFlags.find { it.code == 'filtered' } != null"));
        });
    }

    @CriticalRegression
    @Test
    @Order(13)
    @DisplayName("SPL-07. При равном приоритете выбирается эксперимент с минимальным expId")
    void splitShouldChooseMainExperimentByMinExpIdWhenPriorityIsEqual(RestService restService) {
        long version = BASE_VERSION + 13;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем конфиг с одинаковым приоритетом actionType", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, tieConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что в MAIN выбран expId=701", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 701"));
        });
    }

    @CriticalRegression
    @Test
    @Order(14)
    @DisplayName("SPL-08. Некорректный actionType не участвует в выборе MAIN")
    void splitShouldIgnoreInvalidActionTypeWhenSelectingMainExperiment(RestService restService) {
        long version = BASE_VERSION + 14;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем конфиг с одним невалидным и одним валидным экспериментом", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, invalidActionConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что MAIN построен только по валидному эксперименту", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 802"));
        });
    }

    @CriticalRegression
    @Test
    @Order(15)
    @DisplayName("SPL-12. На текущем стенде при итоговом actionType=2 возвращается filtered=false")
    void splitShouldMarkObjectAsFilteredWhenActionTypeIsTwo(RestService restService) {
        long version = BASE_VERSION + 15;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем конфиг с actionType=2", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, filterConfigJson(1201, 2))),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем фактический флаг filtered на текущем стенде", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("22222222-2222-2222-2222-222222222222") + ".objectFlags.find { it.code == 'filtered' }.value == 'false'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 1201"));
        });
    }

    @CriticalRegression
    @Test
    @Order(16)
    @DisplayName("SPL-13. На текущем стенде при итоговом actionType=4 возвращается filtered=false")
    void splitShouldMarkObjectAsFilteredWhenActionTypeIsFour(RestService restService) {
        long version = BASE_VERSION + 16;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[" + OBJECT_TWO + "]");

        step("Загружаем конфиг с actionType=4", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, filterConfigJson(1301, 4))),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем фактический флаг filtered на текущем стенде", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath("22222222-2222-2222-2222-222222222222") + ".objectFlags.find { it.code == 'filtered' }.value == 'false'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath("22222222-2222-2222-2222-222222222222", "MAIN") + ".resultExps[0].expId == 1301"));
        });
    }

    @CriticalRegression
    @Test
    @Order(17)
    @DisplayName("SPL-17. Невалидный запрос на HTTP-слое возвращает 400 Bad Request")
    void splitShouldReturnValidationFailedForInvalidRequest(RestService restService) {
        long version = BASE_VERSION + 17;
        String invalidRequestBody = """
                {
                  "splittingId": "123456",
                  "requestParams": [],
                  "splittingObjects": [%s]
                }
                """.formatted(OBJECT_TWO);

        step("Загружаем валидный конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Отправляем невалидный запрос без requestId", () -> {
            shouldBeBadRequestError(restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(invalidRequestBody),
                            splitterBaseUri + endpointReq));
        });
    }

    @CriticalRegression
    @Test
    @Order(18)
    @DisplayName("SPL-18. Пустой массив splittingObjects возвращает пустой splittingResults")
    void splitShouldReturnEmptyResultsForEmptyObjects(RestService restService) {
        long version = BASE_VERSION + 18;
        String requestBody = splitRequest(UUID.randomUUID().toString(), "123456", "[]", "[]");

        step("Загружаем валидный конфиг", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(configMessage(version, false, baseConfigJson())),
                            splitterBaseUri + endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK));
        });

        step("Проверяем, что для пустого массива объектов ответ пустой", () -> {
            restService.restClient()
                    .post(spec -> spec.contentType(ContentType.JSON).accept("application/json").body(requestBody),
                            splitterBaseUri + endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 0"));
        });
    }
}

