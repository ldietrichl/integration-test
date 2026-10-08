package ru.sber.qa.splitter.extension;


@org.junit.jupiter.api.Order(10)
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@org.junit.jupiter.api.DisplayName("Splitter extension: MAPPER document matrix, allow=true, REST and Kafka")
public class MapperDocumentMatrixFlowTest extends AbstractSplitterDocumentMatrixFlowTest {
    static java.util.stream.Stream<SplitterDocumentFixtures.Case> filteredCases() {
        return SplitterDocumentFixtures.filteredCases();
    }

    @ru.sber.qa.allure.CriticalRegression
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("filteredCases")
    void filteredAction(SplitterDocumentFixtures.Case scenario, ru.sber.qa.services.kafka.KafkaService kafka) {
        runDocumentCase(scenario, kafka);
    }
}
