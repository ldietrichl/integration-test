package support.splitter.cases;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;
import steps.flow.splitter.workedgroup.MapperNoMainSteps.NoMainMode;
import steps.flow.splitter.workedgroup.MapperNoMainSteps.NoMainCase;

public final class MapperNoMainCases {

    public static Stream<Arguments> noMainCases() {
        return Stream.of(
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-MISSING-ACTION-TYPE",
                        NoMainMode.MISSING_ACTION_TYPE,
                        "сработавшая группа не содержит actionType")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-UNKNOWN-ACTION-TYPE",
                        NoMainMode.UNKNOWN_ACTION_TYPE,
                        "actionType отсутствует в values-map mapperFinalExp")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-WRONG-ACTION-TYPE-DATA-TYPE",
                        NoMainMode.WRONG_ACTION_TYPE_DATA_TYPE,
                        "actionType имеет тип STRING вместо INTEGER")),
                Arguments.of(new NoMainCase(
                        "EXPLAB-2690-05-EMPTY-RESULT-PARAMS",
                        NoMainMode.EMPTY_RESULT_PARAMS,
                        "resultParams сработавшей группы пуст"))
        );
    }
}
