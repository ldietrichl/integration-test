package ru.sber.qa.splitter.extension;


@org.junit.jupiter.api.Order(40)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@org.junit.jupiter.api.DisplayName("Splitter extension: REACTIONS document matrix, allow=false, REST and Kafka")
public class ReactionsDocumentMatrixWithoutMainDeniedFlowTest extends ReactionsDocumentMatrixFlowTest {
    @Override protected boolean allowResultWithoutMain() { return false; }
}
