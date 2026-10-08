package ru.sber.qa.splitter.EXPLAB_2690;

@org.junit.jupiter.api.Order(40)
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@org.junit.jupiter.api.DisplayName("EXPLAB-2690: no REACTIONS alternatives, application flag=false")
@config.services.splitter.WorkedGroupPolicy(allowWithoutMain = false)
public class SplitterReactionsNoAlternativeDenied2690FlowTest extends SplitterReactionsNoAlternative2690FlowTest {
}
