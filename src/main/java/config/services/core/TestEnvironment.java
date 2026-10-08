package config.services.core;

import java.util.Locale;

/** One environment selector shared by REST and infrastructure used by tests. */
public final class TestEnvironment {
    private TestEnvironment() { }

    public static String current() {
        // One editable selector for IDEA and Gradle; stale VM/OS overrides cannot redirect a run.
        return normalize(TestPropertiesLoader.requiredFromFile("env"));
    }

    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Test environment is required");
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "dev" -> "dev";
            case "ift", "eift", "ift-ds", "eift-ds" -> "ift";
            case "ift-dm", "eift-dm" -> "ift-dm";
            case "lt" -> "lt";
            case "local", "localhost" -> "local";
            default -> throw new IllegalArgumentException("Unsupported test environment; use dev, ift, ift-dm, lt or local");
        };
    }
}
