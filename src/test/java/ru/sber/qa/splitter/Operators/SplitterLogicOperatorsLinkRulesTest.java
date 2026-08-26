package ru.sber.qa.splitter.Operators;

import constants.Endpoints;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import ru.sber.qa.allure.CriticalRegression;

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
public class SplitterLogicOperatorsLinkRulesTest {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
     String endpointConfig=Endpoints.Splitter.SPLITTER_CONFIG;
     String endpointReq=Endpoints.Splitter.SPLITTER_SPLIT;


    /** Общее хранилище для id между тестами. */
    public static class SharedState {
    }


    @CriticalRegression
    @Test
    @DisplayName("1.Логические выражения правил привязки - (commId IN 250524)")
    void SplitterLogicExpression1(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/1/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/1/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                                        ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectResults.size() == 0"))
                    ;
        });
    }

    @CriticalRegression
    @Test
    @DisplayName("2.Логические выражения правил привязки - (commId IN 250524) AND (channelId IN 20)")
    void SplitterLogicExpression2(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/2/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/2/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/2/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectResults.size() == 0"))
                    ;
        });
    }


    @CriticalRegression
    @Test
    @DisplayName("3.Логические выражения правил привязки - (commId IN 250524) AND (channelId IN 20) AND (modelId IN 8)")
    void SplitterLogicExpression3(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/3/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/3/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/3/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectResults.size() == 0"))
                    ;
        });
    }

    @CriticalRegression
    @Test
    @DisplayName("4.Логические выражения правил привязки - (commId IN 250524) OR (modelId IN 8)")
    void SplitterLogicExpression4(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/4/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/4/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/4/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"))
                    ;
        });

    }


    @CriticalRegression
    @Test
    @DisplayName("5.Логические выражения правил привязки - (commId IN 250524 AND channelId IN 20) OR (modelId IN 8 AND channelId IN 19)")
    void SplitterLogicExpression5(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/5/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/5/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/5/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });


    }

    @CriticalRegression
    @Test
    @DisplayName("6.Логические выражения правил привязки - (commId IN 250524 AND channelId IN 20 AND productId EQUAL '2-MERSBMW') OR (modelId IN 8 AND channelId IN 19 AND productId EQUAL '2-TOYOTA')")
    void SplitterLogicExpression6(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/6/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/6/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/6/req_neg.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"))
                    ;
        });

    }

    @CriticalRegression
    @Test
    @DisplayName("7.Логические выражения правил привязки - (commId IN 250524 AND channelId IN 20 AND productId EQUAL '2-MERSBMW') OR (modelId IN 8 AND channelId IN 19 AND productId EQUAL '2-TOYOTA')")
    void SplitterLogicExpression7(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/7/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/7/req_pos.json")).toURI());
        Path pathReqNeg_1 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/7/req_neg_1.json")).toURI());
        Path pathReqNeg_2 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/7/req_neg_2.json")).toURI());
        Path pathReqNeg_3 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/7/req_neg_3.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg_1 = Files.readString(pathReqNeg_1);
        String fileReqNeg_2 = Files.readString(pathReqNeg_2);
        String fileReqNeg_3 = Files.readString(pathReqNeg_3);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[2].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

        step("Запрос негативного сценария 1", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_1),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"))
                    ;
        });

        step("Запрос негативного сценария 2", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_2),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

        step("Запрос негативного сценария 3", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_3),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0"))
                    ;
        });

    }

    @CriticalRegression
    @Test
    @DisplayName("8.Логические выражения правил привязки - (commId IN 250524 AND channelId IN 20) OR (productId EQUAL '2-TOYOTA' AND modelId IN 8) OR ( productId EQUAL '3-NISSAN' AND channelId IN 19)")
    void SplitterLogicExpression8(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/8/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/8/req_pos.json")).toURI());
        Path pathReqNeg_1 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/8/req_neg_1.json")).toURI());
        Path pathReqNeg_2 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/8/req_neg_2.json")).toURI());
        Path pathReqNeg_3 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/8/req_neg_3.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg_1 = Files.readString(pathReqNeg_1);
        String fileReqNeg_2 = Files.readString(pathReqNeg_2);
        String fileReqNeg_3 = Files.readString(pathReqNeg_3);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[2].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

        step("Запрос негативного сценария 1", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_1),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

        step("Запрос негативного сценария 2", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_2),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

        step("Запрос негативного сценария 3", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_3),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0"))
                    ;
        });

    }

    @CriticalRegression
    @Test
    @DisplayName("9.Логические выражения правил привязки - (commId IN 250524 AND channelId IN 20 AND productId EQUAL '1-MERSBMW') OR (productId EQUAL '2-TOYOTA' AND modelId IN 8 AND channelId IN 19) OR ( productId EQUAL '3-NISSAN' AND channelId IN 18 AND modelId IN 10)")
    void SplitterLogicExpression9(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/9/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/9/req_pos.json")).toURI());
        Path pathReqNeg = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/9/req_neg_1.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg = Files.readString(pathReqNeg);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == '46871f54-7d8c-40bb-8ca7-35c667e21789'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '1f53341b-aaa9-4f28-ae70-4bbb91c3c8c2'"))
                    ;
        });

        step("Запрос негативного сценария", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == 'f45e3522-ae0c-426e-948b-11a4ec96c04e'"))
                    ;
        });

    }

    @CriticalRegression
    @Test
    @DisplayName("10.Логические выражения правил привязки - (commId IN 250524) OR (modelId IN 8 AND channelId IN 20 AND productId EQUAL '2-NISSAN')")
    void SplitterLogicExpression10(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/10/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/10/req_pos.json")).toURI());
        Path pathReqNeg_1 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/10/req_neg_1.json")).toURI());
        Path pathReqNeg_2 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/10/req_neg_2.json")).toURI());
        Path pathReqNeg_3 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_expressions_link_rules/10/req_neg_3.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg_1 = Files.readString(pathReqNeg_1);
        String fileReqNeg_2 = Files.readString(pathReqNeg_2);
        String fileReqNeg_3 = Files.readString(pathReqNeg_3);
        Long unixTime = System.currentTimeMillis() / 1000L;;


        ObjectMapper objectMapper = new ObjectMapper();
        SplittingConfigMessageDto rawMessage =
                objectMapper.readValue(fileConfig, SplittingConfigMessageDto.class);
        SplittingConfigMessageDto message= new SplittingConfigMessageDto(
                rawMessage.messageId(),
                rawMessage.requestId(),
                unixTime,
                rawMessage.forceConfigLoad(),
                rawMessage.splittingPointCode(),
                rawMessage.splittingConfig()
        );


        var response = restService.restClient()
                .post(spec -> spec
                                .contentType(ContentType.JSON)
                                .accept( "application/json")
                                .body(message),
                        splitterBaseUri + endpointConfig
                );

        response.should(haveStatusCode(HttpStatus.SC_OK));

        step("Запрос позитивного сценарий", () -> {
            var reqPos = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqPos),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == 'cdd20fe8-a0a7-483d-9a4f-b19f23f63ea8'"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[1].objectId == '0c879e13-4e9f-4600-9cf5-03eb3a10e7da'"))
                    ;
        });

        step("Запрос негативного сценария 1", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_1),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults[0].objectId == 'cdd20fe8-a0a7-483d-9a4f-b19f23f63ea8'"))
                    ;
        });

        step("Запрос негативного сценария 2", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_2),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '0c879e13-4e9f-4600-9cf5-03eb3a10e7da'"))
                    ;
        });

        step("Запрос негативного сценария 3", () -> {
            var reqNeg = restService.restClient()
                    .post(spec ->
                                    spec
                                            .contentType(ContentType.JSON)
                                            .header("Content-Type", "application/json")
                                            .accept( "application/json")
                                            .body(fileReqNeg_3),
                            splitterBaseUri+endpointReq)
                    .should(haveStatusCode(HttpStatus.SC_OK),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0"))
                    ;
        });

    }

}

