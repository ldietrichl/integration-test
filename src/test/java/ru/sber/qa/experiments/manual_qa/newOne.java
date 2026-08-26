package ru.sber.qa.experiments.manual_qa;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.http.ContentType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import config.environment.EnvironmentConfigurationExample;
import ru.sber.qa.services.rest.RestService;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class newOne {
    private static final String messageAuditBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.MESSAGE_AUDIT);
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

                            messageAuditBaseUri + "/api/v1/message/audit")
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


}
