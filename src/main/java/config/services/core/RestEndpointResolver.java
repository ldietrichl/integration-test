package config.services.core;

import java.net.URI;

/**
 * Разрешает логический REST-сервис в base URI выбранного окружения.
 *
 * <p>Окружение и все URI берутся только из
 * {@code src/test/resources/test.properties}.</p>
 */
public final class RestEndpointResolver {

    private static final String REST_PREFIX = "rest.";
    private static final String BASE_URI_SUFFIX = ".base-uri";

    private RestEndpointResolver() {
    }

    public static String currentEnvironment() {
        return TestEnvironment.current();
    }

    public static String baseUri(RestServiceEndpoint endpoint) {
        String environment = currentEnvironment();
        String endpointKey = propertyKey(environment, endpoint.propertySegment());
        String configuredUri = TestPropertiesLoader.optional(endpointKey);

        if (configuredUri == null || configuredUri.isBlank()) {
            for (String fallbackSegment : endpoint.fallbackPropertySegments()) {
                configuredUri = TestPropertiesLoader.optional(propertyKey(environment, fallbackSegment));
                if (configuredUri != null && !configuredUri.isBlank()) {
                    break;
                }
            }
        }

        if ((configuredUri == null || configuredUri.isBlank()) && endpoint.gatewayFallbackAllowed()) {
            configuredUri = TestPropertiesLoader.required(propertyKey(environment, "gateway"));
        }

        if (configuredUri == null || configuredUri.isBlank()) {
            throw new IllegalStateException(
                    "Для REST-сервиса " + endpoint + " и окружения " + environment
                            + " не задан URI в src/test/resources/test.properties. Ожидаемый ключ: "
                            + endpointKey);
        }

        return validateAndNormalize(configuredUri, endpointKey);
    }

    public static void validateCurrentEnvironment() {
        for (RestServiceEndpoint endpoint : RestServiceEndpoint.values()) {
            baseUri(endpoint);
        }
    }

    private static String propertyKey(String environment, String service) {
        return REST_PREFIX + environment + "." + service + BASE_URI_SUFFIX;
    }

    private static String validateAndNormalize(String rawUri, String propertyKey) {
        String value = rawUri.trim();
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Некорректный URI в параметре " + propertyKey + ": " + value, e);
        }

        if (uri.getScheme() == null || uri.getHost() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalStateException(
                    "Параметр " + propertyKey + " должен содержать абсолютный HTTP(S) URI: " + value);
        }

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
