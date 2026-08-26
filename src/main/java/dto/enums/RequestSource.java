package dto.enums;

public enum RequestSource {
    UI("UI"),
    SCHEDULER("SCHEDULER"),
    KLEIBER("KLEIBER"),
    INVALID_REQUEST_SOURCE("INVALID_REQUEST_SOURCE");

    private final String value;

    RequestSource(String value) {
        this.value = value;
    }
}
