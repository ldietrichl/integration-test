package ru.sber.qa.splitter.Operators;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
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
@AnyConfigLoadMode
public class SplitterOperatorsLinkRulesTest {
    private static final String splitterBaseUri = RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
     String endpointConfig=Endpoints.Splitter.SPLITTER_CONFIG;
     String endpointReq=Endpoints.Splitter.SPLITTER_SPLIT;


    /** Общее хранилище для id между тестами. */
@AnyConfigLoadMode
    public static class SharedState {
    }


    @CriticalRegression
    @Test
    @DisplayName("1.Операторы правил привязки - equal")
    void SplitterOperatorsExpression1(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_pos.json")).toURI());
        Path pathReqNeg_1 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_neg_1.json")).toURI());
        Path pathReqNeg_2 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_neg_2.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg_1 = Files.readString(pathReqNeg_1);
        String fileReqNeg_2 = Files.readString(pathReqNeg_2);
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
                                    "splittingResults.find { it.objectId == 'baa6ba3b-dd96-4029-98c7-a9f15e86b793' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '9360fd69-668a-46de-b7b0-ad95b5f277b6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '12a3397b-5c14-4a40-b55b-9a02ba5bec14' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '0c74f592-9aef-4720-ba24-3a0656ca40ac' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == 'a753b85c-5359-4d52-ab38-1f52fe96f386' } != null"))
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
                                    "splittingResults.find { it.objectId == '9360fd69-668a-46de-b7b0-ad95b5f277b6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == 'a753b85c-5359-4d52-ab38-1f52fe96f386' } != null"))
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
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' }.objectResults.size() == 0"))
                    ;
        });

    }
    @Disabled
    @Test
    @DisplayName("2.Операторы правил привязки - not_equal")
    void SplitterOperatorsExpression2(RestService restService) throws URISyntaxException, IOException {

        Path pathConfig = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/config.json")).toURI());
        Path pathReqPos = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_pos.json")).toURI());
        Path pathReqNeg_1 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_neg_1.json")).toURI());
        Path pathReqNeg_2 = Path.of(Objects.requireNonNull(getClass().getClassLoader().getResource("splitter/splitter_operator_link_rules/1/req_neg_2.json")).toURI());
        String fileConfig = Files.readString(pathConfig);
        String fileReqPos = Files.readString(pathReqPos);
        String fileReqNeg_1 = Files.readString(pathReqNeg_1);
        String fileReqNeg_2 = Files.readString(pathReqNeg_2);
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
                                    "splittingResults.find { it.objectId == 'baa6ba3b-dd96-4029-98c7-a9f15e86b793' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '9360fd69-668a-46de-b7b0-ad95b5f277b6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '12a3397b-5c14-4a40-b55b-9a02ba5bec14' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '0c74f592-9aef-4720-ba24-3a0656ca40ac' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == 'a753b85c-5359-4d52-ab38-1f52fe96f386' } != null"))
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
                                    "splittingResults.find { it.objectId == '9360fd69-668a-46de-b7b0-ad95b5f277b6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' } != null"),
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == 'a753b85c-5359-4d52-ab38-1f52fe96f386' } != null"))
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
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                                    "splittingResults.find { it.objectId == '6cdf9748-a618-4e2d-aec1-c5a526eb18e6' }.objectResults.size() == 0"))
                    ;
        });

    }
}

