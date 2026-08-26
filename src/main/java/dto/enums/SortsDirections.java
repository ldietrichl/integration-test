package dto.enums;

import lombok.Getter;

@Getter
public enum SortsDirections {
    ASC("ASC"),
    DESC("DESC"),

    EMPTY_FOR_TEST(""), // Пустой

    NONEXISTENT_FOR_TEST("NONEXISTENT"), // Несуществующий
    INCORRECT_EXACT_FOR_TEST("INCORRECT_STATUS `~!@#$%^&*()-_+={}[]|\\//,<.>/?"); // Некорректный

    private final String value;

    SortsDirections(String value) {
        this.value = value;
    }
}
