package util;

import com.fasterxml.jackson.databind.JsonNode;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.splittercheck.SplitterResponseReader;
import util.splittercheck.SplitterResponseSnapshot;

import java.util.Arrays;
import java.util.Objects;

import static org.apache.http.HttpStatus.SC_OK;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

public final class SplitterPrecalcAssertions {

    private SplitterPrecalcAssertions() {
    }

    public static ValidatableResponseWrapper shouldBe200(ValidatableResponseWrapper response) {
        return response.should(haveStatusCode(SC_OK));
    }

    public static SplitterResponseSnapshot snapshot(ValidatableResponseWrapper response) {
        return SplitterResponseReader.snapshot(response);
    }

    public static void shouldHaveJsonBody(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        assertCondition(
                snapshot.hasJsonBody(),
                "Ожидали непустой JSON body",
                snapshot
        );
    }

    public static void shouldHaveEmptyBody(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        assertCondition(
                snapshot.isEmptyBody(),
                "Ожидали пустой body",
                snapshot
        );
    }

    public static void shouldBeConfigLoaded(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки результата loadConfig");
        assertCondition(
                "LOADED".equals(root.path("result").asText(null)),
                "Ожидали result=LOADED",
                snapshot
        );
    }

    public static void shouldBeConfigLoadedOrLoadedWithPrecalc(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки результата loadConfig");
        String result = root.path("result").asText(null);
        boolean accepted = Arrays.asList("LOADED", "LOADED_WITH_PRECALC").contains(result);
        assertCondition(
                accepted,
                "Ожидали result=LOADED или result=LOADED_WITH_PRECALC, фактически=" + result,
                snapshot
        );
    }

    public static void shouldHaveResponseId(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки responseId");
        assertCondition(
                root.hasNonNull("responseId") && !root.path("responseId").asText().isBlank(),
                "Ожидали непустой responseId",
                snapshot
        );
    }

