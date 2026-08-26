package util;

import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.matchers.ResponseBodyJsonPathExpressionEvaluateMatcher;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_OK;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

/**
 * Совместимая версия assertions для splitter.
 *
 * Что сохраняет:
 * - весь текущий рабочий static API
 * - текущие helper path-методы
 *
 * Что добавляет:
 * - matcher-style методы для использования внутри response.should(...)
 *
 * Это позволяет:
 * 1) не ломать существующие тесты вида
 *    SplitterAssertions.shouldHaveConfigVersion(response, version);
 * 2) использовать новый стиль
 *    response.should(assertions.shouldHaveConfigVersion(version));
 */
public final class SplitterAssertions {

    public SplitterAssertions() {
    }

    /* =========================
       Текущий рабочий static API
       ========================= */

    public static ValidatableResponseWrapper shouldBe200(ValidatableResponseWrapper response) {
        return response.should(haveStatusCode(SC_OK));
    }

    public static ValidatableResponseWrapper shouldBe400(ValidatableResponseWrapper response) {
        return response.should(haveStatusCode(SC_BAD_REQUEST));
    }

    public static void shouldHaveConfigVersion(ValidatableResponseWrapper response, long version) {
        response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == " + version));
    }

    public static void shouldHaveSameConfigVersion(ValidatableResponseWrapper response, long version) {
        shouldHaveConfigVersion(response, version);
    }

    public static void shouldHaveMainExpId(ValidatableResponseWrapper response, String objectId, long expId) {
        response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainExpIdPath(objectId) + " == " + expId));
    }

    public static void shouldHaveFilteredValue(ValidatableResponseWrapper response, String objectId, String expected) {
        response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression(filteredFlagPath(objectId) + " == '" + expected + "'"));
    }

    public static void shouldContainMainAndAll(ValidatableResponseWrapper response, String objectId) {
        response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath(objectId) + " != null"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath(objectId, "MAIN") + " != null"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath(objectId, "ALL") + " != null")
        );
    }

    public static void shouldHaveEmptyObjectResults(ValidatableResponseWrapper response, String objectId) {
        response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectId == '" + objectId + "'"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults[0].objectResults.size() == 0")
        );
    }

    public static void shouldBeBadRequestError(ValidatableResponseWrapper response) {
        response.should(
                haveStatusCode(SC_BAD_REQUEST),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("status == 400"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("error == 'Bad Request'")
        );
    }

    /* =========================
       Новый matcher-style API
       ========================= */

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveConfigVersion(long version) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingConfigVersion == " + version);
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveSameConfigVersion(long version) {
        return shouldHaveConfigVersion(version);
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveMainExpId(String objectId, long expId) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(mainExpIdPath(objectId) + " == " + expId);
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveFilteredValue(String objectId, String expected) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(filteredFlagPath(objectId) + " == '" + expected + "'");
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldContainObject(String objectId) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectPath(objectId) + " != null");
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldContainMain(String objectId) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath(objectId, "MAIN") + " != null");
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldContainAll(String objectId) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(objectResultPath(objectId, "ALL") + " != null");
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveObjectResultsSize(String objectId, int expectedSize) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                objectPath(objectId) + ".objectResults.size() == " + expectedSize
        );
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveSplittingResultsSize(int expectedSize) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == " + expectedSize);
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldHaveObjectIdAtIndex(int index, String objectId) {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression(
                "splittingResults[" + index + "].objectId == '" + objectId + "'"
        );
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldBeBadRequestStatusBody() {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression("status == 400");
    }

    public ResponseBodyJsonPathExpressionEvaluateMatcher shouldBeBadRequestErrorBody() {
        return (ResponseBodyJsonPathExpressionEvaluateMatcher) RestMatchers.haveBodyWithEvaluatableJsonPathExpression("error == 'Bad Request'");
    }

    /* =========================
       Common jsonPath helpers
       ========================= */

    public static String objectPath(String objectId) {
        return "splittingResults.find { it.objectId == '" + objectId + "' }";
    }

    public static String objectResultPath(String objectId, String ruleCode) {
        return objectPath(objectId) + ".objectResults.find { it.ruleCode == '" + ruleCode + "' }";
    }

    public static String mainExpIdPath(String objectId) {
        return objectResultPath(objectId, "MAIN") + ".resultExps[0].expId";
    }

    public static String filteredFlagPath(String objectId) {
        return objectPath(objectId) + ".objectFlags.find { it.code == 'filtered' }.value";
    }
}
