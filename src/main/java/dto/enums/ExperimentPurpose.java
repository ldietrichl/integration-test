package dto.enums;

import lombok.Getter;

@Getter
public enum ExperimentPurpose {
    COMMON("COMMON"),
    PILOT("PILOT"),
    DCG("DCG"),
    CLBR("CLBR");

    private final String value;

    ExperimentPurpose(String value) {
        this.value = value;
    }
}
