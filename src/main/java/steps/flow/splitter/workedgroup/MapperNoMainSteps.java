package steps.flow.splitter.workedgroup;

import dto.splitter.config.SplittingResultDto;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperNoMainSteps extends WorkedGroupSteps {

    public SplittingResultDto resultFor(NoMainCase testCase) {
        return switch (testCase.mode()) {
            case MISSING_ACTION_TYPE -> resultWithParams(1, param("result", "500", "INTEGER"));
            case UNKNOWN_ACTION_TYPE -> resultWithParams(1,
                    param("actionType", "99", "INTEGER"),
                    param("result", "500", "INTEGER"));
            case WRONG_ACTION_TYPE_DATA_TYPE -> resultWithParams(1,
                    param("actionType", "1", "STRING"),
                    param("result", "500", "INTEGER"));
            case EMPTY_RESULT_PARAMS -> resultWithParams(1);
        };
    }

    public enum NoMainMode {
        MISSING_ACTION_TYPE,
        UNKNOWN_ACTION_TYPE,
        WRONG_ACTION_TYPE_DATA_TYPE,
        EMPTY_RESULT_PARAMS
    }

    public record NoMainCase(String id, NoMainMode mode, String description) {
        @Override
        public String toString() {
            return id;
        }
    }
}
