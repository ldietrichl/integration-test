package ru.sber.qa.splitter.extension;


import java.util.stream.Stream;
import ru.sber.qa.services.kafka.KafkaService;

@org.junit.jupiter.api.Order(30)
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@org.junit.jupiter.api.DisplayName("Splitter extension: REACTIONS document matrix, allow=true, REST and Kafka")
public class ReactionsDocumentMatrixFlowTest extends AbstractSplitterDocumentMatrixFlowTest {
    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }
    static Stream<SplitterDocumentFixtures.Case> layerCases() { return SplitterDocumentFixtures.layerCases(); }

    @ru.sber.qa.allure.CriticalRegression
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @org.junit.jupiter.params.provider.MethodSource("layerCases")
    void documentLayers(SplitterDocumentFixtures.Case scenario, KafkaService kafka) { runDocumentCase(scenario, kafka); }
}
