package ru.sber.qa.splitter.ConvertedIFT;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@AnyConfigLoadMode
public class SplitterAdditionalExpLab2148Test {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
     String endpointConfig=Endpoints.Splitter.SPLITTER_CONFIG;
     String endpointReq=Endpoints.Splitter.SPLITTER_SPLIT;


    /** Общее хранилище для id между тестами. */
@AnyConfigLoadMode
    public static class SharedState {
    }

    @Test
    @DisplayName("Angelina #1 - ExpLab-2148 не привязался эксп")
    void testAdditional1(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1/req_pos.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);


        step("Загружаем конфиг сплиттера", () -> {
            var config = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(message),

                            splitterBaseUri+endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;
        });

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;
        });
    }


    @Test
    @DisplayName("Angelina #1_1 - ExpLab-2148 приоритезаци по слою")
    void testAdditional1_1(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1_1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1_1/req_pos.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);

        step("Загружаем конфиг сплиттера", () -> {
            var config = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(message),

                            splitterBaseUri+endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;

        });

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;
        });
    }

    @Test
    @DisplayName("Angelina #1_2 - ExpLab-2148 пустой объект ")
    void testAdditional2(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1_2/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/ExpLab-2148/1_2/req_pos.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto message =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);

        step("Загружаем конфиг сплиттера", () -> {
            var config = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(message),

                            splitterBaseUri+endpointConfig)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;

        });

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK))
                    ;
        });
    }

}

