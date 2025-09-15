package ru.sber.qa.config.services;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;

public class CustomMetricsEnvironmentsConfiguration {

    public static RequestSpecification getTestRequestSpecification() {
        RestAssuredConfig restAssuredConfig = RestAssured.config()
                .decoderConfig(DecoderConfig.decoderConfig().defaultContentCharset(StandardCharsets.UTF_8))
                .sslConfig(SSLConfig
                        .sslConfig()
                        .keyStore("pathToTestKeyStore", "keystorePass")
                        .trustStore("pathToTestTruststore", "truststorePass"));
        return new RequestSpecBuilder()
                .setConfig(restAssuredConfig)
                .build();
    }
}
