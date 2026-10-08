package util.support;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import config.services.rest.RestMtlsConfiguration;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return summary(splittingPoint());
    }

    public static String summary(String point) {
        return summary(point, url -> CACHED_VERSIONS.computeIfAbsent(url, SplitterRuntimeMetadata::requestVersion));
    }

    static String summary(String point, java.util.function.Function<String, String> versions) {
        boolean reactions = switch (point) {
            case "MAPPER" -> false;
            case "REACTIONS" -> true;
            default -> throw new IllegalArgumentException("Unknown splitting point: " + point);
        };
        String base = RestEndpointResolver.baseUri(reactions
                ? RestServiceEndpoint.SPLITTER_REACTIONS : RestServiceEndpoint.SPLITTER_MAPPER);
        String versionUrl = base + (reactions ? SPLITTER_REACTIONS_VERSION : SPLITTER_VERSION);
        return "splitter.environment=" + environment() + System.lineSeparator()
                + "splitter.splittingPoint=" + point + System.lineSeparator()
                + "splitter.url=" + base + System.lineSeparator()
                + "splitter.version=" + versions.apply(versionUrl) + System.lineSeparator()
                + "splitter.versionUrl=" + versionUrl + System.lineSeparator()
                + "splitter.configUrl=" + base + (reactions ? SPLITTER_REACTIONS_CONFIG : SPLITTER_CONFIG) + System.lineSeparator()
                + "splitter.splitUrl=" + base + (reactions ? SPLITTER_REACTIONS_SPLIT : SPLITTER_SPLIT) + System.lineSeparator()
                + "splitter.precalculateUrl=" + base + (reactions ? SPLITTER_REACTIONS_PRECALCULATE : SPLITTER_PRECALCULATE);
    }

    public static String version() {
        return CACHED_VERSIONS.computeIfAbsent(versionUrl(), SplitterRuntimeMetadata::requestVersion);
    }

    private static String requestVersion(String url) {
        try {
            RestAssuredConfig restAssuredConfig = RestMtlsConfiguration.apply(new RestAssuredConfig());

            Response response = RestAssured.given()
                    .config(restAssuredConfig)
                    .accept("*/*")
                    .when()
                    .get(url);

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
