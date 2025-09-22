package ru.sber.qa.examples.experiments;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import ru.sber.qa.feeders.ExperimentsFeeder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.examples.experiments.TestDataHelper.experimentId;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

public class ExperimentFindService {

    public static long createExperiment(String url, RestAssuredConfig config) throws IOException {
        if (TestDataHelper.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        RequestSpecification requestSpec = RestAssured.given()
                .config(config)
                .contentType("application/json")
                .header("Content-Type", "application/json")
                .accept("*/*");

        Response response = requestSpec.get(url + "/api/v1/experiments").then().extract().response();
        return experimentId;
    }
}
