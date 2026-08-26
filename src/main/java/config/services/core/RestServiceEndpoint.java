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
    SPLITTER("splitter", true),
    CONFIGURATION_SERVICE("configuration-service", true),
    MESSAGE_AUDIT("message-audit", true);

    private final String propertySegment;
    private final boolean gatewayFallbackAllowed;

    RestServiceEndpoint(String propertySegment, boolean gatewayFallbackAllowed) {
        this.propertySegment = propertySegment;
        this.gatewayFallbackAllowed = gatewayFallbackAllowed;
    }

    String propertySegment() {
        return propertySegment;
    }

    boolean gatewayFallbackAllowed() {
        return gatewayFallbackAllowed;
    }
}
