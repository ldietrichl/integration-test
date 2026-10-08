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
import org.junit.jupiter.api.*;
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


@ExtendWith(PerfeccionistaExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@AnyConfigLoadMode
public class SplitterBaseTest {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
     String endpointConfig=Endpoints.Splitter.SPLITTER_CONFIG;
     String endpointReq=Endpoints.Splitter.SPLITTER_SPLIT;


    /** Общее хранилище для id между тестами. */
@AnyConfigLoadMode
    public static class SharedState {
    }

    @Test
    @DisplayName("Angelina #1")
    void testSendSplitterHappyPassRest1(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/1/req_pos.json")).toURI());
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
    @Disabled("Купмиров сказал не нужно")
    @DisplayName("Angelina #3 - Тестируем корректность обработки результата")
    void testSendSplitterHappyPassRest2(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;
        UUID uuid = UUID.randomUUID();

        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                uuid.toString(),
                uuid.toString(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


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

    @Disabled("Купмиров сказал не нужно")
    @Test
    @DisplayName("Angelina #3_2 - В результатае итогового экспа парамтер actionType и другие параметры")
    void testSendSplitterHappyPassRest(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_2/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_2/req_pos.json")).toURI());
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

    @Disabled("Купмиров сказал не нужно")
    @Test
    @DisplayName("Angelina #3_3 - В результатае итогового экспа нет парамтера actionType")
    void testSendSplitterHappyPassRest3(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_3/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_3/req_pos.json")).toURI());
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

    @Disabled("Купмиров сказал не нужно")
    @Test
    @DisplayName("Angelina #3_4 - В результате итогового экспа actionType  не корректный тип")
    void testSendSplitterHappyPassRest34(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_4/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/3_4/req_pos.json")).toURI());
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
    @DisplayName("Angelina #5 - Тестирование приоритезации по AT")
    void testSendSplitterHappyPassRest5(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/5/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/5/req_pos.json")).toURI());
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
            /*
            {
                    "ruleCode": "MAIN",!
                    "resultExps": [
                        {
                            "conditionId": 1,
                            "expId": 6,!
                            "expGroup": "A",
                            "salt": "24096d2M1e",
                            "layerId": 1,
                            "spreadValue": 5649,
                            "expFlags": null,
                            "groupResultParams": [
                                {
                                    "paramCode": "actionType",
                                    "paramValues": [
                                        "2"!
                                    ],
                                    "dataType": "INTEGER"
                                }
                            ]
                        }
             */

        });

    }

    @Test
    @DisplayName("Angelina #5_2")
    void testSendSplitterHappyPassRest52(RestService restService) throws URISyntaxException, IOException {


        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/5_2/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/5_2/req_pos.json")).toURI());
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
    @DisplayName("Angelina #6 - Тестирование Альтернативу")
    void testSendSplitterHappyPassRest6(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/6/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/6/req_pos.json")).toURI());
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
    @DisplayName("Angelina #6_1")
    void testSendSplitterHappyPassRest61(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/6_1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/6_1/req_pos.json")).toURI());
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
    @DisplayName("Angelina #7 - Тестируем один случай с отменой альтернативы при калибровке")
    void testSendSplitterHappyPassRest7(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/7/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/7/req_pos.json")).toURI());
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
    @DisplayName("Angelina #8 - Тестирование filtered = true 2000")
    void testSendSplitterHappyPassRest8(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8/req_pos.json")).toURI());
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
    @DisplayName("Angelina #8_1 - Тестирование filtered = true 3000")
    void testSendSplitterHappyPassRest81(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_1/req_pos.json")).toURI());
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
    @DisplayName("Angelina #8_2 - Тестирование filtered = true 700")
    void testSendSplitterHappyPassRest82(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_2/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_2/req_pos.json")).toURI());
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
    @DisplayName("Angelina #8_3 - Тестирование filtered = true 200")
    void testSendSplitterHappyPassRest83(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_3/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/convertedIFT/base/8_3/req_pos.json")).toURI());
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

