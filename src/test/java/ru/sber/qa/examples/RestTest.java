package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.flow.RestFlow;
import ru.sber.qa.services.rest.RestService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.math.BigDecimal;
import java.util.List;

import static io.perfeccionista.framework.datasource.Stash.stash;
import static io.perfeccionista.framework.invocation.runner.InvocationInfo.assertInvocation;
import static io.perfeccionista.framework.invocation.wrapper.MultipleAttemptInvocationWrapper.repeatInvocation;
import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.JsonMatchers.*;
import static ru.sber.qa.matchers.RestMatchers.*;
import static ru.sber.qa.matchers.XmlMatchers.*;
import static ru.sber.qa.matchers.conditions.TextConditions.*;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class RestTest {

    /**
     * Для работы с сертификатами необходимо добавить SSlConfig и передать его в запросе
     * В данном примере сертификаты в формате JKS
     */
    RestAssuredConfig JKS_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли для keyStore и trustStore взяты из test.properties и расшифрованы автоматически
                    // !ВАЖНО! для дешифрования требуется в переменные окружения добавить encryption.password,
                    // использованный при шифровании паролей
                    // подробнее о шифровании можно узнать в классе DecryptionTest
//                    .trustStore("путь_до/keystore.jks", TEST_CONFIG.keystorePass())
//                    .keyStore("путь_до/truststore.jks", TEST_CONFIG.truststorePass())
                    //при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );


    /**
     * Если у вас сертификаты в формате cert.pem и ключ cert.key, то можно воспользоваться таким конфигом.
     * Предварительно их нужно конвертировать в формат p12.
     * Команда для конвертации: openssl pkcs12 -export -out keystore.p12 -inkey your_key.key -in your_cert.pem
     * После отправки команды будет запрошен пароль к your_key.key
     * <p>
     * Если у вас сертификаты в формате cert.pem и ключ key.pem, то конвертировать можно так:
     * openssl pkcs12 -export -in cert.pem -inkey key.pem -out certificate.p12 -name "certificate"
     * После отправки команды будет запрос на пароль(придумать), которым будет определен доступ к ключу в дальнейшем.
     * <p>
     * Если сертификаты в формате p12, то просто добавить по примеру:
     */
    RestAssuredConfig P12_CONFIG = RestAssuredConfig.config().sslConfig(
            new SSLConfig()
                    // пароли можно и нужно шифровать, вариант с шифрованием приведен в конфиге выше
//                    .keyStore("путь/к/keystore.p12", "ваш_пароль_от_сертификата")
//                    .keystoreType("PKCS12")
                    // при необходимости отключить валидацию сертификата
                    .relaxedHTTPSValidation()
    );

    /**
     * Тест можно написать несколькими способами
     * Примеры:
     * <pre>
     * - {@link #restApiInitTest2(RestService restService)} - через step().
     * - {@link #restApiInitTestWithFlowSyntax2()} - через Flow.
     * </pre>
     */
    @Test
    @DisplayName("Тест с 2 шагами, проверкой XML и JSON(через step())")
    void restApiInitTest2(RestService restService) {

        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    // Описать спецификацию(spec -> spec.baseUri()/header() и т.д.) и выполнить get/put/post/patch запрос
                    .get(spec -> spec,
                            // при необходимости работы с сертификатами добавить sslConfig(описание см выше)
                            // .config(CONFIG),
                            "http://restpvatftest.sbermock.sigma.sbrf.ru/xmlResponse")
                    // по результатам запроса возвращается ValidatableResponseWrapper, который позволяет вызвать should
                    // и провести ряд проверок.
                    // !важно! в .should для ValidatableResponseWrapper выполняются только RestMatchers
                    // Для просмотра доступных мэтчеров перейти в ru.sber.qa.matchers.RestMatchers
                    .should(
                            haveStatusCode(HttpStatus.SC_OK),
                            notHaveEmptyBody(),
                            haveBodyWithText("Rick Grimes")
                    )
                    // При необходимости преобразовать в ValidatableJson или ValidatableXml
                    .toValidatableXml()
                    // и также через .should() выполнить проверки.
                    // для ValidatableJson выполняются только JsonMatchers
                    // для ValidatableXml выполняются только XmlMatchers
                    .should(
                            haveXmlValueEqualTo("students.student[0].name", "Rick Grimes"),
                            haveXmlValueContains("students.student", "Dixon"),
                            haveXmlValue("students.student[2].name", equalToText("Maggie"))
                    );
        });
        step("Проверяем ответ на тестовый запрос 2", () -> {
            restService.restClient()
                    .get(spec -> spec, "http://restpvatftest.sbermock.sigma.sbrf.ru/cookies")
                    .should(
                            haveStatusCode(HttpStatus.SC_OK),
                            haveBodyJsonValueEqualTo("goal", "Check Cookies with response delay"),
                            notHaveEmptyBody(),
                            // валидация cookie
                            haveCookieName("Coockie1"),
                            haveCookieValue("dfghdfh87sd8f7hh897s9df9h79sdjn"),
                            haveCookie("Coockie1", "dfghdfh87sd8f7hh897s9df9h79sdjn"),
                            // валидация BigDeсimal
                            haveBodyJsonValueEqualTo("BigDecimal", new BigDecimal("1353513.5")))
                    .toValidatableJson()
                    .should(evaluateJsonPathExpressions(List.of(
                                    "delay == 5"
                            ))
                    );
        });
    }

    @Test
    @DisplayName("Тест с 1 шагом, проверкой JSON(через Flow)")
    void restApiInitTestWithFlowSyntax2() {
        // Чтобы написать тест на флоу, необходимо создать класс с флоу(в данном случае RestTestFlow.class) и реализовать
        // все необходимые интерфейсы для работы с шагами(в данном случае Flow и RestFlow)
        // В RestFlow описан метод rest(), который позволяет вызвать все методы для работы с запросами(RestSteps)
        // при помощи вызова flow.rest().get() и т.д.
        FlowRunner.flowRunnerFor(RestTestFlow.class)
                // описываем step и вызываем flow
                .step("Проверяем ответ на тестовый запрос", flow -> {
                    // в нашем случаем flow.rest() возвращает RestSteps,
                    // который позволяет вызвать все методы для работы с запросами(RestSteps)
                    ValidatableResponseWrapper response = flow.rest()
                            .post(spec -> spec,
                                    "http://restpvatftest.sbermock.sigma.sbrf.ru/jsonresponse");

                    //В ответ возращается ValidatableResponseWrapper, который позволяет вызвать should
                    response.should(
                                    haveStatusCode(HttpStatus.SC_OK),
                                    haveBodyJsonValueEqualTo("code", "11"),
                                    haveBodyJsonValue("code", notEqualToText("0")),
                                    haveBodyJsonValue("rqUid", isBlank()),
                                    notHaveEmptyBody())
                            //преобразуем ответ в json и валидируем его
                            .toValidatableJson()
                            .should(
                                    // ищем ключ по JsonPath
                                    notHaveJsonKey("result.locked.locked", "Этого значения не должно быть"),
                                    haveJsonKey("result", "Этот параметр должен быть в ответе"),
                                    evaluateJsonPathExpressions(List.of(
                                            "result.current_time == 1732024955741",
                                            "status == 200")))
                            // фильтруется ответ по JsonPath, на выходе вложенная сущность,
                            // которую можно проверить на наличие ключей
                            .filter("result.vpn")
                            .should(haveJsonKey("description"),
                                    notHaveJsonKey("locked"));
                    //если необходимо сохранить значение из ответа в stash, то можно использовать методы
                    stash().put("result.current_time", response.toResponse().jsonPath().getString("result.current_time"));
                    stash().putIfAbsent("result.current_time", response.toResponse().jsonPath().getString("result.current_time"));
                })

                .step("Используем stash", flow -> {
                    //далее можно использовать значения, сохраненные ранее в stash, stash доступен в любом месте теста
                    System.out.println(stash().get("result.current_time"));
                })

                .run();
    }

    @Test
    void testResponseValue(RestService restService) {

        String requestBody = """
                {
                  "message": "string",
                  "params": [],
                  "changedParams": []
                }""";

        step("Проверяем ответ на тестовый запрос", () -> {
            restService.restClient()
                    .post(spec ->
                                    spec

                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "*/*")
                                            .body(requestBody),

                            "http://ingress-http.ci07963639-dev-terra000003-abtm-back.apps.dev-terra000003-ids.ocp.delta.sbrf.ru/api/v1/message/audit")
                    .should(
                            haveStatusCode(HttpStatus.SC_OK));

                    /* .should(
                            // ищем ключ по JsonPath
                            notHaveJsonKey("result.locked", "Этого значения не должно быть"),
                            haveJsonKey("result", "Этот параметр должен быть в ответе"),
                            evaluateJsonPathExpressions(List.of(
                                    "result.current_time == 1732024955741",
                                    "status == 200")))

                     */
                    // фильтруется ответ по JsonPath, на выходе вложенная сущность, которую можно проверить на наличие ключей

        });
    }

    @Test
    void testXmlResponseValue(RestService restService) {
        step("Проверяем ответ XML на тестовый запрос", () -> {
            restService.restClient()
                    .get(spec -> spec,
                            "http://restpvatftest.sbermock.sigma.sbrf.ru/xmlResponse")
                    .should(
                            haveStatusCode(HttpStatus.SC_OK),
                            notHaveEmptyBody(),
                            haveBodyWithText("Rick Grimes"))
                    .toValidatableXml()
                    .should(
                            haveXmlValueEqualTo("students.student[0].name", "Rick Grimes"),
                            haveXmlValueContains("students.student", "Dixon"),
                            haveXmlValue("students.student[2].name", equalToText("Maggie"))
                    );
        });
    }

    @Test
    void cookieValidationTest(RestService restService) {
        step("Проверяем Cookie с ожиданием ответа(ответ с задержкой)", () -> repeatInvocation(
                assertInvocation("Ожидаем ответ"), () -> {
                    restService.restClient()
                            .get(spec -> spec, "http://restpvatftest.sbermock.sigma.sbrf.ru/cookies")
                            .should(
                                    haveStatusCode(HttpStatus.SC_OK),
                                    haveBodyJsonValueEqualTo("delay", "5"),
                                    notHaveEmptyBody(),
                                    // валидация cookie
                                    haveCookieName("Coockie1"),
                                    haveCookieValue("dfghdfh87sd8f7hh897s9df9h79sdjn"),
                                    haveCookie("Coockie1", "dfghdfh87sd8f7hh897s9df9h79sdjn"),
                                    // валидация BigDeсimal
                                    haveBodyJsonValueEqualTo("BigDecimal", new BigDecimal("1353513.5")))
                            .toValidatableJson()
                            .should(evaluateJsonPathExpressions(List.of(
                                            "delay == 5"
                                    ))
                            );
                }));
    }

    static class RestTestFlow implements Flow, RestFlow {
    }
}