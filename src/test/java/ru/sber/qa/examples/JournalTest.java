package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.environment.JournalEnvConfigStandart;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.flow.JournalFlow;
import ru.sber.qa.matchers.conditions.NumberConditions;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.services.journal.api.JournalSearchRequest;
import ru.sber.qa.services.pvm.api.unofficial.dto.InternalSourceKafkaTopicsFetchRq;

import java.util.List;

import static ru.sber.qa.matchers.JsonMatchers.*;

@Disabled("Демонстрационный класс, не предназначенный для запуска")
@Epic("journal")
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(JournalEnvConfigStandart.class)
public class JournalTest {

    @Test
    void exampleGetSingleValueFlowTest() {
        FlowRunner.flowRunnerFor(JournalFlowForTest.class)
                .step(flow -> {
                    flow
                            .pprb("onevalue")
                            .getSingleValue(InternalSourceKafkaTopicsFetchRq.FilterType.CONTAIN, "")
                            .should(
                                    evaluateJsonPathExpression("createdAt == 1740487001043"),
                                    haveJsonValue("createdAt", TextConditions.equalToText("1740487001043")),
                                    haveJsonValueEqualTo("createdAt", "1740487001043"),
                                    haveJsonValue("params.find{ it.name == 'DUL_NUMBER' }.value", TextConditions.equalToText("904974")),
                                    haveJsonValueEqualTo("params.find{ it.name == 'DUL_NUMBER' }.value", "904974")
                            );
                }).run();
    }

    @Test
    void exampleGetValuesFlowTest() {
        FlowRunner.flowRunnerFor(JournalFlowForTest.class)
                .step(flow -> {
                    flow
                            .ufs("onevalue")
                            .getValues(
                                    "",
                                    List.of(),
                                    List.of("WITHDRAWAL_MONEY"),
                                    List.of(new JournalSearchRequest.Condition()
                                            .withField("sessionId")
                                            .withClause(JournalSearchRequest.Clause.MUST)
                                            .withType(JournalSearchRequest.Type.MATCH)
                                            .withValue("ZUvOixlGRiGJOtYgfCRES8ClcW3FjPH2CY0G6pFdUkZrFl4dH02GYclxAYZ5j-qC")),
                                    List.of(),
                                    new JournalSearchRequest.Time(1752137340000L, -5,
                                            JournalSearchRequest.DurationUnit.MINUTES)
                                    , NumberConditions.greaterThan(3))
                            .filter("find{it}")
                            .should(evaluateJsonPathExpression("MESSAGE_DEMAND.contains('2095858893343684744')"));
                }).run();
    }

    static class JournalFlowForTest implements Flow, JournalFlow {
    }
}
