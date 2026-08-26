package util.support;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static constants.Endpoints.Splitter.SPLITTER_CONFIG;
import static constants.Endpoints.Splitter.SPLITTER_PRECALCULATE;
import static constants.Endpoints.Splitter.SPLITTER_SPLIT;
import static constants.Endpoints.Splitter.SPLITTER_VERSION;

public final class SplitterRuntimeMetadata {

    private static volatile String cachedVersion;

    private SplitterRuntimeMetadata() {
    }

    public static String environment() {
        return RestEndpointResolver.currentEnvironment();
    }

    public static String splitterBaseUri() {
        return RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER);
    }

    public static String versionUrl() {
        return splitterBaseUri() + SPLITTER_VERSION;
    }

    public static String configUrl() {
        return splitterBaseUri() + SPLITTER_CONFIG;
    }

    public static String splitUrl() {
        return splitterBaseUri() + SPLITTER_SPLIT;
    }

    public static String precalculateUrl() {
        return splitterBaseUri() + SPLITTER_PRECALCULATE;
    }

    public static String version() {
        String current = cachedVersion;
        if (current != null && !current.isBlank()) {
            return current;
        }
        synchronized (SplitterRuntimeMetadata.class) {
            if (cachedVersion == null || cachedVersion.isBlank()) {
                cachedVersion = requestVersion();
            }
            return cachedVersion;
        }
    }

    private static String requestVersion() {
        try {
            RestAssuredConfig restAssuredConfig = new RestAssuredConfig()
                    .sslConfig(new SSLConfig()
                            .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                            .keystoreType("PKCS12")
                            .relaxedHTTPSValidation());

            Response response = RestAssured.given()
                    .config(restAssuredConfig)
                    .baseUri(splitterBaseUri())
                    .accept("*/*")
                    .when()
                    .get(SPLITTER_VERSION);

            if (response.statusCode() != 200) {
                return "unavailable(status=" + response.statusCode() + ")";
            }

            String body = response.getBody().asString();
            if (body == null) {
                return "unknown";
            }
            body = body.trim();
            if (body.startsWith("\"") && body.endsWith("\"") && body.length() >= 2) {
                body = body.substring(1, body.length() - 1);
            }
            return body.isBlank() ? "unknown" : body;
        } catch (Exception e) {
            return "unavailable(" + e.getClass().getSimpleName() + ")";
        }
    }
}
