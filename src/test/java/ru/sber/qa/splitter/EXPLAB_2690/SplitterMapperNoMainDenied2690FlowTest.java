package ru.sber.qa.splitter.EXPLAB_2690;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;

@Order(20)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@DisplayName("EXPLAB-2690: MAPPER no-MAIN, application flag=false")
@config.services.splitter.WorkedGroupPolicy(allowWithoutMain = false)
public class SplitterMapperNoMainDenied2690FlowTest extends SplitterMapperNoMain2690FlowTest {
}
