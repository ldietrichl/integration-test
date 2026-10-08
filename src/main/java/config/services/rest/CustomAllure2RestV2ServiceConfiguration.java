package config.services.rest;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;
import ru.sber.qa.services.rest.filters.RequestResponseConsoleLoggingFilter;
import steps.rest.splitter.SplitterKafkaConfigLoadFilter;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

public class CustomAllure2RestV2ServiceConfiguration extends DefaultRestServiceConfiguration {
    @Override
    public @NotNull RequestSpecification requestSpecification() {
        RestAssuredConfig restAssuredConfig = RestMtlsConfiguration.apply(new RestAssuredConfig(),
                RestEndpointResolver.baseUri(RestServiceEndpoint.EXPLAB_GATEWAY));
        boolean localEnvironment = "local".equals(RestEndpointResolver.currentEnvironment());

        RequestSpecification specification = super.requestSpecification()
                .config(restAssuredConfig)
                .filter(RestMtlsConfiguration.requestGuard())
                .baseUri(RestEndpointResolver.baseUri(RestServiceEndpoint.EXPLAB_GATEWAY))
                .contentType(ContentType.JSON)
                .accept("*/*")
                .filter(new SplitterKafkaConfigLoadFilter())
                .filter(new AllureRestAssured())
                .filter(new RequestResponseConsoleLoggingFilter(Level.WARN));
        if (!localEnvironment) {
            specification.header("Authorization", "Bearer " + TEST_CONFIG.explabGatewayToken());
        }
        return specification;
    }
}
