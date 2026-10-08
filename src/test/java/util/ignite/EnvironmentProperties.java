package util.ignite;

import config.services.core.SecurePropertyResolver;
import config.services.core.TestConfigurationFiles;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.configuration.ConfigurationService;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

/** Reads non-secret settings from the named project file; placeholders resolve through the secure overlay. */
public final class EnvironmentProperties {
    private final Properties resource;
    private final Function<String, String> resolver;
    private final boolean allowFixtureSwitches;

    public EnvironmentProperties() {
        this("test.properties");
    }

    public EnvironmentProperties(String resource) {
        this(TestConfigurationFiles.load(resource), EnvironmentProperties::resolveSelectedValue, resource.equals("test.properties"));
    }

    EnvironmentProperties(Properties resource, Function<String, String> resolver) {
        this(resource, resolver, false);
    }

    EnvironmentProperties(Properties resource, Function<String, String> resolver, boolean allowFixtureSwitches) {
        this.resource = resource;
        this.resolver = resolver;
        this.allowFixtureSwitches = allowFixtureSwitches;
    }

    public String raw(String key) {
        if (allowFixtureSwitches && key.matches("(?:data-operator\\.fixture|links\\.fixture)\\.(?:dev|ift|ift-dm|lt|local)\\.(?:enabled|output\\.directory|run-id)")) {
            String generated = System.getProperty(key);
            if (generated != null) return generated;
        }
        return resource.getProperty(key);
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

    private static String resolveSelectedValue(String value) {
        if (!Environment.existForCurrentThread()) return SecurePropertyResolver.resolve(value);
        Properties selected = new Properties();
        selected.setProperty("value", value);
        return Environment.getForCurrentThread().getService(ConfigurationService.class)
                .getProperties(() -> selected).getProperty("value");
    }
}
