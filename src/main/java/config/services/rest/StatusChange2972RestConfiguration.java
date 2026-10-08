package config.services.rest;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import config.services.core.StatusChange2972Settings;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.jetbrains.annotations.NotNull;
import ru.sber.qa.services.rest.DefaultRestServiceConfiguration;

/** Platform V AT creates and owns the client; domain steps attach credential-free evidence. */
public class StatusChange2972RestConfiguration extends DefaultRestServiceConfiguration {
    protected RestServiceEndpoint endpoint() { return RestServiceEndpoint.EXPERIMENTS; }
    protected boolean configuration() { return false; }

    @Override public @NotNull RequestSpecification requestSpecification() {
        StatusChange2972Settings settings = new StatusChange2972Settings();
        RestAssuredConfig config = RestAssuredConfig.config()
                .logConfig(io.restassured.config.LogConfig.logConfig().blacklistHeader("Authorization"))
                .headerConfig(io.restassured.config.HeaderConfig.headerConfig()
                        .overwriteHeadersWithName("Authorization", "Content-Type", "Accept"))
                .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 10000).setParam("http.socket.timeout", settings.timeout() * 1000));
        if (RestMtlsConfiguration.enabled(settings.env)) {
            config = RestMtlsConfiguration.apply(config, RestEndpointResolver.baseUri(endpoint()));
        } else if (!settings.env.equals("local")) {
            config = config.sslConfig(new SSLConfig()
                    .keyStore(settings.optional("keystore", "src/test/resources/keystore.p12"), settings.secret("keystore.pass"))
                    .keystoreType("PKCS12").relaxedHTTPSValidation());
        }
        RequestSpecification spec = super.requestSpecification().config(config)
                .baseUri(RestEndpointResolver.baseUri(endpoint())).contentType(ContentType.JSON).accept(ContentType.JSON);
        if (!settings.env.equals("local") || settings.localAuth()) spec.header("Authorization", "Bearer " + settings.token("primary", configuration()));
        spec.filter(RestMtlsConfiguration.requestGuard());
        return spec;
    }

    public static final class Configurations extends StatusChange2972RestConfiguration {
        @Override protected RestServiceEndpoint endpoint() { return RestServiceEndpoint.CONFIGURATION_SERVICE; }
        @Override protected boolean configuration() { return true; }
    }
}
