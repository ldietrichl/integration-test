package ru.sber.qa.splitter.NewTest.document;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;

import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterDocument04AllResultFlowTest extends AbstractSplitterDocumentFlowTest {

    @CriticalRegression
    @Test
    @DisplayName("DOC-04-01. ALL содержит все связанные эксперименты при одном объекте")
    void allResultShouldContainAllLinkedExperiments() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(10401, "DOC-04-01-SALT-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))),
                experiment(10402, "DOC-04-01-SALT-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "3"))),
                experiment(10403, "DOC-04-01-SALT-C", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "2"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с тремя связанными экспериментами", flow -> loadConfigStep(flow, config))
                .step("Проверяем ruleCode=ALL", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertRuleResultSize(response, MATCHING_OBJECT_ID, "ALL", 3);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10401L, 10402L, 10403L);
                    assertMainExp(response, MATCHING_OBJECT_ID, 10403L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-04-02. REST ALL исключает связанный эксперимент без сработавшей группы")
    void allResultShouldNotContainExperimentWithoutHitGroup() {
        long version = SplitterVersionProvider.next();
        String splittingId = "123456";
        int spreadForNotHitExperiment = spread("DOC-04-02-SALT-B", splittingId);
        LoadConfigRequestDto config = config(version,
                experiment(10404, "DOC-04-02-SALT-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))),
                experiment(10405, "DOC-04-02-SALT-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(group("A", spreadForNotHitExperiment == 0 ? 1 : 0, spreadForNotHitExperiment == 0 ? 2 : spreadForNotHitExperiment, 1, "3"))));
        SplitRequestDto request = splitRequest(splittingId,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с одним полностью сработавшим и одним не сработавшим диапазоном", flow -> loadConfigStep(flow, config))
                .step("Проверяем очистку публичного ALL от эксперимента без сработавшей группы", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertRuleResultSize(response, MATCHING_OBJECT_ID, "ALL", 1);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10404L);
                    assertAllExpGroup(response, MATCHING_OBJECT_ID, 10404L, "A");
                    assertMainExp(response, MATCHING_OBJECT_ID, 10404L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("DOC-04-03. ALL по нескольким объектам содержит только эксперименты своего objectSelectConditions")
    void allResultShouldBeCalculatedPerObject() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version,
                experiment(10406, "DOC-04-03-SALT-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))),
                experiment(10407, "DOC-04-03-SALT-B", List.of(objectParamEqualsCondition(1, "id", "2", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "1"))),
                experiment(10408, "DOC-04-03-SALT-C", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")),
                        List.of(fullRangeGroup("A", 1, "2"))));
        SplitRequestDto request = splitRequest(DEFAULT_SPLITTING_ID,
                object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")),
                object(SECOND_OBJECT_ID, param("id", "2", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем конфиг с пересекающимися и непересекающимися condition", flow -> loadConfigStep(flow, config))
                .step("Проверяем независимое наполнение ALL для каждого объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, request));
                    assertConfigVersion(response, version);
                    assertAllRuleHasExpIdsExactly(response, MATCHING_OBJECT_ID, 10406L, 10408L);
                    assertAllRuleHasExpIdsExactly(response, SECOND_OBJECT_ID, 10407L);
                })
                .run();
    }
}
