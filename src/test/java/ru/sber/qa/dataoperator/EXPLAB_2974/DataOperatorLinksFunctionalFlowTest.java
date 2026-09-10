package ru.sber.qa.dataoperator.EXPLAB_2974;

import config.environment.EnvironmentConfigWithRest;
import flow.RestFlows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import request.dataoperator.v2.DataOperatorLinksFunctionalCases;
import request.dataoperator.v2.DataOperatorLinksFunctionalCases.Scenario;
import util.dataoperator.LinksFixtureSession;
import util.dataoperator.LinksFixtureConfiguration;
import util.dataoperator.LinksFixtureRuntime;
import util.dataoperator.LinksHttpEvidence;
import java.util.stream.Stream;
import static util.dataoperator.DataOperatorLinksAssertions.shouldHaveLinks;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("data-operator-EXPLAB-2974")
public class DataOperatorLinksFunctionalFlowTest extends RestFlows {
    @RegisterExtension final LinksHttpEvidence evidence=new LinksHttpEvidence();

    @BeforeAll
    static void requireFixtureConfiguration() throws Exception {
        var result=new LinksFixtureRuntime(new LinksFixtureConfiguration()).probe();
        io.qameta.allure.Allure.addAttachment("Ignite fixture compatibility probe", "application/json", result.toString(), ".json");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("functionalCases")
    @DisplayName("EXPLAB-2974. Функциональный расчёт связей на собственных REST-данных")
    void shouldCalculateExactLinks(Scenario scenario){
        getFlowWithRest().step("Подготовка, расчёт и очистка: "+scenario,flow->{
            try(var fixture=new LinksFixtureSession(scenario.dataSet())){
                var steps=flow.restCustomSteps().dataOperatorV2Steps();
                fixture.prepare(steps);
                String body=scenario.request(fixture.point()).toString();
                var response=steps.getSplittingObjectLinks(body);
                evidence.record(scenario.id(),scenario.name(),body,false,response);
                shouldHaveLinks(response,scenario.objectType(),scenario.parentType(),scenario.conditions(),scenario.expected());
            } catch (Exception error) {
                throw new IllegalStateException("Fixture lifecycle failed for " + scenario, error);
            }
        }).run();
    }
    static Stream<Scenario> functionalCases(){return DataOperatorLinksFunctionalCases.all().stream();}
}
