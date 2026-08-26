package ru.sber.qa.experiments.manual_qa;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.io.IOException;

import static ru.sber.qa.experiments.manual_qa.TestDataHelper.experimentId;

public class ExperimentFindService {

    public static long createExperiment(RestAssuredConfig config) throws IOException {
        if (TestDataHelper.experimentId == null) {
            throw new IllegalStateException("experimentId не установлен");
        }

        RequestSpecification requestSpec = RestAssured.given()
                .config(config)
                .contentType("application/json")
                .header("Content-Type", "application/json")
                .accept("*/*");

        Response response = requestSpec.get(RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS) + "/api/v1/experiments").then().extract().response();
        return experimentId;
    }
}
