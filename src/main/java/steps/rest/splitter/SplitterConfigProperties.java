package steps.rest.splitter;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

final class SplitterConfigProperties {

    private static final Properties TEST_PROPERTIES = loadTestProperties();

    private SplitterConfigProperties() {
    }

    static String string(String key, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (hasText(systemValue)) {
            return systemValue.trim();
        }

        String envValue = System.getenv(envKey(key));
        if (hasText(envValue)) {
            return envValue.trim();
        }

        String fileValue = TEST_PROPERTIES.getProperty(key);
        if (hasText(fileValue)) {
            return fileValue.trim();
        }

        return defaultValue;
    }

    static boolean bool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(string(key, String.valueOf(defaultValue)));
    }

    static Duration durationSeconds(String key, Duration defaultValue) {
        String raw = string(key, String.valueOf(defaultValue.toSeconds()));
        try {
            return Duration.ofSeconds(Long.parseLong(raw));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property " + key + " must be a number of seconds, actual=" + raw,
                    exception);
        }
    }

    private static Properties loadTestProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("test.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось прочитать test.properties", exception);
        }
        return properties;
    }

    private static String envKey(String key) {
        return key.toUpperCase(Locale.ROOT)
                .replace('.', '_')
                .replace('-', '_');
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
