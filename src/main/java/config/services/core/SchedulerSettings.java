package config.services.core;

import java.net.URI;
import java.util.Properties;

/** Scheduler configuration uses the project environment and secure property resolver. */
public final class SchedulerSettings {
    private final Properties defaults = TestConfigurationFiles.load("scheduler.properties");
    private final Properties project = TestConfigurationFiles.load("test.properties");
    public final String environment = TestEnvironment.current();

    public String optional(String key, String fallback) {
        String shared = switch (key) {
            case "fixtures.enabled", "fixtures.isolated", "user-id", "other-user-id" -> key;
            case "tunnel.enabled" -> "kubernetes.tunnel.enabled";
            default -> null;
        };
        if (shared != null) {
            String value = new StandSettings().optional(shared, "scheduler." + environment + "." + key,
                    "scheduler." + key, null);
            if (value != null) return value;
        }
        String scoped = "scheduler." + environment + "." + key;
        String common = "scheduler." + key;
        String value = project.getProperty(scoped, project.getProperty(common,
                defaults.getProperty(scoped, defaults.getProperty(common, fallback))));
        return value == null ? null : value.trim();
    }
    public String required(String key) {
        String value = optional(key, null);
        if (value == null || value.isBlank() || value.startsWith("<SET_ME_"))
            throw new IllegalStateException("Configure scheduler." + environment + "." + key);
        value = SecurePropertyResolver.resolve(value);
        if (value.isBlank() || value.contains("${") || value.startsWith("<SET_ME_"))
            throw new IllegalStateException("Unresolved scheduler setting: " + key);
        return value;
    }
    public boolean tunnelEnabled() {
        String value = System.getProperty("scheduler.tunnel.enabled", optional("tunnel.enabled", "false")).trim();
        if (!"true".equals(value) && !"false".equals(value))
            throw new IllegalArgumentException("scheduler.tunnel.enabled must be true or false");
        boolean tunnel = Boolean.parseBoolean(value);
        if (tunnel && config.services.rest.RestMtlsConfiguration.enabled(environment))
            throw new IllegalStateException("Scheduler REST cannot use a tunnel while stand mTLS is enabled; set stand."
                    + environment + ".kubernetes.tunnel.enabled=false");
        return tunnel;
    }
    public String baseUri() {
        String explicit = optional("base-uri", "");
        return explicit.isBlank() ? RestEndpointResolver.baseUri(RestServiceEndpoint.SCHEDULER) : uri(required("base-uri"));
    }
    public static String uri(String value) {
        URI uri = URI.create(value);
        if (uri.getHost() == null || !java.util.Set.of("http", "https").contains(uri.getScheme())
                || uri.getUserInfo() != null) throw new IllegalArgumentException("Expected HTTP(S) URI without embedded credentials");
        return value.replaceAll("/+$", "");
    }
    public long user() {
        StandFixtureUsers.Pair users = StandFixtureUsers.current();
        return users == null ? positive("user-id") : users.primary();
    }
    public long otherUser() {
        StandFixtureUsers.Pair users = StandFixtureUsers.current();
        long other = users == null ? positive("other-user-id") : users.secondary();
        if (other == user()) throw new IllegalStateException("Two distinct scheduler users required");
        return other;
    }
    public long positive(String key) {
        long value = Long.parseLong(required(key));
        if (value <= 0) throw new IllegalStateException("Expected positive scheduler setting: " + key);
        return value;
    }
    public String splittingPoint() { return optional("splitting-point", "MAPPER"); }
    public int timeoutSeconds() { return Integer.parseInt(optional("timeout.seconds", "30")); }
    public int bulkTimeoutSeconds() {
        int value = Integer.parseInt(optional("bulk.timeout.seconds", "300"));
        if (value < timeoutSeconds() || value > 600)
            throw new IllegalStateException("scheduler.bulk.timeout.seconds must be between the normal timeout and 600");
        return value;
    }
    public boolean managedFixturesEnabled() {
        return new StandSettings().flag("workloads.scheduler.fixtures.prepare.enabled");
    }
    public boolean jobsPaused() {
        // A properties flag cannot impersonate a completed managed preparation.
        return managedFixturesEnabled()
                ? steps.flow.scheduler.SchedulerFixtureStandSteps.readyForCurrentScenario()
                : "true".equals(optional("jobs.paused", "false"));
    }
    public void requireFixturePermission() {
        if (!"true".equals(optional("fixtures.isolated", "false")))
            throw new IllegalStateException("Confirm a dedicated test namespace with scheduler.<env>.fixtures.isolated=true; do not use shared business objects");
        if (!"true".equals(required("fixtures.enabled")) || !jobsPaused())
            throw new IllegalStateException("Owned DB fixtures require fixtures.enabled=true and independently paused scheduler jobs");
        user();
        if (!tunnelEnabled()) baseUri();
    }
}
