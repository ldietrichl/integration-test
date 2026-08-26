package config.services.rest.config_service;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;
import ru.sber.qa.services.rest.filters.RequestResponseConsoleLoggingFilter;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

//send msg to other uri
public class CustomAllure2RestConfigServiceServiceConfiguration extends DefaultRestServiceConfiguration {
    private final String token = TEST_CONFIG.configurationServiceToken();


    @Override
    public @NotNull RequestSpecification requestSpecification() {
        RestAssuredConfig restAssuredConfig = new RestAssuredConfig()
                .sslConfig(
                        new SSLConfig()
                                .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                                .keystoreType("PKCS12")
                                .relaxedHTTPSValidation());

        return super.requestSpecification()
                .config(restAssuredConfig)
                .baseUri(RestEndpointResolver.baseUri(RestServiceEndpoint.CONFIGURATION_SERVICE))
                .contentType(ContentType.JSON)
                .accept("*/*")
                .header("Authorization", "Bearer " + token)
                .filter(new AllureRestAssured())
                .filter(new RequestResponseConsoleLoggingFilter(Level.WARN));
    }
}
