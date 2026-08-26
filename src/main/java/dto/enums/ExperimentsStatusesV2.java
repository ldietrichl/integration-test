package dto.enums;

import lombok.Getter;

import java.util.List;

@Getter
public enum ExperimentsStatusesV2 {
    DRAFT("DRAFT"),
    AGREEMENT("AGREEMENT"),
    AGREED("AGREED"),
    NOT_AGREED("NOT_AGREED"),
    STARTING("STARTING"),
    IN_PROGRESS("IN_PROGRESS"),
    STOPING("STOPING"),
    STOPPED("STOPPED"),
    COMPLETED("COMPLETED"),

    EMPTY_FOR_TEST(""), // Пустой

    DRAFT_LOWER_CASE_FOR_TEST("draft"),
    NONEXISTENT_FOR_TEST("NONEXISTENT"), // Несуществующий
    INCORRECT_STATUS_FOR_TEST("[INCORRECT_STATUS `~!@#$%^&*()-_+={}[]|\\//,<.>/?]"); // Некорректный

    private final String value;

    ExperimentsStatusesV2(String value) {
        this.value = value;
    }

    public static List<ExperimentsStatusesV2> all() {
        return List.of(DRAFT, AGREEMENT, AGREED, NOT_AGREED, STARTING, IN_PROGRESS, STOPING, STOPPED, COMPLETED);
    }
}
