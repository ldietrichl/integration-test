package config.services.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.Locale;
import steps.rest.scheduler.SchedulerSteps;

/** A real application token, not an OpenShift token. No token or raw claims in evidence. */
public final class SchedulerApplicationIdentity {
    private static final ThreadLocal<Long> RESOLVED_USER = new ThreadLocal<>();
    private SchedulerApplicationIdentity() { }

    /** DEV can expose an effective system actor without authenticating a human user. */
    public static boolean observesDevAuthor() {
        StandSettings stand = new StandSettings();
        String mode = stand.optional("application.identity.mode",
                "dev".equals(stand.environment) ? "observed-creator" : "token");
        if (!java.util.Set.of("observed-creator", "token").contains(mode))
            throw new IllegalStateException("Expected application.identity.mode=observed-creator or token");
        if ("observed-creator".equals(mode) && !"dev".equals(stand.environment))
            throw new IllegalStateException("Observed anonymous creator mode is approved for DEV only");
        return "observed-creator".equals(mode);
    }

    public static String requireToken() {
        StandSettings stand = new StandSettings();
        String name = stand.optional("application.access-token.env",
                "EXPLAB_" + stand.environment.toUpperCase(Locale.ROOT) + "_ACCESS_TOKEN");
        if (!name.matches("[A-Z_][A-Z0-9_]*"))
            throw new IllegalStateException("Expected an environment-variable name for stand application.access-token.env");
        String token = System.getenv(name);
        if (token == null || token.isBlank())
            throw new IllegalStateException("APPLICATION_IDENTITY_REQUIRED: set " + name
                    + " locally before starting Gradle/IDE; do not use an OpenShift token");
        token = token.trim();
        claims(token);
        return token;
    }

    public static String subject(String token) { return claims(token).path("sub").asText(); }

    private static JsonNode claims(String token) {
        try {
            if (token.length() > 32768 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException();
            JsonNode claims = SchedulerSteps.JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            if (claims == null || !claims.isObject() || !claims.path("sub").isTextual()
                    || claims.path("sub").asText().isBlank() || claims.path("sub").asText().length() > 512
                    || !claims.path("exp").isIntegralNumber()
                    || claims.path("exp").asLong() <= java.time.Instant.now().getEpochSecond() + 30)
                throw new IllegalArgumentException();
            return claims;
        } catch (Exception invalid) {
            // Do not attach parser exceptions: they may include the credential-bearing input.
            throw new IllegalStateException("APPLICATION_TOKEN_INVALID_OR_EXPIRED: real application JWT with sub and exp required");
        }
        // Parsing does not validate the JWT signature. Authentication remains the service's responsibility.
    }

    public static void bindUser(long id) {
        if (id <= 0) throw new IllegalArgumentException("A positive resolved application user ID is required");
        RESOLVED_USER.set(id);
    }

    public static void requireUser(long expected) {
        Long resolved = RESOLVED_USER.get();
        if (resolved == null || resolved != expected)
            throw new IllegalStateException("MY_TASKS requires a user-service identity matching the primary DB fixture user");
    }

    public static void clear() { RESOLVED_USER.remove(); }
}
