package config.services.container;

import config.services.core.TestConfigurationFiles;
import config.services.core.TestEnvironment;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.configuration.ConfigurationService;
import ru.sber.qa.services.configuration.scope.ConfigScope;

import java.util.Properties;
import java.util.Set;

/** Explicit, stand-scoped diagnostic overlay. Never changes the regression profile. */
public final class ContainerDiagnosticsSettings {
    public static final String RESOURCE = "container-diagnostics.properties";
    public final String environment = TestEnvironment.current();
    private final Properties properties;
    private final String prefix;

    private ContainerDiagnosticsSettings(Properties properties) {
        if (!Set.of("dev", "ift", "lt").contains(environment))
            throw new IllegalStateException("DIAGNOSTIC_ENV: select dev/ift/lt in test.properties");
        this.properties = properties;
        prefix = "stand." + environment + ".container-diagnostics.";
    }

    public static ContainerDiagnosticsSettings local() {
        return new ContainerDiagnosticsSettings(new DiagnosticScope().getProperties());
    }

    public static ContainerDiagnosticsSettings from(Environment environment) {
        return new ContainerDiagnosticsSettings(environment.getService(ConfigurationService.class)
                .getProperties(new DiagnosticScope()));
    }

    public static final class DiagnosticScope implements ConfigScope {
        @Override public Properties getProperties() {
            Properties result = new KubernetesTunnelConfigScope().getProperties();
            // Diagnostic switches only. Connection identity comes exclusively from the shared stand.
            for (Properties source : java.util.List.of(TestConfigurationFiles.load(RESOURCE),
                    TestConfigurationFiles.load("test.properties"))) {
                source.stringPropertyNames().stream()
                        .filter(key -> key.matches("stand\\.(dev|ift|lt)\\.container-diagnostics\\..+"))
                        .forEach(key -> result.setProperty(key, source.getProperty(key)));
            }
            return result;
        }
    }

    public String value(String key, String fallback) {
        return properties.getProperty(prefix + key, fallback).trim();
    }

    public String required(String key) {
        String value = value(key, "");
        if (value.isBlank() || value.contains("<SET_ME_") || value.contains("$" + "{"))
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: missing " + prefix + key);
        return value;
    }

    public boolean flag(String key, boolean fallback) {
        String value = value(key, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: expected boolean " + prefix + key);
        return Boolean.parseBoolean(value);
    }

    public int number(String key, int fallback, int min, int max) {
        int value = Integer.parseInt(value(key, Integer.toString(fallback)));
        if (value < min || value > max)
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: out of range " + prefix + key);
        return value;
    }

    public String phase() {
        String value = value("phase", "before-browser");
        if (!Set.of("before-browser", "after-browser", "after-cli-login").contains(value))
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: invalid comparison phase");
        return value;
    }

    public String resource(String key) {
        String name = required(key);
        if (!name.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: exact DNS resource name required");
        return name;
    }

    public KubernetesTunnelSettings connection() {
        Properties mapped = new Properties();
        properties.stringPropertyNames().stream().filter(key -> key.startsWith("kubernetes."))
                .forEach(key -> mapped.setProperty(key, properties.getProperty(key)));
        mapped.setProperty("kubernetes." + environment + ".service", resource("service"));
        return KubernetesTunnelSettings.fromProperties(mapped);
    }

    public String requiredMutationApproval() {
        return environment + "/" + connection().namespace + "/" + resource("deployment");
    }
}
