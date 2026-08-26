package ru.sber.qa.messages;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;


import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import request.dictionaries.DictionariesRequestFactory;
import request.messages.MessagesParams;
import request.messages.MessagesRequestFactory;
import config.environment.EnvironmentConfigurationExample;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.RestService;

import java.util.UUID;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class MessagesPostAudit {
    private static final String messageServiceBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.MESSAGES);
    private final DictionariesRequestFactory factory = new DictionariesRequestFactory();


    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /** Общее хранилище для id между тестами. */
    public static class SharedState {
        public static Long experimentId;
    }


    @Test
    @DisplayName("Успешное создание события аудита")

    void testSendAuditMessageDto(RestService restService, KafkaService kafkaService) {


                //собираем dto
        MessagesParams defaultParams = MessagesParams.builder()
                .session(UUID.randomUUID().toString())
                .userName("Малышев Илья")
                .build();
        MessagesRequestFactory factory = new MessagesRequestFactory();

        //превращаем message в строку
        String bodyJson = factory.toJson(defaultParams);

        step("Проверяем ответ на тестовый запрос", () -> {
            var response = restService.restClient()
                    .post(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(bodyJson),

                            messageServiceBaseUri+"/api/v1/message/audit")

                    .should(
                            haveStatusCode(HttpStatus.SC_OK)
                            );
            //todo: проверка на то что в kafka пришло сообщение


        });


    }


    @Test
    @DisplayName("Невалидное тело запроса  события аудита")

    void testSendAuditValidationError(RestService restService, KafkaService kafkaService) {


        //формируем невалидное тело запроса
        String bodyJson = "invalid body";

        step("Проверяем ответ на тестовый запрос", () -> {
            var response = restService.restClient()
                    .post(spec ->
                                    spec

                                            .config(P12_CONFIG)
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(bodyJson),

                            messageServiceBaseUri+"/api/v1/message/audit")

                    .should(
                            haveStatusCode(HttpStatus.SC_BAD_REQUEST)
                    );
            //todo: проверка на то что в kafka пришло сообщение


        });

    }

}
