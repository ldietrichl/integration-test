package util.ignite;

import config.services.core.SecurePropertyResolver;
import config.services.core.TestConfigurationFiles;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;
import static config.services.core.SecureLocalConfigScope.SECURE_LOCAL_CONFIG;

/** Reads one fully qualified key. A present blank value masks lower-priority sources. */
public final class EnvironmentProperties {
    private final Function<String, String> system;
    private final Function<String, String> environment;
    private final Function<String, String> secure;
    private final Properties resource;
    private final Function<String, String> resolver;

    public EnvironmentProperties() {
        this(System::getProperty, System::getenv, SECURE_LOCAL_CONFIG::getProperty,
                loadResource(), SecurePropertyResolver::resolve);
    }

    EnvironmentProperties(Function<String, String> system, Function<String, String> environment,
                          Function<String, String> secure, Properties resource,
                          Function<String, String> resolver) {
        this.system = system;
        this.environment = environment;
        this.secure = secure;
        this.resource = resource;
        this.resolver = resolver;
    }

    public String raw(String key) {
        String value = system.apply(key);
        if (value == null) value = environment.apply(environmentName(key));
        if (value == null) value = secure.apply(key);
        if (value == null) value = resource.getProperty(key);
        return value;
    }

    public String optional(String key) {
        String value = raw(key);
        if (value == null || value.isBlank()) return null;
        String resolved = resolver.apply(value.trim());
        if (resolved == null || resolved.isBlank()) return null;
        if (resolved.startsWith("<") || resolved.startsWith("SET_ME") || resolved.contains("${")) {
            throw new IllegalStateException("Unresolved test setting: " + key);
        }
        return resolved.trim();
    }

    public static String environmentName(String key) {
        return key.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }

    private static Properties loadResource() {
        return TestConfigurationFiles.load("test.properties");
    }
}
