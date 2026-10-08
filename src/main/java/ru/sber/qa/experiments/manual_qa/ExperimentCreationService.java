package ru.sber.qa.experiments.manual_qa;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import feeders.ExperimentsFeeder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExperimentCreationService {

    public static long createExperiment(RestAssuredConfig config) throws IOException {
        String filePath = "src/test/resources/experiments/create_exp.json";
        String jsonBody = new String(Files.readAllBytes(Paths.get(filePath)));

        String myId = ExperimentsFeeder.generateAqaMalilId();
        jsonBody = jsonBody.replace("${myId}", myId);

        String salt = ExperimentsFeeder.generateSalt();
        jsonBody = jsonBody.replace("${salt}", salt);

        long startDt = ExperimentsFeeder.startDt;
        jsonBody = jsonBody.replace("${startDt}", String.valueOf(startDt));

        long endDt = ExperimentsFeeder.endDt;
        jsonBody = jsonBody.replace("${endDt}", String.valueOf(endDt));

        RequestSpecification requestSpec = RestAssured.given()
                .config(config)
                .contentType("application/json")
                .header("Content-Type", "application/json")
                .accept("*/*")
                .body(jsonBody);

        Response response = requestSpec.post(RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS) + "/api/v1/experiments").then().extract().response();

        long experimentId = response.jsonPath().getLong("id");
        System.out.println("НОВЫЙ ИД: " + experimentId);
        return experimentId;
    }
}
