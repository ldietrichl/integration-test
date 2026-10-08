package ru.sber.qa.splitter.extension;


import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;

@Order(10)
@io.perfeccionista.framework.SetEnvironmentConfiguration(config.environment.EnvironmentConfigurationExample.class)
@ru.sber.qa.splitter.support.AnyConfigLoadMode
@DisplayName("Splitter extension: MAPPER REST/report exact groups and requestDt")
public class MapperKafkaReportContractFlowTest extends ReactionsKafkaReportContractFlowTest {
    @Override protected EndpointMode endpointMode() { return EndpointMode.MAPPER; }
}
