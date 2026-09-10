package util.dataoperator;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import util.ignite.EnvironmentProperties;
import util.ignite.IgniteConfiguration;

/** Data-operator fixture policy; the connection itself belongs to the shared Ignite profile. */
public final class LinksFixtureConfiguration {
    public final String environment = RestEndpointResolver.currentEnvironment();
    public final String serviceUri = RestEndpointResolver.baseUri(RestServiceEndpoint.DATA_OPERATOR);
    private final EnvironmentProperties properties = new EnvironmentProperties();
    private final IgniteConfiguration ignite;

    public LinksFixtureConfiguration() {
        if (!"true".equals(fixtureSetting("enabled"))) {
            throw new IllegalStateException("Enable data-operator.fixture." + environment + ".enabled for isolated fixture writes");
        }
        ignite = new IgniteConfiguration();
    }

    public IgniteConfiguration ignite() { return ignite; }
    public Path runtimeDirectory() { return ignite.runtimeDirectory(); }

    public Path output() {
        String directory = fixtureSetting("output.directory");
        // Existing lease manifests and recovery paths remain valid after the connection rename.
        return Path.of(directory == null ? "build/explab-2974-fixtures/" + environment : directory)
                .toAbsolutePath().normalize();
    }

    private String fixtureSetting(String suffix) {
        String canonical = "data-operator.fixture." + environment + "." + suffix;
        return properties.raw(canonical) != null ? properties.optional(canonical)
                : properties.optional("links.fixture." + environment + "." + suffix);
    }

    /** Bridge for immutable, previously prepared LinksCacheTool bundles. */
    Map<String, String> legacyHelperEnvironment() {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> selected = ignite.helperEnvironment();
        Map<String, String> names = Map.ofEntries(
                Map.entry("USERNAME", "USERNAME"), Map.entry("PASSWORD", "PASSWORD"),
                Map.entry("SSL_ENABLED", "SSL_ENABLED"),
                Map.entry("SSL_KEY_STORE_PATH", "SSL_KEYSTORE"),
                Map.entry("SSL_KEY_STORE_TYPE", "SSL_KEYSTORETYPE"),
                Map.entry("SSL_KEY_STORE_PASSWORD", "SSL_KEYSTOREPASSWORD"),
                Map.entry("SSL_TRUST_STORE_PATH", "SSL_TRUSTSTORE"),
                Map.entry("SSL_TRUST_STORE_TYPE", "SSL_TRUSTSTORETYPE"),
                Map.entry("SSL_TRUST_STORE_PASSWORD", "SSL_TRUSTSTOREPASSWORD"));
        names.forEach((source, target) -> {
            String value = selected.get("IGNITE_CLIENT_" + source);
            if (value != null) result.put("LINKS_IGNITE_" + target, value);
        });
        return result;
    }
}
