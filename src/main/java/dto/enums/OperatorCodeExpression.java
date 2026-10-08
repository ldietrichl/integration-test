package dto.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum OperatorCodeExpression {
    EQUAL("equal"),
    NOT_EQUAL("not_equal"),
    IN("in"),
    NOT_IN("not_in"),
    LIKE_ANY("like_any"),
    LIKE("like"),
    NOT_LIKE("not_like"),
    NOT_LIKE_ANY("not_like_any"),
    MORE("more"),
    MORE_EQUAL("more_equal"),
    LESS("less"),
    LESS_EQUAL("less_equal"),
    IS_NULL("is_null"),
    IS_NOT_NULL("is_not_null"),
    LIKE_ALL("like_all"),
    NOT_LIKE_ALL("not_like_all"),

    EMPTY_FOR_TEST(""), // Пустой

    EQUAL_UPPER_CASE_FOR_TEST("EQUAL"),
    NONEXISTENT_FOR_TEST("nonexistent"), // Несуществующий
    INCORRECT_STATUS_FOR_TEST("[INCORRECT_STATUS `~!@#$%^&*()-_+={}[]|\\//,<.>/?]"); // Некорректный

    private final String value;

    OperatorCodeExpression(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
