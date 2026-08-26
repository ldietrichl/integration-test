package dto.enums;

public enum RequestSourceSplitConfig {
    SPLITTER("SPLITTER"),
    CONFIG_SERVICE("CONFIG_SERVICE"),
    INVALID_REQUEST_SOURCE("INVALID_REQUEST_SOURCE");

    private final String value;

    RequestSourceSplitConfig(String value) {
        this.value = value;
    }
}
