package ru.sber.qa.examples.experiments.manual_qa;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;

import static ru.sber.qa.examples.experiments.manual_qa.TestDataHelper.experimentId;

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
