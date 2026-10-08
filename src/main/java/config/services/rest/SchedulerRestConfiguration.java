package config.services.rest;

import config.services.core.SchedulerSettings;
import io.restassured.config.*;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;

public class SchedulerRestConfiguration extends DefaultRestServiceConfiguration {
    protected String prefix() { return ""; }

    public static RestAssuredConfig requestConfig(
            SchedulerSettings settings, String prefix, String baseUri, int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 600)
            throw new IllegalArgumentException("Scheduler REST timeout must be between 1 and 600 seconds");
        RestAssuredConfig config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeader("Authorization", "Cookie", "Set-Cookie"))
                .headerConfig(HeaderConfig.headerConfig()
                        .overwriteHeadersWithName("Authorization", "Content-Type", "Accept"))
                .httpClient(HttpClientConfig.httpClientConfig().setParam("http.connection.timeout", 10000)
                        .setParam("http.socket.timeout", timeoutSeconds * 1000));
        if (RestMtlsConfiguration.enabled(settings.environment)) {
            RestMtlsConfiguration.requireApprovedTarget(baseUri);
            return RestMtlsConfiguration.apply(config);
        }
        if ("https".equalsIgnoreCase(java.net.URI.create(baseUri).getScheme()))
            return config.sslConfig(SchedulerTlsConfiguration.configure(settings, prefix));
        return config;
    }

    @Override public RequestSpecification requestSpecification() {
        SchedulerSettings s = new SchedulerSettings();
        String p = prefix();
        String baseUri = p.isEmpty() ? s.baseUri() : SchedulerSettings.uri(s.required(p + "base-uri"));
        RestAssuredConfig config = requestConfig(s, p, baseUri, s.timeoutSeconds());
        RequestSpecification spec = super.requestSpecification().config(config)
                .baseUri(baseUri)
                .contentType(ContentType.JSON).accept(ContentType.JSON);
        spec.filter(RestMtlsConfiguration.requestGuard());
        if (!s.optional(p + "token", "").isBlank()) spec.header("Authorization", "Bearer " + s.required(p + "token"));
        return spec;
    }
}
