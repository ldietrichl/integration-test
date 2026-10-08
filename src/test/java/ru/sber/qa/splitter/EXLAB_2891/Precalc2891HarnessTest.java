package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import steps.flow.splitter.mapper.MapperDuplicateSteps;
import support.splitter.Precalc2891HarnessFixtures;
import infrastructure.kubernetes.WorkloadScenarioEvidence;

import config.services.splitter.MapperPrecalcProfile;

import com.fasterxml.jackson.databind.node.ObjectNode;
import infrastructure.kubernetes.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

/** Local checks of ticket routing/assertions; these are not evidence of a service fix. */
@DisplayName("EXLAB-2891. Проверки тестовой инфраструктуры без стенда")
public class Precalc2891HarnessTest extends Precalc2891HarnessFixtures {
    @Test void rejectsUnexpectedExceptionAndSuccess() {
        assertThrows(AssertionError.class, () -> check(400, body("EXCEPTION", "paramCode")));
        assertThrows(AssertionError.class, () -> check(200, body("VALIDATION_FAILED", "paramCode")));
    }
    @Test void optionalDetailsAndRequestCorrelationFollowSpecification() {
        check(400, body("VALIDATION_FAILED", "splittingObjects[1].objectParams[1].paramCode"));
        check(400, body("VALIDATION_FAILED", "unused").putNull("errorDetails"));
        ObjectNode structured = body("VALIDATION_FAILED", "unused");
        structured.putObject("errorDetails").put("paramCode", "required");
        check(400, structured);
        assertThrows(AssertionError.class, () -> check(400, body("VALIDATION_FAILED", "paramCode").put("requestId", "wrong")));
    }
    @Test void springEnvelopeMustMatchEndpoint() {
        ObjectNode response = dto.splitter.precalc.MapperPrecalcRequests.JSON.createObjectNode().put("status",400).put("error","Bad Request")
                .put("path","/api/v1/splitter/pre-calculate");
        check(400, response);
        response.put("path","/api/v1/splitter/reactions/pre-calculate");
        assertThrows(AssertionError.class, () -> check(400, response));
    }
    @Test void rejectsErrorWithSuccessPayload() {
        ObjectNode response = body("VALIDATION_FAILED", "paramCode");
        response.putObject("counter");
        assertThrows(AssertionError.class, () -> check(400, response));
    }
    @Test void mapperPlanUsesMapperOnlyAndEnablesPrecalc() {
        var plan = MapperPrecalcProfile.plan("", "rest", k->null, k->null, k->null);
        assertEquals("splitter-mapper", plan.target().workload());
        assertEquals("splitter-mapper-service", plan.deployment());
        assertEquals("splitter-mapper-service-lib", plan.rulesMap());
        assertEquals("splitter-rules-mapper.yml", plan.rulesKey());
        assertEquals("true", plan.environment().get("SPLITTER_PRELIMINARY_CALCULATION_ENABLED"));
        assertEquals("true", plan.environment().get("SPLITTER_API_CONFIG_LOAD"));
        assertTrue(MapperPrecalcProfile.RULES.contains("proc-code: mapperFinalExp"));
        assertFalse(MapperPrecalcProfile.RULES.contains("finalExpByLayerAndId"));
    }
    @Test void kafkaModeOnlyChangesConfigTransport() {
        var plan = MapperPrecalcProfile.plan("", "kafka", k->null, k->null, k->null);
        assertEquals("false", plan.environment().get("SPLITTER_API_CONFIG_LOAD"));
        assertEquals("splitter-mapper", plan.target().workload());
    }
    @Test void explicitNamesWinOverLegacyDefaults() {
        var values = Map.of("exlab2891.mapper.stand.service", "selected", "explab2690.stand.service", "legacy");
        var plan = MapperPrecalcProfile.plan("", "rest", values::get, k->null, k->null);
        assertEquals("selected", plan.deployment());
    }
    @Test void desiredStateUsesActualEnvironmentReferences() {
        var session = mock(KubernetesWorkloadControl.class);
        var plan = MapperPrecalcProfile.plan("", "rest", k->null, k->null, k->null);
        when(session.environmentState(plan.environment())).thenReturn(List.of(new ConfigMapState("actual", Map.of("flag","true"))));
        var states = MapperPrecalcProfile.desiredState(plan,session);
        assertEquals(2, states.size());
        assertEquals("actual", states.get(0).name());
        assertTrue(states.get(1).data().containsKey("splitter-rules-mapper.yml"));
        verify(session).environmentState(plan.environment());
    }
    @Test void evidenceUsesCommonWindowAndFailureFlag() {
        var session = mock(KubernetesWorkloadControl.class);
        var evidence = new WorkloadScenarioEvidence("EXLAB-2891", "test");
        evidence.bind(session);
        evidence.configMaps(List.of(new ConfigMapState("actual", Map.of("flag","true"))));
        evidence.prepared(); evidence.operationStarted(); evidence.operationFinished();
        evidence.failed(new AssertionError("expected")); evidence.finish();
        var order = inOrder(session);
        order.verify(session).beginScenarioEvidence(any());
        order.verify(session).captureScenarioConfigMaps(eq("до подготовки"), anyList());
        order.verify(session).captureScenarioConfigMaps(eq("перед операцией"), anyList());
        order.verify(session).startScenarioOperation(any());
        order.verify(session).finishScenarioOperation(any());
        order.verify(session).captureScenarioConfigMaps(eq("после операции"), anyList());
        order.verify(session).finishScenarioEvidence("EXLAB-2891 after scenario",true);
    }
    @Test void mapperFixtureHasMainActionTypeAndOriginalIncidentValues() {
        var exp = dto.splitter.precalc.MapperPrecalcRequests.exp(101,1);
        assertEquals("actionType", exp.path("groups").get(0).path("splittingResults").get(0).path("resultParams").get(1).path("paramCode").asText());
        var object = MapperDuplicateSteps.incidentObject();
        assertEquals("1646160",object.path("uniqueConfigurationId").asText());
        assertEquals(10,object.path("objectParams").size());
    }
}
