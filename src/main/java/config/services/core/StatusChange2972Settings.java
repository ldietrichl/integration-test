package config.services.core;

import steps.db.experiments.v2.StatusChange2972DbSteps;

import config.services.core.RestEndpointResolver;
import config.services.core.RestServiceEndpoint;
import config.services.core.SecurePropertyResolver;
import config.services.core.TestConfigurationFiles;
import config.services.core.TestEnvironment;
import java.nio.file.Path;
import java.util.*;

/** The environment selector and scenario settings come exclusively from test.properties. */
public final class StatusChange2972Settings {
    public final Properties properties = TestConfigurationFiles.load("test.properties");
    public final String env = TestEnvironment.current();
    public final String experiments = RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS);
    public final String configurations = RestEndpointResolver.baseUri(RestServiceEndpoint.CONFIGURATION_SERVICE);

    public StatusChange2972Settings() {
        if (!Set.of("dev", "ift", "ift-dm", "lt", "local").contains(env))
            throw new IllegalStateException("EXPLAB-2972 stand suite requires env=dev/ift/ift-dm/lt/local in test.properties");
    }
    public String optional(String name, String fallback) {
        String value = properties.getProperty("explab2972." + env + "." + name,
                properties.getProperty("explab2972." + name, fallback));
        return value == null ? null : value.trim();
    }
    public String required(String name) {
        String value = optional(name, null);
        if (value == null || value.isBlank()) throw new IllegalStateException(
                "Set explab2972." + env + "." + name + " in src/test/resources/test.properties");
        return value.contains("${") ? resolveSecret(value, name) : value;
    }
    public String secret(String property) {
        String raw = properties.getProperty(property);
        if (raw == null || raw.isBlank()) throw new IllegalStateException("Missing property " + property + " in test.properties");
        return resolveSecret(raw, property);
    }
    public static String resolveSecret(String raw, String key) {
        String value = SecurePropertyResolver.resolve(raw);
        if (value == null || value.isBlank() || value.contains("${") || value.startsWith("<SET_ME_"))
            throw new IllegalStateException("Credential not configured: " + key);
        return value;
    }
    public String token(String role, boolean config) {
        if (env.equals("local") && !localAuth()) return "";
        if (env.equals("local")) return resolveSecret(required("auth." + role + ".token"), "auth." + role + ".token");
        if (role.equals("primary") && config) return secret("rest.configuration-service.token");
        String key = "auth." + role + ".token";
        String scoped = optional(key, null);
        // An explicitly configured credential must never fall back to another identity.
        if (scoped != null) return resolveSecret(scoped, key);
        if (role.equals("primary")) return secret("rest.explab-gateway.token");
        return resolveSecret(required(key), key);
    }
    public boolean localAuth() { return env.equals("local") && "true".equals(optional("auth.enabled", "false")); }
    public long user(String role) {
        String key = "auth." + role + ".user-id";
        String configured = env.equals("local") ? optional(key, "1") : required(key);
        return parseUserId(role, configured);
    }
    public void observeUser(String role, long observedId) {
        if (observedId <= 0) throw new IllegalStateException(
                "Experiment service returned an invalid createdBy for " + role + ": " + observedId);
        String key = "auth." + role + ".user-id";
        String configured = optional(key, null);
        if (configured != null && !configured.isBlank()) {
            long expected = parseUserId(role, configured);
            if (expected != observedId) throw new IllegalStateException(
                    "Configured explab2972." + env + "." + key + "=" + expected
                            + " does not match experiment-service createdBy=" + observedId);
        }
        properties.setProperty("explab2972." + env + "." + key, Long.toString(observedId));
    }
    private long parseUserId(String role, String value) {
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) throw new NumberFormatException("non-positive");
            return id;
        } catch (RuntimeException error) {
            throw new IllegalStateException("Set explab2972." + env + ".auth." + role
                    + ".user-id to the positive numeric id of the configured token user", error);
        }
    }
    public int timeout() { return Integer.parseInt(optional("timeout.seconds", "120")); }
    public List<Integer> selected(String group) {
        String value = group.equals("regular") ? optional(group + ".cases", "1,4,5,7,16,18,19,20") : required(group + ".cases");
        List<Integer> ids = new ArrayList<>();
        for (String item : value.split(",")) {
            int id = Integer.parseInt(item.trim().replace("TP-", ""));
            if (id < 1 || id > 60 || ids.contains(id)) throw new IllegalArgumentException("Invalid/duplicate scenario " + item);
            ids.add(id);
        }
        return ids;
    }
    public void requireManaged(int id) {
        String key = String.format("case.%02d.prepared", id);
        if (!"true".equals(required(key))) throw new IllegalStateException("Confirm actual stand prerequisites with " + key + "=true");
    }
    public Path fixture() { return Path.of(optional("fixture", "src/test/resources/explab2972/experiment-template.json")); }
    public void validateExecution(List<Integer> selected) throws Exception {
        for (int id : selected) if (steps.rest.experiments.v2.StatusChange2972ControlSteps.CASES.contains(id))
            steps.rest.experiments.v2.StatusChange2972ControlSteps.preflight(this, id);
        if (env.equals("local") && (selected.contains(2) || selected.contains(3)) && !localAuth())
            throw new IllegalStateException("TP-2/TP-3 require separate authenticated users; enable the explicit local identity profile");
        if (!java.nio.file.Files.isRegularFile(fixture())) throw new IllegalStateException("Fixture file missing: " + fixture());
        if (!env.equals("local")) {
        Path keystore=Path.of(optional("keystore","src/test/resources/keystore.p12"));
        if (!java.nio.file.Files.isRegularFile(keystore)) throw new IllegalStateException("Keystore missing: " + keystore);
        secret("keystore.pass"); token("primary",false);
        }
        if(selected.contains(2)){
            token("primary",false);token("approver",false);
            if(user("primary")<=1 || user("approver")<=1 || user("primary")==user("approver"))
                throw new IllegalStateException("TP-02 requires two distinct non-system identities");
        }
        if(selected.contains(3)){
            token("primary",false);token("denied",false);
            if(user("denied")<=1 || user("denied")==user("primary"))
                throw new IllegalStateException("TP-03 requires a distinct user without agreement permission");
        }
        if(selected.stream().anyMatch(Set.of(39,40,41,42,43,44,45,49,50,51,52,60)::contains)) {
            token("primary",true);
            try(var db=new StatusChange2972DbSteps(this,"configuration")){db.query("SELECT id FROM configurations.config_request WHERE false");}
        }
        if(selected.stream().anyMatch(Set.of(6,12,13,21,28,29,30,34,35,39,40,41,42,43,44,45,46,47,48,57,60)::contains)) {
            try(var db=new StatusChange2972DbSteps(this,"experiment")){db.query("SELECT id FROM experiments.status_change_element WHERE false");}
        }
    }
}
