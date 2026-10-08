package config.services.rest;

import config.services.core.StatusChange2972Settings;
import io.restassured.config.*;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;

/** Internal user-service endpoint; never inferred from the public gateway URL. */
public final class LaunchPlan2972UsersConfiguration extends DefaultRestServiceConfiguration {
    @Override public RequestSpecification requestSpecification() {
        var s = new StatusChange2972Settings();
        var config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeader("Authorization"))
                .headerConfig(HeaderConfig.headerConfig()
                        .overwriteHeadersWithName("Authorization", "Content-Type", "Accept"))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10000).setParam("http.socket.timeout", 30000));
        if (RestMtlsConfiguration.enabled(s.env)) {
            config = RestMtlsConfiguration.apply(config, s.required("launch-plan.users.base-uri"));
        } else if (!s.env.equals("local")) config = config.sslConfig(new SSLConfig()
                .keyStore(s.optional("keystore", "src/test/resources/keystore.p12"), s.secret("keystore.pass"))
                .keystoreType("PKCS12"));
        var spec = super.requestSpecification().baseUri(s.required("launch-plan.users.base-uri"))
                .config(config).contentType(ContentType.JSON).accept(ContentType.JSON);
        String configured = s.optional("launch-plan.users.token", null);
        if (!s.env.equals("local") || s.localAuth() || configured != null) {
            String token = configured == null ? s.token("primary", false)
                    : StatusChange2972Settings.resolveSecret(configured, "launch-plan.users.token");
            spec.header("Authorization", "Bearer " + token);
        }
        spec.filter(RestMtlsConfiguration.requestGuard());
        return spec;
    }
}
