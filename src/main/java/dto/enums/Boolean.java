package dto.enums;

import lombok.Getter;

@Getter
public enum Boolean {
    TRUE("true"),
    FALSE("false"),

    NONEXISTENT_FOR_TEST("NONEXISTENT"), // Несуществующий
    INCORRECT_EXACT_FOR_TEST("INCORRECT_STATUS `~!@#$%^&*()-_+={}[]|\\//,<.>/?"), // Некорректный
    EMPTY_FOR_TEST(""); // Пустой

    private final String value;

    Boolean(String value) {
        this.value = value;
    }
}
