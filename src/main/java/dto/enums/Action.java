package dto.enums;

public enum Action {
    START("START"),
    STOP("STOP"),
    UPDATE("UPDATE"),
    INVALID_ACTION("INVALID_ACTION");

    private String value;

    Action(String value) {
        this.value = value;
    }
}
