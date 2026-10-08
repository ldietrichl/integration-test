package config.services.container;

import config.services.core.TestConfigurationFiles;
import ru.sber.qa.services.configuration.scope.ConfigScope;

import java.util.Properties;

/** Only infrastructure settings enter the project's secure configuration service. */
public final class KubernetesTunnelConfigScope implements ConfigScope {
    @Override
    public Properties getProperties() {
        Properties result = new Properties();
        merge(result, TestConfigurationFiles.load("kubernetes-tunnels.properties"));
        merge(result, TestConfigurationFiles.load("test.properties"));
        config.services.core.StandSettings stand = new config.services.core.StandSettings();
        for (String key : java.util.List.of("container-service.ready", "context", "api-server", "namespace", "kubeconfig",
                "oc.executable", "transport", "local-port", "startup-timeout.seconds", "request-timeout.ms",
                "logs.enabled", "logs.tail.lines", "logs.max.bytes", "logs.timeout.seconds",
                "logs.clock-skew.seconds", "tls.diagnostics.enabled",
                "tls.ca-resource", "tls.ca-api-server", "tls.ca-sha256",
                "auth.mode", "auth.expected-user", "auth.require-token",
                "service-account", "service-account.secret",
                "permissions.required", "permissions.optional-missing")) {
            String value = stand.optional("kubernetes." + key, "kubernetes." + stand.environment + "." + key,
                    "kubernetes." + key, null);
            if (value != null) result.setProperty("kubernetes." + stand.environment + "." + key, value);
        }
        String enabled = stand.optional("kubernetes.enabled", "scheduler." + stand.environment + ".infrastructure.enabled",
                "scheduler.infrastructure.enabled", null);
        if (enabled != null) result.setProperty("scheduler.infrastructure.enabled", enabled);
        String optIn = System.getProperty("scheduler.infrastructure.enabled");
        if (optIn != null) result.setProperty("scheduler.infrastructure.enabled", optIn);
        return result;
    }

    private static void merge(Properties target, Properties source) {
        source.stringPropertyNames().stream()
                .filter(key -> key.startsWith("kubernetes.") || key.startsWith("scheduler.infrastructure."))
                .forEach(key -> target.setProperty(key, source.getProperty(key).trim()));
    }

    public static boolean enabled() {
        String value = new KubernetesTunnelConfigScope().getProperties()
                .getProperty("scheduler.infrastructure.enabled", "false");
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("scheduler.infrastructure.enabled must be true or false");
        return Boolean.parseBoolean(value);
    }
}
