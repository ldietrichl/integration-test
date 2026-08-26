package constants;

import lombok.Getter;

@Getter
public enum ConstantsForTests {
    PROJECT_NAME ("Автоматизация тестирования на проекте \"ExpLab - модуль A/B тестирования\""),
    AUTOTEST_FIELDS_MARKER ("---8<---[QA-AT \"ExpLab-A/B тесты\"]------");

    final String value;

    ConstantsForTests(String value) {
        this.value = value;
    }
}