    public static void shouldHaveSoConfigVersion(ValidatableResponseWrapper response, int version) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки soConfigVersion");
        assertCondition(
                root.has("soConfigVersion") && root.path("soConfigVersion").asInt() == version,
                "Ожидали soConfigVersion=" + version,
                snapshot
        );
    }

    public static void shouldHaveNonEmptySplitEnvelope(ValidatableResponseWrapper response) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки split envelope");
        assertCondition(root.hasNonNull("requestId"), "Ожидали непустой requestId", snapshot);
        assertCondition(root.hasNonNull("splittingId"), "Ожидали непустой splittingId", snapshot);
        assertCondition(root.has("splittingResults") && root.path("splittingResults").isArray(),
                "Ожидали массив splittingResults", snapshot);
    }

    public static void shouldHaveSplittingResultsSize(ValidatableResponseWrapper response, int expectedSize) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки splittingResults");
        JsonNode splittingResults = root.path("splittingResults");
        assertCondition(splittingResults.isArray(), "Поле splittingResults должно быть массивом", snapshot);
        assertCondition(
                splittingResults.size() == expectedSize,
                "Ожидали splittingResults.size=" + expectedSize + ", фактически=" + splittingResults.size(),
                snapshot
        );
    }

    public static void shouldContainObjectIds(ValidatableResponseWrapper response, String... objectIds) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки objectId");
        JsonNode splittingResults = root.path("splittingResults");
        assertCondition(splittingResults.isArray(), "Поле splittingResults должно быть массивом", snapshot);

        for (String objectId : objectIds) {
            findObjectResult(root, objectId, snapshot);
        }
    }

    public static void shouldHaveSingleObjectWithEmptyResults(ValidatableResponseWrapper response, String objectId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки единственного объекта");
        getSingleObjectResult(root, objectId, snapshot);
        shouldHaveObjectWithEmptyResults(response, objectId);
    }

    public static void shouldHaveSingleObjectWithNonEmptyResults(ValidatableResponseWrapper response, String objectId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки единственного объекта");
        getSingleObjectResult(root, objectId, snapshot);
        shouldHaveObjectWithNonEmptyResults(response, objectId);
    }

    public static void shouldHaveObjectWithEmptyResults(ValidatableResponseWrapper response, String objectId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки business-result");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        JsonNode objectResults = objectNode.path("objectResults");
        boolean empty = objectResults.isMissingNode()
                || objectResults.isNull()
                || (objectResults.isArray() && objectResults.isEmpty());
        assertCondition(
                empty,
                "Ожидали пустой business-result для объекта " + objectId,
                snapshot
        );
    }

    public static void shouldHaveObjectWithNonEmptyResults(ValidatableResponseWrapper response, String objectId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки business-result");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        JsonNode objectResults = objectNode.path("objectResults");
        boolean nonEmpty = objectResults.isArray() && !objectResults.isEmpty();
        assertCondition(
                nonEmpty,
                "Ожидали непустой business-result для объекта " + objectId,
                snapshot
        );
    }

    public static void shouldContainObjectResultWithRuleCode(ValidatableResponseWrapper response,
                                                             String objectId,
                                                             String ruleCode) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки ruleCode");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        JsonNode objectResults = objectNode.path("objectResults");
        assertCondition(objectResults.isArray(), "Поле objectResults должно быть массивом", snapshot);

        for (JsonNode objectResult : objectResults) {
            if (Objects.equals(ruleCode, objectResult.path("ruleCode").asText(null))) {
                return;
            }
        }

        throw new AssertionError("Ожидали ruleCode=" + ruleCode + " у объекта " + objectId
                + "\nResponse body:\n" + snapshot.prettyBody());
    }

    public static void shouldContainExpId(ValidatableResponseWrapper response, String objectId, long expId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки expId");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        assertCondition(
                containsExpId(objectNode, expId),
                "Ожидали expId=" + expId + " у объекта " + objectId,
                snapshot
        );
    }

    public static void shouldNotContainExpId(ValidatableResponseWrapper response, String objectId, long expId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки expId");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        assertCondition(
                !containsExpId(objectNode, expId),
                "Не ожидали expId=" + expId + " у объекта " + objectId,
                snapshot
        );
    }

    public static void shouldContainConditionId(ValidatableResponseWrapper response, String objectId, long conditionId) {
        SplitterResponseSnapshot snapshot = snapshot(response);
        JsonNode root = readBody(snapshot, "Ожидали JSON body для проверки conditionId");
        JsonNode objectNode = findObjectResult(root, objectId, snapshot);
        assertCondition(
                containsConditionId(objectNode, conditionId),
                "Ожидали conditionId=" + conditionId + " у объекта " + objectId,
                snapshot
        );
    }

    private static JsonNode getSingleObjectResult(JsonNode root, String objectId, SplitterResponseSnapshot snapshot) {
        JsonNode splittingResults = root.path("splittingResults");
        assertCondition(splittingResults.isArray(), "Поле splittingResults должно быть массивом", snapshot);
        assertCondition(
                splittingResults.size() == 1,
                "Ожидали ровно один элемент в splittingResults, фактически=" + splittingResults.size(),
                snapshot
        );

        JsonNode first = splittingResults.get(0);
        String actualObjectId = first.path("objectId").asText(null);
        assertCondition(
                Objects.equals(actualObjectId, objectId),
                "Ожидали objectId=" + objectId + ", фактически=" + actualObjectId,
                snapshot
        );
        return first;
    }

    private static JsonNode findObjectResult(JsonNode root, String objectId, SplitterResponseSnapshot snapshot) {
        JsonNode splittingResults = root.path("splittingResults");
        assertCondition(splittingResults.isArray(), "Поле splittingResults должно быть массивом", snapshot);

        for (JsonNode result : splittingResults) {
            if (Objects.equals(objectId, result.path("objectId").asText(null))) {
                return result;
            }
        }

        throw new AssertionError("Не найден objectId=" + objectId + " в splittingResults"
                + "\nResponse body:\n" + snapshot.prettyBody());
    }

    private static boolean containsExpId(JsonNode objectNode, long expId) {
        JsonNode objectResults = objectNode.path("objectResults");
        if (!objectResults.isArray()) {
            return false;
        }

        for (JsonNode objectResult : objectResults) {
            JsonNode resultExps = objectResult.path("resultExps");
            if (!resultExps.isArray()) {
                continue;
            }
            for (JsonNode resultExp : resultExps) {
                if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean containsConditionId(JsonNode objectNode, long conditionId) {
        JsonNode objectResults = objectNode.path("objectResults");
        if (!objectResults.isArray()) {
            return false;
        }

        for (JsonNode objectResult : objectResults) {
            JsonNode resultExps = objectResult.path("resultExps");
            if (!resultExps.isArray()) {
                continue;
            }
            for (JsonNode resultExp : resultExps) {
                if (resultExp.path("conditionId").asLong(Long.MIN_VALUE) == conditionId) {
                    return true;
                }
            }
        }

        return false;
    }

    private static JsonNode readBody(SplitterResponseSnapshot snapshot, String messagePrefix) {
        return snapshot.requireJsonBody(messagePrefix);
    }

    private static void assertCondition(boolean condition, String message, SplitterResponseSnapshot snapshot) {
        if (!condition) {
            throw new AssertionError(message + "\nResponse body:\n" + snapshot.prettyBody());
        }
    }
}
