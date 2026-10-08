package util.splittercheck;

import java.util.Locale;

public enum SplitterCheckMode {
    OFF,
    WARN,
    FAIL;

    public static SplitterCheckMode fromSystemProperty() {
        String raw = System.getProperty("splitter.check.mode", "WARN");
        return fromString(raw);
    }

    public static SplitterCheckMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return WARN;
        }
        try {
            return SplitterCheckMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WARN;
        }
    }

    public boolean isEnabled() {
        return this != OFF;
    }

    public boolean isFailFast() {
        return this == FAIL;
    }
}
