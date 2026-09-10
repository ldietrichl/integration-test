package util.ignite;

import config.services.core.TestEnvironment;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Environment-scoped Ignite connection settings, independent of a service or a test case. */
public final class IgniteConfiguration {
    private static final Map<String, String> LEGACY_SUFFIXES = Map.ofEntries(
            Map.entry("addresses", "ignite.addresses"),
            Map.entry("username", "ignite.username"),
            Map.entry("password", "ignite.password"),
            Map.entry("ssl.enabled", "ignite.ssl.enabled"),
            Map.entry("ssl.key-store.path", "ignite.ssl.keyStore"),
            Map.entry("ssl.key-store.type", "ignite.ssl.keyStoreType"),
            Map.entry("ssl.key-store.password", "ignite.ssl.keyStorePassword"),
            Map.entry("ssl.trust-store.path", "ignite.ssl.trustStore"),
            Map.entry("ssl.trust-store.type", "ignite.ssl.trustStoreType"),
            Map.entry("ssl.trust-store.password", "ignite.ssl.trustStorePassword"),
            Map.entry("runtime.directory", "runtime.directory"));
    private static final List<String> KEYS = List.of("addresses", "username", "password", "ssl.enabled",
            "ssl.key-store.path", "ssl.key-store.type", "ssl.key-store.password",
            "ssl.trust-store.path", "ssl.trust-store.type", "ssl.trust-store.password",
            "runtime.directory", "output.directory", "operation-timeout-seconds");

    private final String environment;
    private final String prefix;
    private final EnvironmentProperties properties;
    private final boolean legacyProfile;

    public IgniteConfiguration() {
        this(TestEnvironment.current(), new EnvironmentProperties());
    }

    IgniteConfiguration(String environment, EnvironmentProperties properties) {
        this.environment = TestEnvironment.normalize(environment);
        this.prefix = "ignite." + this.environment + ".";
        this.properties = properties;
        // Migrate a connection profile as a whole: never fill missing canonical credentials from legacy.
        this.legacyProfile = KEYS.stream().noneMatch(key -> properties.raw(prefix + key) != null);
        String addresses = required("addresses");
        if (List.of(addresses.split(",", -1)).stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Empty Ignite address in " + settingName("addresses"));
        }
        if (!Set.of("true", "false").contains(required("ssl.enabled"))) {
            throw new IllegalArgumentException(settingName("ssl.enabled") + " must be true or false");
        }
        if ((optional("username") == null) != (optional("password") == null)) {
            throw new IllegalStateException("Configure both Ignite username and password, or neither, for " + this.environment);
        }
        operationTimeoutSeconds();
    }

    public String environment() { return environment; }
    public boolean usesLegacyProfile() { return legacyProfile; }

    public String optional(String suffix) {
        if (!KEYS.contains(suffix)) throw new IllegalArgumentException("Unknown Ignite setting: " + suffix);
        if (!legacyProfile) return properties.optional(prefix + suffix);
        String legacy = LEGACY_SUFFIXES.get(suffix);
        return legacy == null ? null : properties.optional("links.fixture." + environment + "." + legacy);
    }

    public String required(String suffix) {
        String value = optional(suffix);
        if (value == null) throw new IllegalStateException("Configure " + settingName(suffix) + " for the selected test environment");
        return value;
    }

    private String settingName(String suffix) {
        String legacy = LEGACY_SUFFIXES.get(suffix);
        return legacyProfile && legacy != null ? "links.fixture." + environment + "." + legacy : prefix + suffix;
    }

    public Path runtimeDirectory() {
        String directory = optional("runtime.directory");
        if (directory == null && legacyProfile) directory = "tools/data-operator-explab-2974";
        if (directory == null) throw new IllegalStateException("Configure " + prefix + "runtime.directory explicitly");
        return Path.of(directory).toAbsolutePath().normalize();
    }

    public Path outputDirectory() {
        String directory = optional("output.directory");
        return Path.of(directory == null ? "build/ignite/" + environment : directory).toAbsolutePath().normalize();
    }

    public long operationTimeoutSeconds() {
        String value = optional("operation-timeout-seconds");
        if (value == null) return 60;
        try {
            long seconds = Long.parseLong(value);
            if (seconds < 1 || seconds > 3600) throw new NumberFormatException();
            return seconds;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(prefix + "operation-timeout-seconds must be from 1 to 3600");
        }
    }

    /** Private child-process protocol: populated only after selecting exactly one environment. */
    public Map<String, String> helperEnvironment() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String suffix : List.of("username", "password", "ssl.enabled")) add(values, suffix);
        if ("true".equals(required("ssl.enabled"))) {
            for (String suffix : List.of("ssl.key-store.path", "ssl.key-store.type", "ssl.key-store.password",
                    "ssl.trust-store.path", "ssl.trust-store.type", "ssl.trust-store.password")) add(values, suffix);
        }
        return values;
    }

    private void add(Map<String, String> values, String suffix) {
        String value = optional(suffix);
        if (value != null) values.put("IGNITE_CLIENT_" + EnvironmentProperties.environmentName(suffix), value);
    }
}
