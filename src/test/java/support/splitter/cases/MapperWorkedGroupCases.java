package support.splitter.cases;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;
import steps.flow.splitter.workedgroup.MapperWorkedGroupSteps.WorkedGroupCase;

public final class MapperWorkedGroupCases {

    public static Stream<Arguments> differentConditionCases() {
        return Stream.of(
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-A", 0, 2500, "A", 1, "0", "101")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-B", 2500, 5000, "B", 2, "1", "202")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-01-C", 5000, 7500, "C", 3, "3", "303")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-02-NO-MAIN", 7500, 10000, null, 0, null, null))
        );
    }

    public static Stream<Arguments> sameConditionCases() {
        return Stream.of(
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-A", 0, 2500, "A", 1, "0", "101")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-B", 2500, 5000, "B", 1, "1", "202")),
                Arguments.of(new WorkedGroupCase("EXPLAB-2690-03-C", 5000, 7500, "C", 1, "3", "303"))
        );
    }
}
