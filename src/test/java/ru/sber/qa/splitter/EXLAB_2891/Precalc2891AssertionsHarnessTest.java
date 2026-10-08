package ru.sber.qa.splitter.EXLAB_2891;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;
import support.splitter.Precalc2891AssertionsHarnessFixtures;

import util.splittercheck.PrecalcResponseAssertions;
import util.validation.ExceptionChainAssertions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static steps.flow.splitter.mapper.MapperPrecalcSteps.*;

/** Deliberately damaged responses must fail; these checks do not execute the service. */
class Precalc2891AssertionsHarnessTest extends Precalc2891AssertionsHarnessFixtures {
    @Test void acceptsValidResponses() {
        PrecalcResponseAssertions.precalculated(preRequest(), precalc());
        var split = splitResponse();
        PrecalcResponseAssertions.splitEnvelope(splitRequest(), split, 123);
        assertMainA(split, 101);
        ((ArrayNode) split.at("/splittingResults/0/objectResults")).removeAll();
        PrecalcResponseAssertions.splitEnvelope(splitRequest(), split, 123);
        assertNoResult(split, "O1");
    }
    @ParameterizedTest(name = "Число: {0}")
    @ValueSource(strings = {"\"1\"", "1.0", "4294967297", "9223372036854775808", "null", "true"})
    void rejectsNumericCoercion(String value) throws Exception {
        ObjectNode r = JSON.createObjectNode(); r.set("n", JSON.readTree(value));
        assertThrows(AssertionError.class, () -> PrecalcResponseAssertions.numberEquals(r, "n", 1));
    }
    @ParameterizedTest(name = "Предрасчёт: {0}")
    @ValueSource(strings = {"no-counter", "missing-counter-field", "negative-counter", "error", "version-string", "wrong-version", "short-uuid", "no-uuid"})
    void rejectsDamagedPrecalc(String mutation) {
        var r = precalc();
        switch (mutation) {
            case "no-counter" -> r.remove("counter");
            case "missing-counter-field" -> ((ObjectNode) r.path("counter")).remove("objectsAdded");
            case "negative-counter" -> ((ObjectNode) r.path("counter")).put("objectsAdded", -1);
            case "error" -> r.put("errorCode", "EXCEPTION");
            case "version-string" -> r.put("soConfigVersion", "101");
            case "wrong-version" -> r.put("soConfigVersion", 102);
            case "short-uuid" -> r.put("responseId", "1-1-1-1-1");
            case "no-uuid" -> r.remove("responseId");
        }
        assertThrows(AssertionError.class, () -> PrecalcResponseAssertions.precalculated(preRequest(), r));
    }
    @ParameterizedTest(name = "Split envelope: {0}")
    @ValueSource(strings = {"missing-object", "extra-object", "duplicate-object", "duplicate-rule", "wrong-request", "wrong-splitting", "version-string", "missing-results"})
    void rejectsDamagedSplit(String mutation) {
        var r = splitResponse();
        var objects = (ArrayNode) r.path("splittingResults");
        var rules = (ArrayNode) objects.get(0).path("objectResults");
        switch (mutation) {
            case "missing-object" -> objects.removeAll();
            case "extra-object" -> objects.add(objects.get(0).<ObjectNode>deepCopy().put("objectId", "OTHER"));
            case "duplicate-object" -> objects.add(objects.get(0).deepCopy());
            case "duplicate-rule" -> rules.add(rules.get(0).deepCopy());
            case "wrong-request" -> r.put("requestId", "wrong");
            case "wrong-splitting" -> r.put("splittingId", "wrong");
            case "version-string" -> r.put("splittingConfigVersion", "123");
            case "missing-results" -> ((ObjectNode) objects.get(0)).remove("objectResults");
        }
        assertThrows(AssertionError.class, () -> PrecalcResponseAssertions.splitEnvelope(splitRequest(), r, 123));
    }
    @ParameterizedTest(name = "MAIN/ALL: {0}")
    @ValueSource(strings = {"missing-main", "missing-all", "duplicate-all-exp", "wrong-exp", "wrong-condition", "wrong-group",
            "wrong-final-group", "wrong-layer", "wrong-salt", "wrong-marker", "wrong-action", "wrong-type", "duplicate-param", "missing-param"})
    void rejectsDamagedBindings(String mutation) {
        var r = splitResponse();
        var rules = (ArrayNode) r.at("/splittingResults/0/objectResults");
        var exps = (ArrayNode) rules.get(1).path("resultExps");
        var exp = (ObjectNode) exps.get(0);
        var params = (ArrayNode) exp.path("groupResultParams");
        switch (mutation) {
            case "missing-main" -> rules.remove(0);
            case "missing-all" -> rules.remove(1);
            case "duplicate-all-exp" -> exps.add(exp.deepCopy());
            case "wrong-exp" -> exp.put("expId", 4294967397L);
            case "wrong-condition" -> exp.put("conditionId", "10");
            case "wrong-group" -> exp.put("expGroup", "B");
            case "wrong-final-group" -> exp.put("finalExpGroup", "B");
            case "wrong-layer" -> exp.put("layerId", 102);
            case "wrong-salt" -> exp.put("salt", "other");
            case "wrong-marker" -> ((ObjectNode) params.get(0)).putArray("paramValues").add("wrong");
            case "wrong-action" -> ((ObjectNode) params.get(1)).putArray("paramValues").add("1");
            case "wrong-type" -> ((ObjectNode) params.get(1)).put("dataType", "STRING");
            case "duplicate-param" -> params.add(params.get(0).deepCopy());
            case "missing-param" -> params.remove(1);
        }
        assertThrows(AssertionError.class, () -> assertMainA(r, 101));
    }
    @Test void exceptionCauseAllowsWrappersButDetectsLostCauseOrStack() {
        var root = new IllegalArgumentException("root");
        var original = new IllegalStateException("original", root);
        var snapshot = ExceptionChainAssertions.snapshot(original);
        ExceptionChainAssertions.preserves(new RuntimeException("wrapper", original), snapshot);
        ExceptionChainAssertions.preserves(new IllegalStateException("original", original), snapshot);
        assertThrows(AssertionError.class, () -> ExceptionChainAssertions.preserves(root, snapshot));
        original.setStackTrace(new StackTraceElement[0]);
        assertThrows(AssertionError.class, () -> ExceptionChainAssertions.preserves(original, snapshot));
    }
    @Test void startupInfrastructureFailureCannotPassAsValidation() {
        assertThrows(AssertionError.class, () -> ExceptionChainAssertions.startupFailure(
                new IllegalArgumentException("config", new NoSuchMethodException()), "java.lang.IllegalArgumentException"));
        assertThrows(AssertionError.class, () -> ExceptionChainAssertions.startupFailure(
                new NullPointerException("config"), "java.lang.IllegalArgumentException"));
        ExceptionChainAssertions.startupFailure(new IllegalArgumentException("Couldn't load rules config"), "java.lang.IllegalArgumentException");
    }
}
