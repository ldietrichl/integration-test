package ru.sber.qa.splitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigurationExample;
import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import dto.splitter.SplittingConfigMessageDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.services.rest.RestService;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class SplitterVintageHappyPassPost {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);

    @Test
    @DisplayName("Запрашиваем версию splitter сервиса")
    void exploratoryScenarioForSpecClarification(RestService restService) {


        step("Запрашиваем версию splitter сервиса", () ->
                restService.restClient()
                        .post(spec -> spec
                                        .contentType(ContentType.JSON)
                                        .header("Content-Type", "application/json")
                                        .accept("application/json")
                                        .body("fff"),
                                splitterBaseUri + "/api/v1/splitter-vintage/split")
                        .should(haveStatusCode(HttpStatus.SC_OK))
        );


    }
}
