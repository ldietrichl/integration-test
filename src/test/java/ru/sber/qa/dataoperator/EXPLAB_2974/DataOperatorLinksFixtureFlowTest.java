package ru.sber.qa.dataoperator.EXPLAB_2974;

import com.fasterxml.jackson.databind.ObjectMapper;
import config.environment.EnvironmentConfigWithRest;
import dto.dataoperator.v2.SplittingObjectsLinksRequestDto;
import flow.RestFlows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import request.dataoperator.v2.DataOperatorLinksTestDataFactory;
import util.dataoperator.LinksHttpEvidence;
import util.dataoperator.DataOperatorLinksAssertions.Pair;
import util.dataoperator.DataOperatorLinksAssertions.Selection;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static util.dataoperator.DataOperatorLinksAssertions.shouldHaveLinks;

/** Local fixture lifecycle belongs to test-fixtures.ps1 and always finishes in its finally block. */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@ResourceLock("data-operator-EXPLAB-2974")
@EnabledIfSystemProperty(named = "links.fixture.manifest", matches = ".+")
public class DataOperatorLinksFixtureFlowTest extends RestFlows {
    @RegisterExtension
    final LinksHttpEvidence evidence = new LinksHttpEvidence();

    @Test
    @DisplayName("EXPLAB-2974 SL-01. D1: точные связи и изоляция другой точки")
    void shouldSelectD1CreditObjects() throws Exception {
        var manifest = new ObjectMapper().readTree(Path.of(System.getProperty("links.fixture.manifest")).toFile());
        if (!"ready".equals(manifest.path("status").asText()))
            throw new IllegalStateException("Fixture setup must complete before the flow starts");
        String point = manifest.path("fixture").path("points").path("SP1").asText();
        var request = DataOperatorLinksTestDataFactory.baseline(point);
        getFlowWithRest()
                .step("Рассчитываем связи настоящим data-operator на подготовленном через REST наборе D1", flow -> {
                    var response = flow.restCustomSteps().dataOperatorV2Steps().getSplittingObjectLinks(request);
                    evidence.record("SL-01", "D1: A,B,D; X excluded", SplittingObjectsLinksRequestDto.toJson(request), false, response);
                    shouldHaveLinks(response, "INTEGER", "INTEGER", Map.of(101321L, Set.of(1)),
                            Map.of(new Selection(101321L, 1), Set.of(
                                    new Pair("1001", "101"), new Pair("1001", "102"), new Pair("1002", "101"))));
                }).run();
    }
}
