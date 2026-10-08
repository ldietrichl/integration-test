package ru.sber.qa.splitter.EXPLAB_2690;

@org.junit.jupiter.api.Order(20)
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="explab2690.stand.application-flags.enabled", matches="true")
@org.junit.jupiter.api.DisplayName("EXPLAB-2690: mixed MAPPER objects, application flag=false")
@config.services.splitter.WorkedGroupPolicy(allowWithoutMain = false)
public class SplitterMapperMixedObjectsDenied2690FlowTest extends SplitterMapperMixedObjects2690FlowTest {
}
