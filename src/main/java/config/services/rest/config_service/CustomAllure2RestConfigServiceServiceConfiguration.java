package config.services.rest.config_service;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import config.services.rest.RestMtlsConfiguration;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.RestAssuredConfig;
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
        RestAssuredConfig restAssuredConfig = RestMtlsConfiguration.apply(new RestAssuredConfig(),
                RestEndpointResolver.baseUri(RestServiceEndpoint.CONFIGURATION_SERVICE));

        return super.requestSpecification()
                .config(restAssuredConfig)
                .filter(RestMtlsConfiguration.requestGuard())
                .baseUri(RestEndpointResolver.baseUri(RestServiceEndpoint.CONFIGURATION_SERVICE))
                .contentType(ContentType.JSON)
                .accept("*/*")
                .header("Authorization", "Bearer " + token)
                .filter(new AllureRestAssured())
                .filter(new RequestResponseConsoleLoggingFilter(Level.WARN));
    }
}
