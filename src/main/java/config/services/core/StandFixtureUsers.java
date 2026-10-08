package config.services.core;

/** Scenario-local database identities. No credentials, account writes or cross-test JVM properties. */
public final class StandFixtureUsers {
    public record Pair(long primary, long secondary) {
        public Pair {
            if (primary <= 0 || secondary <= 0 || primary == secondary)
                throw new IllegalArgumentException("Two distinct positive fixture user IDs required");
        }
    }
    private static final ThreadLocal<Pair> CURRENT = new ThreadLocal<>();
    private StandFixtureUsers() {}
    public static void install(Pair pair) { CURRENT.set(java.util.Objects.requireNonNull(pair)); }
    public static Pair current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
