package config.services.core;
import java.util.Properties;

/** Shared DEV/IFT/LT settings. Explicit new keys win; old keys only bridge an existing installation. */
public final class StandSettings {
    /** Resource/readiness defaults only. Explicit configuration, including blank values, wins. */
    public static java.util.Map<String, String> workloadDefaults(String environment, String workload,
                                                               String deployment, String configMap) {
        String prefix = "stand." + environment + ".workloads." + workload + ".";
        return java.util.Map.of(prefix + "deployment", deployment, prefix + "configmap", configMap,
                prefix + "readiness.timeout.seconds", "120", prefix + "readiness.poll.millis", "2000");
    }

    public static synchronized void installMissing(java.util.Map<String, String> defaults,
                                                   java.util.function.Function<String, String> configured) {
        // Keep defaults available until root-store rollback; never grant mutation permissions here.
        defaults.forEach((key, value) -> {
            if (configured.apply(key) == null && System.getProperty(key) == null) System.setProperty(key, value);
        });
    }

    private final Properties defaults = TestConfigurationFiles.load("stand.properties");
    private final Properties project = TestConfigurationFiles.load("test.properties");
    public final String environment = TestEnvironment.current();

    public String optional(String suffix, String legacyScoped, String legacyCommon, String fallback) {
        String key = "stand." + environment + "." + suffix;
        String value = System.getProperty(key);
        if (value == null) value = SecureLocalConfigScope.SECURE_LOCAL_CONFIG.getProperty(key);
        if (value == null) value = project.getProperty(key);
        if (value == null && legacyScoped != null) value = project.getProperty(legacyScoped);
        if (value == null && legacyCommon != null) value = project.getProperty(legacyCommon);
        if (value == null) value = defaults.getProperty(key, fallback);
        return value == null ? null : SecurePropertyResolver.resolve(value).trim();
    }
    public String optional(String suffix, String fallback) { return optional(suffix, null, null, fallback); }
    public String required(String suffix) {
        String value = optional(suffix, "");
        if (value.isBlank() || value.contains("${") || value.contains("<SET_ME_"))
            throw new IllegalStateException("Configure stand." + environment + "." + suffix);
        return value;
    }
    public boolean flag(String suffix) {
        String value = optional(suffix, "false");
        if (!java.util.Set.of("true", "false").contains(value))
            throw new IllegalArgumentException("Expected true/false: stand." + environment + "." + suffix);
        return Boolean.parseBoolean(value);
    }
    public int integer(String suffix, int fallback, int min, int max) {
        int value = Integer.parseInt(optional(suffix, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalArgumentException("Out-of-range stand setting: " + suffix);
        return value;
    }
}
