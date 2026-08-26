package constants;

/**
 * Runtime-configurable endpoint paths for mapper-compatible splitter tests.
 *
 * <p>Defaults keep the current mapper contract. Java 8/vintage-compatible
 * deployments can be checked with the same test classes by overriding
 * {@code -Dsplitter.endpoint.prefix} or a specific path property.</p>
 */
public final class SplitterEndpointPaths {
    private static final String DEFAULT_MAPPER_PREFIX = "/api/v1/splitter/mapper";

    private SplitterEndpointPaths() {
    }

    public static String config() {
        return path("splitter.endpoint.config", "/config");
    }

    public static String split() {
        return path("splitter.endpoint.split", "/split");
    }

    public static String precalculate() {
        return path("splitter.endpoint.precalculate", "/pre-calculate");
    }

    public static String version() {
        return path("splitter.endpoint.version", "/version");
    }

    private static String path(String propertyName, String suffix) {
        String explicitPath = System.getProperty(propertyName);
        if (explicitPath != null && !explicitPath.isBlank()) {
            return normalizePath(explicitPath);
        }
        return normalizePath(prefix() + suffix);
    }

    private static String prefix() {
        return trimTrailingSlash(System.getProperty("splitter.endpoint.prefix", DEFAULT_MAPPER_PREFIX));
    }

    private static String normalizePath(String rawPath) {
        String value = rawPath.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value;
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
