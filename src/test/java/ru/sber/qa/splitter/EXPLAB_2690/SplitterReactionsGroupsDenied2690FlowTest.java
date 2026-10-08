package ru.sber.qa.splitter.EXPLAB_2690;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;

@Order(40)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@DisplayName("EXPLAB-2690: REACTIONS groups, application flag=false")
@config.services.splitter.WorkedGroupPolicy(allowWithoutMain = false)
public class SplitterReactionsGroupsDenied2690FlowTest extends SplitterReactionsGroups2690FlowTest {
}
