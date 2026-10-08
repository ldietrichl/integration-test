package config.services.rest;

import config.services.core.SchedulerSettings;
import io.restassured.config.HeaderConfig;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;

/** Plain HTTP only inside the owned loopback tunnel. Cluster TLS remains verified by oc/Fabric8. */
public final class SchedulerTunnelRestConfiguration extends DefaultRestServiceConfiguration {
    public static RestAssuredConfig requestConfig(int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600)
            throw new IllegalArgumentException("Scheduler tunnel timeout must be between 1 and 600 seconds");
        return RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeader("Authorization", "Cookie", "Set-Cookie"))
                .headerConfig(HeaderConfig.headerConfig()
                        .overwriteHeadersWithName("Authorization", "Content-Type", "Accept"))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10000)
                        .setParam("http.socket.timeout", timeoutSeconds * 1000));
    }

    @Override
    public RequestSpecification requestSpecification() {
        SchedulerSettings settings = new SchedulerSettings();
        RestAssuredConfig config = requestConfig(settings.timeoutSeconds());
        // A request is legal only after SchedulerSteps replaces this non-service origin.
        RequestSpecification specification = super.requestSpecification().config(config)
                .baseUri("http://127.0.0.1").port(1).basePath("")
                .contentType(ContentType.JSON).accept("application/json");
        // Application token only, if required. Never use the OpenShift OAuth token here.
        if (!settings.optional("token", "").isBlank())
            specification.header("Authorization", "Bearer " + settings.required("token"));
        return specification;
    }
}
