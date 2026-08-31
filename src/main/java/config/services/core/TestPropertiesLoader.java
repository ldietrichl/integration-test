package config.services.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Единственная точка чтения test.properties для REST-конфигурации.
 */
final class TestPropertiesLoader {

    private static final String TEST_PROPERTIES_RESOURCE = "test.properties";
    private static final Properties PROPERTIES = load();

    private TestPropertiesLoader() {
    }

    static String required(String key) {
        String value = optional(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "В src/test/resources/test.properties не задан обязательный параметр: " + key);
        }
        return value.trim();
    }

    static String optional(String key) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return SecurePropertyResolver.resolve(systemValue).trim();
        }

        String environmentValue = System.getenv(toEnvironmentName(key));
        if (environmentValue != null && !environmentValue.isBlank()) {
            return SecurePropertyResolver.resolve(environmentValue).trim();
        }

        String value = PROPERTIES.getProperty(key);
        return value == null ? null : SecurePropertyResolver.resolve(value).trim();
    }

    private static String toEnvironmentName(String key) {
        StringBuilder result = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char current = key.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                result.append(Character.toUpperCase(current));
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    private static Properties load() {
        Properties properties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TestPropertiesLoader.class.getClassLoader();
        }

        try (InputStream input = classLoader.getResourceAsStream(TEST_PROPERTIES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Не найден src/test/resources/" + TEST_PROPERTIES_RESOURCE);
            }
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Не удалось прочитать src/test/resources/" + TEST_PROPERTIES_RESOURCE, e);
        }
    }
}
