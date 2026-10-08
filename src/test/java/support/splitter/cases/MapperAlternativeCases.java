package support.splitter.cases;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;
import static steps.flow.splitter.workedgroup.WorkedGroupSteps.*;
import steps.flow.splitter.workedgroup.MapperAlternativeSteps.AlternativeCase;

public final class MapperAlternativeCases {

    public static Stream<Arguments> alternativeCases() {
        return Stream.of(
                Arguments.of(new AlternativeCase(
                        "EXPLAB-2690-04-A-WORKED",
                        0, 5000,
                        "A",
                        LEFT_OBJECT_ID, 1, "1", "101",
                        RIGHT_OBJECT_ID, 2, "B", "3", "202")),
                Arguments.of(new AlternativeCase(
                        "EXPLAB-2690-04-B-WORKED",
                        5000, 10000,
                        "B",
                        RIGHT_OBJECT_ID, 2, "3", "202",
                        LEFT_OBJECT_ID, 1, "A", "1", "101"))
        );
    }
}
