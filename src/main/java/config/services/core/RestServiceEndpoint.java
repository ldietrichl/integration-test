package config.services.core;

/**
 * Логические REST-сервисы проекта.
 *
 * <p>Имена намеренно отражают назначение URI. Даже если несколько сервисов
 * опубликованы через один ingress, тестовый код больше не оперирует
 * безымянными переменными {@code url}/{@code uri}.</p>
 */
public enum RestServiceEndpoint {
    EXPLAB_GATEWAY("gateway", false),
    EXPERIMENTS("experiments", true),
    DICTIONARIES("dictionaries", true),
    DATA_OPERATOR("data-operator", true),
    MESSAGES("messages", true),
    SPLITTER_MAPPER("splitter-mapper", true, "splitter"),
    SPLITTER_REACTIONS("splitter-reactions", true, "splitter"),
    SPLITTER("splitter", true, "splitter-mapper"),
    CONFIGURATION_SERVICE("configuration-service", true),
    MESSAGE_AUDIT("message-audit", true);

    private final String propertySegment;
    private final boolean gatewayFallbackAllowed;
    private final String[] fallbackPropertySegments;

    RestServiceEndpoint(String propertySegment, boolean gatewayFallbackAllowed, String... fallbackPropertySegments) {
        this.propertySegment = propertySegment;
        this.gatewayFallbackAllowed = gatewayFallbackAllowed;
        this.fallbackPropertySegments = fallbackPropertySegments;
    }

    String propertySegment() {
        return propertySegment;
    }

    boolean gatewayFallbackAllowed() {
        return gatewayFallbackAllowed;
    }

    String[] fallbackPropertySegments() {
        return fallbackPropertySegments;
    }
}
