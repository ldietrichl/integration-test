package ru.sber.qa.splitter.extension;


import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;

@Order(40)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@DisplayName("Splitter extension: six REACTIONS experiments, application flag=false")
public class ReactionsLayerPriorityWithoutMainDeniedFlowTest extends ReactionsLayerPriorityFlowTest {
    @Override protected boolean allowResultWithoutMain() { return false; }
}
