package ru.sber.qa.splitter.tests_v9.document;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;
import java.util.stream.Stream;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9. Тест 3: MAPPER-матрица альтернатив")
@AnyConfigLoadMode
public class SplitterV9DocumentMapperAlternativeMatrixFlowTest extends AbstractSplitterV9FlowTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("mapperAlternativeCases")
    void mapperAlternativeCasesShouldFollowDocumentMatrix(MapperAlternativeCase testCase) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange(testCase.id(), V9_SALT, testCase.rangeFrom(), testCase.rangeTo());
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем MAPPER config для матрицы альтернатив " + testCase.id(),
                        flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split для " + testCase.id() + ": splittingId=" + splittingId
                                + ", spread=" + spread(V9_SALT, splittingId),
                        flow -> {
                            ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                            assertBasicResponseContract(response, request, version);
                            assertSplittingResultsHaveUniqueObjectIds(response);
                            long expectedSpread = spread(V9_SALT, splittingId);
                            verifyObject(response, OBJECT_1, testCase.object1(), expectedSpread);
                            verifyObject(response, OBJECT_2, testCase.object2(), expectedSpread);
                            assertAllResponseRowsAreWorkedGroupsForEveryObject(response);
                        })
                .run();
    }

    private void verifyObject(ValidatableResponseWrapper response, String objectId, ExpectedMain expected, long expectedSpread) {
        if (expected == null) {
            assertObjectEmptyOrAbsent(response, objectId);
            return;
        }
        assertRuleResultSize(response, objectId, "MAIN", 1);
        assertRuleExpsHaveMandatoryFields(response, objectId, "MAIN");
        assertRuleExpsHaveSpreadValue(response, objectId, "MAIN", expectedSpread);
        assertFirstRuleExp(response, objectId, "MAIN", expected.expId(), expected.expGroup(), expected.finalExpGroup());
        assertFirstRuleExpActionType(response, objectId, "MAIN", expected.actionType());
        assertMainHasNoExpFlags(response, objectId);
        assertFilteredFlag(response, objectId, "2".equals(expected.actionType()) || "4".equals(expected.actionType()));
        if (findRule(response, objectId, "ALL", false) != null) {
            assertRuleExpsHaveMandatoryFields(response, objectId, "ALL");
            assertRuleExpsHaveSpreadValue(response, objectId, "ALL", expectedSpread);
            assertAllExpFlagsHaveAlternativeValue(response, objectId, "false");
        }
    }

    private LoadConfigRequestDto mapperAlternativeConfig(long version) {
        return configFor(EndpointMode.MAPPER, version, exp1(), exp2(), exp3());
    }

    private ExperimentDto exp1() {
        return experiment(1,
                V9_SALT,
                List.of(
                        objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "id2", "2", "INTEGER")),
                List.of(
                        groupWithDocResult("A", List.of(share(0, 2500)), 1, "0", "1"),
                        groupWithDocResult("B", List.of(share(2500, 5000)), 2, "1", "2")));
    }

    private ExperimentDto exp2() {
        List<ShareDto> shares = List.of(share(0, 1000), share(2500, 3500), share(5000, 6000));
        return experiment(2,
                V9_SALT,
                List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                List.of(groupWithDocResult("A", shares, 1, "1", "2")));
    }

    private ExperimentDto exp3() {
        List<ShareDto> shares = List.of(
                share(0, 500),
                share(1000, 1500),
                share(2500, 3000),
                share(3500, 4000),
                share(5000, 5500),
                share(6000, 6500));
        return experiment(3,
                V9_SALT,
                List.of(objectParamEqualsCondition(1, "id2", "2", "INTEGER")),
                List.of(groupWithDocResult("A", shares, 1, "3", "3")));
    }

    private static Stream<Arguments> mapperAlternativeCases() {
        return Stream.of(
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-00-05-ALLOW", 0, 500, main(2, "A", "A", "1"), main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-05-10-ALLOW", 500, 1000, main(2, "A", "A", "1"), null)),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-10-15-ALLOW", 1000, 1500, main(1, "A", "A", "0"), main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-15-20-ALLOW", 1500, 2000, main(1, "A", "A", "0"), null)),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-25-30-ALLOW", 2500, 3000, main(2, "A", "A", "1"), main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-30-35-ALLOW", 3000, 3500, main(2, "A", "A", "1"), main(1, "B", "B", "1"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-35-40-ALLOW", 3500, 4000, null, main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-40-45-ALLOW", 4000, 4500, null, main(1, "B", "B", "1"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-50-55-ALLOW", 5000, 5500, main(2, "A", "A", "1"), main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-55-60-ALLOW", 5500, 6000, main(2, "A", "A", "1"), null)),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-60-65-ALLOW", 6000, 6500, null, main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-65-70-ALLOW", 6500, 7000, null, null)),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-55-60-DENY", 5500, 6000, main(2, "A", "A", "1"), null)),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-60-65-DENY", 6000, 6500, null, main(3, "A", "A", "3"))),
                Arguments.of(new MapperAlternativeCase("SPL-V9-T04M-65-70-DENY", 6500, 7000, null, null))
        );
    }

    private static ExpectedMain main(long expId, String expGroup, String finalExpGroup, String actionType) {
        return new ExpectedMain(expId, expGroup, finalExpGroup, actionType);
    }

    private record MapperAlternativeCase(String id,
                                         int rangeFrom,
                                         int rangeTo,
                                         ExpectedMain object1,
                                         ExpectedMain object2) {
        @Override
        public String toString() {
            return id;
        }
    }

    private record ExpectedMain(long expId, String expGroup, String finalExpGroup, String actionType) {
    }
}
