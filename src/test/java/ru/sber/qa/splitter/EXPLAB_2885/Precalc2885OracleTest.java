package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import support.splitter.Precalc2885OracleFixtures;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import static org.junit.jupiter.api.Assertions.*;
import static steps.flow.splitter.reactions.ReactionsPrecalcSteps.*;

/** Offline checks of the acceptance oracle; no calls to the stand. */
@Regression @AnyConfigLoadMode
@DisplayName("EXPLAB-2885. Самопроверка оракула без стенда")
public class Precalc2885OracleTest extends Precalc2885OracleFixtures {
    @Test void acceptsExactBinding() { assertMainA(response(), 101); }
    @Test void replacedExperimentRetainsOriginalLayerAndRejectsWrongLayer() {
        ObjectNode r = response();
        ObjectNode main = (ObjectNode) rule(object(r, "O1"), "MAIN").path("resultExps").get(0);
        main.put("expId", 202);
        assertMain(r, "O1", 202, "A", 10, "101-A-10", 101);
        main.put("layerId", 202);
        assertThrows(AssertionError.class, () -> assertMain(r, "O1", 202, "A", 10, "101-A-10", 101));
    }
    @Test void acceptsHttpValidationEnvelopeWithoutInventingSdkCode() {
        var body = JSON.createObjectNode().put("status", 400).put("error", "Bad Request")
                .put("path", "/api/v1/splitter/reactions/pre-calculate");
        assertEquals("REST_BEAN_VALIDATION", assertPrecalcValidationRejection(400, body));
    }
    @Test void acceptsSdkValidationButRejectsAnotherBusinessError() {
        var body = JSON.createObjectNode().put("errorCode", "VALIDATION_FAILED");
        assertEquals("SDK_VALIDATION", assertPrecalcValidationRejection(400, body));
        body.put("errorCode", "PRECALC_NOT_ENABLED");
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(400, body));
    }
    @Test void rejectsSuccessWrongEndpointAndMalformedValidationErrors() {
        var body = JSON.createObjectNode().put("status", 400).put("error", "Bad Request")
                .put("path", "/api/v1/splitter/reactions/pre-calculate");
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(200, body));
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(500, body));
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(400, JSON.createObjectNode()));
        body.put("path", "/api/v1/splitter/reactions/split");
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(400, body));
        body.put("path", "/api/v1/splitter/reactions/pre-calculate").putObject("counter");
        assertThrows(AssertionError.class, () -> assertPrecalcValidationRejection(400, body));
    }
    @Test void rejectsDuplicateObjectBeforeNormalization() {
        ObjectNode r = response(); r.withArray("splittingResults").add(r.path("splittingResults").get(0).deepCopy());
        assertThrows(AssertionError.class, () -> assertEquivalent(r, r));
    }
    @Test void rejectsWrongConditionAndMarker() {
        ObjectNode r = response();
        ((ObjectNode)rule(object(r, "O1"), "MAIN").path("resultExps").get(0)).put("conditionId", 20);
        assertThrows(AssertionError.class, () -> assertMainA(r, 101));
        ObjectNode m = response();
        ((ObjectNode)rule(object(m, "O1"), "MAIN").path("resultExps").get(0).path("groupResultParams").get(0)).putArray("paramValues").add("WRONG");
        assertThrows(AssertionError.class, () -> assertMainA(m, 101));
    }
    @Test void rejectsMissingMainAndUnexpectedPositiveResult() {
        ObjectNode r = response();
        assertThrows(AssertionError.class, () -> assertNoResult(r, "O1"));
        ((ObjectNode)r.path("splittingResults").get(0)).putArray("objectResults");
        assertThrows(AssertionError.class, () -> assertMainA(r, 101));
    }
    @Test void rejectsDuplicateMain() {
        ObjectNode r = response(); ObjectNode o = (ObjectNode)r.path("splittingResults").get(0);
        o.withArray("objectResults").add(o.path("objectResults").get(0).deepCopy());
        assertThrows(AssertionError.class, () -> assertMainA(r, 101));
    }
    @Test void countersMustBePresentEvenWhenExpectedZero() {
        assertThrows(AssertionError.class, () -> assertCounters(JSON.createObjectNode(), 0, 0, 0, 0, 0, 0, 0));
    }
    @Test void orderNormalizationDoesNotEraseDuplicates() {
        assertEquals(canonical(JSON.createArrayNode().add(2).add(1)), canonical(JSON.createArrayNode().add(1).add(2)));
        assertNotEquals(canonical(JSON.createArrayNode().add(1).add(1)), canonical(JSON.createArrayNode().add(1)));
    }
    @Test void rejectsGroupLeakedFromAnotherRequest() {
        ObjectNode r = response();
        ((ObjectNode)rule(object(r, "O1"), "MAIN").path("resultExps").get(0)).put("finalExpGroup", "B");
        assertThrows(AssertionError.class, () -> assertMainA(r, 101));
    }
}
