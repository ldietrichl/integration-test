package ru.sber.qa.splitter.EXPLAB_2729;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import config.environment.EnvironmentConfigWithRest;
import dto.dataoperator.DataOperatorRuleDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.sber.qa.allure.CriticalRegression;
import util.dataoperator.DataOperatorAssertions;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static request.dataoperator.DataOperatorTestDataFactory.andGroup;
import static request.dataoperator.DataOperatorTestDataFactory.idsRequest;
import static request.dataoperator.DataOperatorTestDataFactory.impossibleValue;
import static request.dataoperator.DataOperatorTestDataFactory.orGroups;
import static request.dataoperator.DataOperatorTestDataFactory.registryRequest;
import static util.dataoperator.DataOperatorAssertions.ids;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-cache")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AnyConfigLoadMode
public class DataOperatorObjectIds2729RulesFlowTest extends AbstractDataOperator2729FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-07. Одно правило equal возвращает тот же набор ID, что splitting-objects")
    void singleEqualRuleShouldReturnExpectedIds() {
        getFlowWithRest()
                .step("Находим существующий параметр объекта в Ignite", flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    DataOperatorRuleDto equal = equalRule(candidate);
                    var rules = andGroup(equal);

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    List<String> expected = DataOperatorAssertions.normalizedControlIds(control);
                    Assertions.assertFalse(expected.isEmpty(),
                            "Динамически выбранное equal-правило должно находить хотя бы один объект: " + candidate);
                    DataOperatorAssertions.shouldHaveIdsExactly(response, expected);
                    DataOperatorAssertions.shouldHaveUniqueIds(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-08. Условия одной группы объединяются по логическому И")
    void rulesInsideGroupShouldBeCombinedByAnd() {
        getFlowWithRest()
                .step("Формируем equal AND is_not_null для одного существующего параметра", flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    var rules = andGroup(
                            equalRule(candidate),
                            candidateRule(candidate, "is_not_null"));

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    List<String> expected = DataOperatorAssertions.normalizedControlIds(control);
                    Assertions.assertFalse(expected.isEmpty(), "Позитивная AND-группа должна находить объект");
                    DataOperatorAssertions.shouldHaveIdsExactly(response, expected);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-09. Противоречивые условия внутри И-группы дают пустой результат")
    void contradictoryAndGroupShouldReturnEmptyIds() {
        getFlowWithRest()
                .step("Формируем equal(value) AND not_equal(value)", flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    var rules = andGroup(
                            equalRule(candidate),
                            candidateRule(candidate, "not_equal", candidate.value()));

                    var response = objectIds(flow, idsRequest(false, rules));
                    DataOperatorAssertions.shouldHaveEmptyIds(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-10. Группы rules объединяются по логическому ИЛИ")
    void ruleGroupsShouldBeCombinedByOr() {
        getFlowWithRest()
                .step("Формируем (equal existing) OR (equal impossible)", flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    var rules = orGroups(
                            List.of(equalRule(candidate)),
                            List.of(candidateRule(candidate, "equal", impossibleValue())));

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    List<String> expected = DataOperatorAssertions.normalizedControlIds(control);
                    Assertions.assertFalse(expected.isEmpty(), "Первая OR-группа должна находить объект");
                    DataOperatorAssertions.shouldHaveIdsExactly(response, expected);
                })
                .run();
    }

    @Test
    @DisplayName("DO-IDS-11. Пересечение результатов OR-групп не создает дубли ID")
    void overlappingOrGroupsShouldReturnUniqueIds() {
        getFlowWithRest()
                .step("Дважды передаем одинаковую OR-группу", flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    var rules = orGroups(
                            List.of(equalRule(candidate)),
                            List.of(equalRule(candidate)));

                    var response = objectIds(flow, idsRequest(false, rules));
                    DataOperatorAssertions.shouldHaveUniqueIds(response);
                })
                .run();
    }

    @ParameterizedTest(name = "DO-IDS-12.{index}. operator={0}")
    @MethodSource("basicOperatorCases")
    @DisplayName("DO-IDS-12. Базовые операторы возвращают те же ID, что контрольный метод")
    void basicOperatorsShouldMatchRegistryEndpoint(String operatorCode, ValueStrategy valueStrategy) {
        getFlowWithRest()
                .step("Проверяем оператор " + operatorCode, flow -> {
                    CandidateRule candidate = requireAnyCandidate(flow);
                    String[] values = valueStrategy.values(candidate);
                    var rules = andGroup(candidateRule(candidate, operatorCode, values));

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    DataOperatorAssertions.shouldHaveIdsExactly(response, DataOperatorAssertions.normalizedControlIds(control));
                    DataOperatorAssertions.shouldHaveUniqueIds(response);
                })
                .run();
    }

    Stream<Arguments> basicOperatorCases() {
        return Stream.of(
                Arguments.of("equal", (ValueStrategy) candidate -> new String[]{candidate.value()}),
                Arguments.of("in", (ValueStrategy) candidate -> new String[]{candidate.value(), impossibleValue()}),
                Arguments.of("not_equal", (ValueStrategy) candidate -> new String[]{impossibleValue()}),
                Arguments.of("not_in", (ValueStrategy) candidate -> new String[]{impossibleValue()}),
                Arguments.of("is_not_null", (ValueStrategy) candidate -> new String[]{})
        );
    }

    @ParameterizedTest(name = "DO-IDS-13.{index}. numeric operator={0}")
    @MethodSource("numericOperatorCases")
    @DisplayName("DO-IDS-13. Операторы сравнения INTEGER/NUMBER корректно фильтруют ID")
    void numericComparisonOperatorsShouldMatchRegistryEndpoint(String operatorCode,
                                                                NumericThresholdStrategy thresholdStrategy) {
        getFlowWithRest()
                .step("Проверяем числовой оператор " + operatorCode, flow -> {
                    CandidateRule candidate = requireNumericCandidate(flow);
                    String threshold = thresholdStrategy.threshold(numericValue(candidate)).stripTrailingZeros().toPlainString();
                    var rules = andGroup(candidateRule(candidate, operatorCode, threshold));

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    DataOperatorAssertions.shouldHaveIdsExactly(response, DataOperatorAssertions.normalizedControlIds(control));
                })
                .run();
    }

    Stream<Arguments> numericOperatorCases() {
        return Stream.of(
                Arguments.of("more", (NumericThresholdStrategy) value -> value.subtract(BigDecimal.ONE)),
                Arguments.of("less", (NumericThresholdStrategy) value -> value.add(BigDecimal.ONE)),
                Arguments.of("more_equal", (NumericThresholdStrategy) value -> value),
                Arguments.of("less_equal", (NumericThresholdStrategy) value -> value)
        );
    }

    @ParameterizedTest(name = "DO-IDS-14.{index}. string operator={0}")
    @MethodSource("stringOperatorCases")
    @DisplayName("DO-IDS-14. Строковые операторы корректно фильтруют ID")
    void stringOperatorsShouldMatchRegistryEndpoint(String operatorCode, ValueStrategy valueStrategy) {
        getFlowWithRest()
                .step("Проверяем строковый оператор " + operatorCode, flow -> {
                    CandidateRule candidate = requireStringCandidate(flow);
                    var rules = andGroup(candidateRule(candidate, operatorCode, valueStrategy.values(candidate)));

                    var control = objects(flow, registryRequest(false, rules));
                    var response = objectIds(flow, idsRequest(false, rules));

                    DataOperatorAssertions.shouldHaveIdsExactly(response, DataOperatorAssertions.normalizedControlIds(control));
                })
                .run();
    }

    Stream<Arguments> stringOperatorCases() {
        return Stream.of(
                Arguments.of("like", (ValueStrategy) candidate -> new String[]{candidate.value()}),
                Arguments.of("not_like", (ValueStrategy) candidate -> new String[]{impossibleValue()}),
                Arguments.of("like_any", (ValueStrategy) candidate -> new String[]{impossibleValue(), candidate.value()}),
                Arguments.of("not_like_any", (ValueStrategy) candidate -> new String[]{impossibleValue(), impossibleValue()}),
                Arguments.of("like_all", (ValueStrategy) candidate -> new String[]{candidate.value()})
        );
    }

    @FunctionalInterface
    interface ValueStrategy {
        String[] values(CandidateRule candidate);
    }

    @FunctionalInterface
    interface NumericThresholdStrategy {
        BigDecimal threshold(BigDecimal actualValue);
    }
}
