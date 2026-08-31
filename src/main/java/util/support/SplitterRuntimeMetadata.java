package util.support;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.response.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import static constants.Endpoints.Splitter.SPLITTER_CONFIG;
import static constants.Endpoints.Splitter.SPLITTER_PRECALCULATE;
import static constants.Endpoints.Splitter.SPLITTER_REACTIONS_CONFIG;
import static constants.Endpoints.Splitter.SPLITTER_REACTIONS_PRECALCULATE;
import static constants.Endpoints.Splitter.SPLITTER_REACTIONS_SPLIT;
import static constants.Endpoints.Splitter.SPLITTER_REACTIONS_VERSION;
import static constants.Endpoints.Splitter.SPLITTER_SPLIT;
import static constants.Endpoints.Splitter.SPLITTER_VERSION;

public final class SplitterRuntimeMetadata {

    private static final Map<String, String> CACHED_VERSIONS = new ConcurrentHashMap<>();

    private SplitterRuntimeMetadata() {
    }

    public static String environment() {
        return RestEndpointResolver.currentEnvironment();
    }

    public static String splittingPoint() {
        return isReactions() ? "REACTIONS" : "MAPPER";
    }

    public static String splitterBaseUri() {
        return RestEndpointResolver.baseUri(restServiceEndpoint());
    }

    public static String versionUrl() {
        return splitterBaseUri() + versionPath();
    }

    public static String configUrl() {
        return splitterBaseUri() + configPath();
    }

    public static String splitUrl() {
        return splitterBaseUri() + splitPath();
    }

    public static String precalculateUrl() {
        return splitterBaseUri() + precalculatePath();
    }

    public static String summary() {
        return "splitter.environment=" + environment() + System.lineSeparator()
                + "splitter.splittingPoint=" + splittingPoint() + System.lineSeparator()
                + "splitter.url=" + splitterBaseUri() + System.lineSeparator()
                + "splitter.version=" + version() + System.lineSeparator()
                + "splitter.versionUrl=" + versionUrl() + System.lineSeparator()
                + "splitter.configUrl=" + configUrl() + System.lineSeparator()
                + "splitter.splitUrl=" + splitUrl() + System.lineSeparator()
                + "splitter.precalculateUrl=" + precalculateUrl();
    }

    public static String version() {
        return CACHED_VERSIONS.computeIfAbsent(versionUrl(), ignored -> requestVersion());
    }

    private static String requestVersion() {
        try {
            RestAssuredConfig restAssuredConfig = new RestAssuredConfig();
            if (!"local".equals(environment())) {
                restAssuredConfig = restAssuredConfig.sslConfig(
                        new SSLConfig()
                                .keyStore("src/test/resources/keystore.p12", TEST_CONFIG.keystorePass())
                                .keystoreType("PKCS12")
                                .relaxedHTTPSValidation());
            }

            Response response = RestAssured.given()
                    .config(restAssuredConfig)
                    .baseUri(splitterBaseUri())
                    .accept("*/*")
                    .when()
                    .get(versionPath());

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

    private static RestServiceEndpoint restServiceEndpoint() {
        return isReactions()
                ? RestServiceEndpoint.SPLITTER_REACTIONS
                : RestServiceEndpoint.SPLITTER_MAPPER;
    }

    private static String versionPath() {
        return isReactions() ? SPLITTER_REACTIONS_VERSION : SPLITTER_VERSION;
    }

    private static String configPath() {
        return isReactions() ? SPLITTER_REACTIONS_CONFIG : SPLITTER_CONFIG;
    }

    private static String splitPath() {
        return isReactions() ? SPLITTER_REACTIONS_SPLIT : SPLITTER_SPLIT;
    }

    private static String precalculatePath() {
        return isReactions() ? SPLITTER_REACTIONS_PRECALCULATE : SPLITTER_PRECALCULATE;
    }

    private static boolean isReactions() {
        return "REACTIONS".equalsIgnoreCase(System.getProperty("splitter.local.splitting-point", "MAPPER").trim());
    }
}
