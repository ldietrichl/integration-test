package util.ignite;

import config.services.core.TestEnvironment;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Environment-scoped Ignite connection settings, independent of a service or a test case. */
public final class IgniteConfiguration {
    private final String environment;
    private final String prefix;
    private final IgniteProfile profile;

    public IgniteConfiguration() {
        this(TestEnvironment.current(), new EnvironmentProperties("ignite.properties"));
    }

    IgniteConfiguration(String environment, EnvironmentProperties properties) {
        this.profile = new IgniteProfile(environment, properties);
        this.environment = profile.environment();
        this.prefix = "ignite." + this.environment + ".";
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
    public boolean usesLegacyProfile() { return profile.usesLegacyProfile(); }

    public String optional(String suffix) {
        return profile.optional(suffix);
    }

    public String required(String suffix) {
        String value = optional(suffix);
        if (value == null) throw new IllegalStateException("Configure " + settingName(suffix) + " for the selected test environment");
        return value;
    }

    private String settingName(String suffix) {
        return profile.settingName(suffix);
    }

    public Path runtimeDirectory() {
        return profile.runtimeDirectory();
    }

    public Path outputDirectory() {
        return profile.outputDirectory();
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
