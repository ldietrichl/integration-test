package util.ignite;

import config.services.core.BuildArtifacts;
import config.services.core.TestEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Selects one complete profile without resolving connection values during helper preparation. */
final class IgniteProfile {
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

    IgniteProfile(String environment, EnvironmentProperties properties) {
        this.environment = TestEnvironment.normalize(environment);
        this.prefix = "ignite." + this.environment + ".";
        this.properties = properties;
        // Preserve whole-profile migration: canonical connection keys also select the canonical profile.
        this.legacyProfile = KEYS.stream().noneMatch(key -> properties.raw(prefix + key) != null);
    }

    String environment() { return environment; }
    boolean usesLegacyProfile() { return legacyProfile; }

    String optional(String suffix) {
        if (!KEYS.contains(suffix)) throw new IllegalArgumentException("Unknown Ignite setting: " + suffix);
        if (!legacyProfile) return properties.optional(prefix + suffix);
        String legacy = LEGACY_SUFFIXES.get(suffix);
        return legacy == null ? null : properties.optional("links.fixture." + environment + "." + legacy);
    }

    String settingName(String suffix) {
        String legacy = LEGACY_SUFFIXES.get(suffix);
        return legacyProfile && legacy != null ? "links.fixture." + environment + "." + legacy : prefix + suffix;
    }

    Path runtimeDirectory() {
        String directory = optional("runtime.directory");
        if (directory == null && legacyProfile) directory = "tools/data-operator-explab-2974";
        if (directory == null) throw new IllegalStateException("Configure " + prefix + "runtime.directory explicitly");
        return Path.of(directory).toAbsolutePath().normalize();
    }

    Path outputDirectory() {
        String directory = optional("output.directory");
        return BuildArtifacts.directory(directory == null ? "build/ignite/" + environment : directory);
    }
}
