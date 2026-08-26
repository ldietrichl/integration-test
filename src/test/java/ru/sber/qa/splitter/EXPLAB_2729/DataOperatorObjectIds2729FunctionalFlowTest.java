package ru.sber.qa.splitter.EXPLAB_2729;

import config.environment.EnvironmentConfigWithRest;
import dto.dataoperator.request.SplittingObjectIdsRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import util.dataoperator.DataOperatorAssertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static request.dataoperator.DataOperatorTestDataFactory.emptyRules;
import static request.dataoperator.DataOperatorTestDataFactory.idsRequest;
import static request.dataoperator.DataOperatorTestDataFactory.impossibleValue;
import static request.dataoperator.DataOperatorTestDataFactory.registryRequest;
import static request.dataoperator.DataOperatorTestDataFactory.rule;
import static util.dataoperator.DataOperatorAssertions.ids;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-cache")
public class DataOperatorObjectIds2729FunctionalFlowTest extends AbstractDataOperator2729FlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-01. rules=[] возвращает все ID и совпадает с objectIds метода splitting-objects")
    void emptyRulesShouldReturnAllObjectIds() {
        AtomicReference<List<String>> expectedIds = new AtomicReference<>();

        getFlowWithRest()
                .step("Получаем контрольный objectIds через splitting-objects returnIds=true", flow -> {
                    var control = objects(flow, registryRequest(false, emptyRules()));
                    expectedIds.set(DataOperatorAssertions.normalizedControlIds(control));
                })
                .step("Получаем ids через новый endpoint", flow -> {
                    var response = objectIds(flow, idsRequest(false, emptyRules()));

                    DataOperatorAssertions.shouldHaveIdsEnvelope(response);
                    DataOperatorAssertions.shouldHaveIdsExactly(response, expectedIds.get());
                    DataOperatorAssertions.shouldHaveUniqueIds(response);
                    DataOperatorAssertions.shouldHaveAscendingIds(response);
                    DataOperatorAssertions.shouldNotExceedLimit(response, 20_000);
                    DataOperatorAssertions.shouldContainOnlyStrings(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-02. Отсутствующий parent эквивалентен parent=false")
    void omittedParentShouldBehaveAsFalse() {
        AtomicReference<List<String>> falseParentIds = new AtomicReference<>();

        getFlowWithRest()
                .step("Запрашиваем ids с parent=false", flow ->
                        falseParentIds.set(ids(objectIds(flow, idsRequest(false, emptyRules())))))
                .step("Запрашиваем ids без поля parent", flow -> {
                    SplittingObjectIdsRequestDto requestWithoutParent = idsRequest(null, emptyRules());
                    var response = objectIds(flow, requestWithoutParent);
                    Assertions.assertEquals(falseParentIds.get(), ids(response),
                            "parent=null/отсутствует должен обрабатываться как parent=false");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-03. Отсутствие совпадений возвращает 200 и ids=[]")
    void noMatchesShouldReturnEmptyIds() {
        String unknownParameter = "EXPLAB_2729_UNKNOWN_PARAMETER";
        String unknownValue = impossibleValue();

        getFlowWithRest()
                .step("Фильтруем по заведомо отсутствующему parameterCode", flow -> {
                    var request = idsRequest(false, List.of(List.of(
                            rule("STRING", unknownParameter, "equal", unknownValue))));
                    var response = objectIds(flow, request);
                    DataOperatorAssertions.shouldHaveIdsEnvelope(response);
                    DataOperatorAssertions.shouldHaveEmptyIds(response);
                })
                .run();
    }

    @Test
    @DisplayName("DO-IDS-04. Повторный запрос возвращает стабильный состав и порядок ids")
    void repeatedRequestShouldBeDeterministic() {
        AtomicReference<List<String>> firstResult = new AtomicReference<>();

        getFlowWithRest()
                .step("Выполняем первый запрос", flow ->
                        firstResult.set(ids(objectIds(flow, idsRequest(false, emptyRules())))))
                .step("Повторяем тот же запрос без изменения кэша", flow -> {
                    List<String> secondResult = ids(objectIds(flow, idsRequest(false, emptyRules())));
                    Assertions.assertEquals(firstResult.get(), secondResult,
                            "Одинаковые запросы к неизмененному кэшу должны возвращать одинаковый результат");
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DO-IDS-05. parent=true возвращает уникальный сортированный список не более 20000 ID")
    void parentTrueShouldReturnContractCompliantParentIds() {
        getFlowWithRest()
                .step("Получаем идентификаторы родительских объектов", flow -> {
                    var response = objectIds(flow, idsRequest(true, emptyRules()));
                    DataOperatorAssertions.shouldHaveIdsEnvelope(response);
                    DataOperatorAssertions.shouldHaveUniqueIds(response);
                    DataOperatorAssertions.shouldHaveAscendingIds(response);
                    DataOperatorAssertions.shouldNotExceedLimit(response, 20_000);
                    DataOperatorAssertions.shouldContainOnlyStrings(response);
                })
                .run();
    }

    @Test
    @DisplayName("DO-IDS-06. Успешный ответ содержит только ids без пагинации и content")
    void responseShouldNotContainRegistryEnvelope() {
        getFlowWithRest()
                .step("Проверяем минимальный контракт нового endpoint", flow -> {
                    var response = objectIds(flow, idsRequest(false, emptyRules()));
                    DataOperatorAssertions.shouldHaveIdsEnvelope(response);
                    DataOperatorAssertions.shouldContainOnlyIdsField(response);
                })
                .run();
    }
}
