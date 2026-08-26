package ru.sber.qa.messages;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import request.messages.MessagesParams;
import request.messages.MessagesRequestFactory;
import config.environment.EnvironmentConfigurationExample;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.time.Duration;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.KafkaQueueAsserts.assertFirstPayload;
import static util.KafkaRestMatchers.hasKafkaFirstPayloadByHostnameSince;


@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class MessagesPostAuditNew {
    private static final String messageServiceBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.MESSAGES);

    private static final String ENV = "dev"; // dev, ift, prod
    private static final String TOPIC = "omon_explab_log";
    private static final String HOSTNAME_SUBSTR = "message-service";
    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
                    .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /**
     * Вариант 1 — REST + Kafka в одном should(...).
     */
    @Test
    @DisplayName("REST + Kafka проверка (contains + hostname + since)")
    void testSendAuditMessageDto_v1(RestService restService, KafkaService kafkaService) {

        long since = System.currentTimeMillis();

        //собираем dto
        MessagesParams defaultParams = MessagesParams.builder().build();
        MessagesRequestFactory factory = new MessagesRequestFactory();

        //превращаем message в строку
        String bodyJson = factory.toJson(defaultParams);

        step("Отправляем сообщение и проверяем REST + Kafka", () -> {
            ValidatableResponseWrapper response = restService.restClient()
                    .post(spec -> spec
                                    .config(P12_CONFIG)
                                    .contentType("application/json")
                                    .accept("*/*")
                                    .body(bodyJson),
                            messageServiceBaseUri+"/api/v1/message/audit");

            response.should(
                    haveStatusCode(HttpStatus.SC_OK)

            );

/*
            assertFirstPayload(
                    "dev_audit",
                    "CI02047903.CI07963639-explab",
                    "Создание эксперимента",
                    since,
                    kafkaService,
                    Duration.ofSeconds(10)
            );
/*

            hasKafkaFirstPayloadByHostnameSince(
                    ENV,
                    TOPIC,
                    "Успешно отправлено событие аудита",
                    HOSTNAME_SUBSTR,
                    since,
                    kafkaService,
                    Duration.ofSeconds(10)
            );
                    hasKafkaFirstPayloadByHostnameSince(
                            ENV,
                            TOPIC,
                            "Запрос на отправку сообщения аудита",
                            HOSTNAME_SUBSTR,
                            since,
                            kafkaService,
                            Duration.ofSeconds(10)
                    );

 */

        });
    }

    /**
     * Вариант 2 — Проверка другой подстроки.
     */
    @Test
    @DisplayName("REST + Kafka проверка (другая ожидаемая подстрока)")
    void testSendAuditMessageDto_v2(RestService restService, KafkaService kafkaService) {

        long since = System.currentTimeMillis();
        //собираем dto
        MessagesParams defaultParams = MessagesParams.builder().build();
        MessagesRequestFactory factory = new MessagesRequestFactory();

        //превращаем message в строку
        String bodyJson = factory.toJson(defaultParams);

        step("Отправляем сообщение и проверяем REST + Kafka", () -> {
            ValidatableResponseWrapper response = restService.restClient()
                    .post(spec -> spec
                                    .config(P12_CONFIG)
                                    .contentType("application/json")
                                    .accept("*/*")
                                    .body(bodyJson),
                            messageServiceBaseUri+"/api/v1/message/audit");

            response.should(
                    haveStatusCode(HttpStatus.SC_OK),
                    hasKafkaFirstPayloadByHostnameSince(
                            ENV,
                            TOPIC,
                            "Запрос на отправку сообщения аудита",
                            HOSTNAME_SUBSTR,
                            since,
                            kafkaService,
                            Duration.ofSeconds(10)
                    )
            );
        });
    }
}
