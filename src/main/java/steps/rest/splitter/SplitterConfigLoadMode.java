package steps.rest.splitter;

import java.util.Locale;

enum SplitterConfigLoadMode {
    REST,
    KAFKA;

    private static final String PROPERTY = "splitter.config.load.mode";

    static SplitterConfigLoadMode current() {
        String raw = SplitterConfigProperties.string(PROPERTY, REST.name());
        try {
            return valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + PROPERTY + "=" + raw
                    + ". Expected one of: rest, kafka", exception);
        }
    }

    static boolean isKafka() {
        return current() == KAFKA;
    }
}
