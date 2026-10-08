package support.splitter.cases;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;
import static steps.flow.splitter.workedgroup.WorkedGroupSteps.*;
import steps.flow.splitter.workedgroup.ReactionsWorkedGroupSteps.ReactionsCase;

public final class ReactionsWorkedGroupCases {

    public static Stream<Arguments> reactionsCases() {
        return Stream.of(
                Arguments.of(new ReactionsCase(
                        "EXPLAB-2690-06-A-WORKED", 0, 5000, "A",
                        LEFT_OBJECT_ID, 1, "1", "101",
                        RIGHT_OBJECT_ID, 2, "B", "3", "202")),
                Arguments.of(new ReactionsCase(
                        "EXPLAB-2690-06-B-WORKED", 5000, 10000, "B",
                        RIGHT_OBJECT_ID, 2, "3", "202",
                        LEFT_OBJECT_ID, 1, "A", "1", "101"))
        );
    }
}
