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
    private static final String DEFAULT_REACTIONS_PREFIX = "/api/v1/splitter/reactions";

    private SplitterEndpointPaths() {
    }

    public static String config() {
        return mapperConfig();
    }

    public static String split() {
        return mapperSplit();
    }

    public static String precalculate() {
        return mapperPrecalculate();
    }

    public static String version() {
        return mapperVersion();
    }

    public static String mapperConfig() {
        return path("splitter.mapper.endpoint.config", "splitter.endpoint.config",
                mapperPrefix(), "/config");
    }

    public static String mapperSplit() {
        return path("splitter.mapper.endpoint.split", "splitter.endpoint.split",
                mapperPrefix(), "/split");
    }

    public static String mapperPrecalculate() {
        return path("splitter.mapper.endpoint.precalculate", "splitter.endpoint.precalculate",
                mapperPrefix(), "/pre-calculate");
    }

    public static String mapperVersion() {
        return path("splitter.mapper.endpoint.version", "splitter.endpoint.version",
                mapperPrefix(), "/version");
    }

    public static String reactionsConfig() {
        return path("splitter.reactions.endpoint.config", null,
                reactionsPrefix(), "/config");
    }

    public static String reactionsSplit() {
        return path("splitter.reactions.endpoint.split", null,
                reactionsPrefix(), "/split");
    }

    public static String reactionsPrecalculate() {
        return path("splitter.reactions.endpoint.precalculate", null,
                reactionsPrefix(), "/pre-calculate");
    }

    public static String reactionsVersion() {
        return path("splitter.reactions.endpoint.version", null,
                reactionsPrefix(), "/version");
    }

    private static String path(String propertyName, String legacyPropertyName, String prefix, String suffix) {
        String explicitPath = System.getProperty(propertyName);
        if ((explicitPath == null || explicitPath.isBlank()) && legacyPropertyName != null) {
            explicitPath = System.getProperty(legacyPropertyName);
        }
        if (explicitPath != null && !explicitPath.isBlank()) {
            return normalizePath(explicitPath);
        }
        return normalizePath(prefix + suffix);
    }

    private static String mapperPrefix() {
        String value = System.getProperty("splitter.mapper.endpoint.prefix",
                System.getProperty("splitter.endpoint.prefix", DEFAULT_MAPPER_PREFIX));
        return trimTrailingSlash(value);
    }

    private static String reactionsPrefix() {
        return trimTrailingSlash(System.getProperty("splitter.reactions.endpoint.prefix", DEFAULT_REACTIONS_PREFIX));
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
