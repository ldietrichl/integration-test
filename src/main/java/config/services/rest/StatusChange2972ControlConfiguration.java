package config.services.rest;

import config.services.core.StatusChange2972Settings;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.HeaderConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;

public final class StatusChange2972ControlConfiguration extends DefaultRestServiceConfiguration {
    @Override public RequestSpecification requestSpecification() {
        var settings = new StatusChange2972Settings();
        var config = RestAssuredConfig.config()
                .logConfig(LogConfig.logConfig().blacklistHeader("Authorization"))
                .headerConfig(HeaderConfig.headerConfig()
                        .overwriteHeadersWithName("Authorization", "Content-Type", "Accept"))
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 10000).setParam("http.socket.timeout", 120000));
        if (RestMtlsConfiguration.enabled(settings.env)) {
            config = RestMtlsConfiguration.apply(config, settings.required("control.base-uri"));
        } else if (!settings.env.equals("local")) {
            String mtls = settings.optional("control.mtls.enabled", "true");
            if (!mtls.equals("true") && !mtls.equals("false"))
                throw new IllegalStateException("control.mtls.enabled must be true or false");
            if (mtls.equals("true")) {
                String password = settings.optional("control.keystore.pass", null);
                password = password == null ? settings.secret("keystore.pass")
                        : StatusChange2972Settings.resolveSecret(password, "control.keystore.pass");
                config = config.sslConfig(new SSLConfig().keyStore(
                        settings.optional("control.keystore", settings.optional("keystore", "src/test/resources/keystore.p12")),
                        password).keystoreType("PKCS12"));
            }
        }
        var spec = super.requestSpecification().baseUri(settings.required("control.base-uri"))
                .contentType(ContentType.JSON).accept(ContentType.JSON).config(config);
        if (!settings.env.equals("local")) spec.header("Authorization", "Bearer "
                + StatusChange2972Settings.resolveSecret(settings.required("control.token"), "control.token"));
        spec.filter(RestMtlsConfiguration.requestGuard());
        return spec;
    }
}
