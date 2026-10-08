package scheduler.localchecks;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.HeaderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.FilterableRequestSpecification;
import steps.rest.scheduler.SchedulerRequestDiagnostics;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory request-contract checks only. No HTTP dispatch, sockets, framework environment,
 * Kubernetes clients, test resources or corporate scenario execution.
 */
public final class SchedulerRequestContractChecks {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> VALID = Map.of("page", 0, "size", 1);
    private static int passed;

    private SchedulerRequestContractChecks() { }

    public static void main(String[] args) throws Exception {
        check("valid object", () -> allowed(request(VALID), VALID));
        check("valid serialized JSON", () -> allowed(request(json(VALID)), VALID));
        check("valid UTF-8 bytes", () -> allowed(request(json(VALID).getBytes(StandardCharsets.UTF_8)), VALID));
        check("charset does not change JSON media type", () -> {
            var request = request(VALID);
            request.contentType("application/json; charset=UTF-8");
            allowed(request, VALID);
        });
        check("case-insensitive Accept", () -> {
            var request = request(VALID);
            request.accept("APPLICATION/JSON");
            allowed(request, VALID);
        });
        check("missing page fixture remains missing", () -> {
            Map<String, Object> fixture = Map.of("size", 1);
            var evidence = allowed(request(fixture), fixture);
            equal(Map.of("present", false, "type", "ABSENT"), evidence.get("page"));
            equal(Map.of("size", 1), fixture);
        });
        check("serialized missing page fixture", () -> {
            Map<String, Object> fixture = Map.of("size", 1);
            allowed(request(json(fixture)), fixture);
        });
        check("string page fixture remains a string", () -> {
            Map<String, Object> fixture = Map.of("page", "abc", "size", 1);
            var evidence = allowed(request(fixture), fixture);
            equal(Map.of("present", true, "type", "STRING"), evidence.get("page"));
            equal("abc", fixture.get("page"));
        });
        check("serialized string page fixture", () -> {
            Map<String, Object> fixture = Map.of("page", "abc", "size", 1);
            allowed(request(json(fixture)), fixture);
        });
        check("REST Assured enum aliases reproduce the old failure", () -> {
            var request = request(VALID);
            request.accept(ContentType.JSON);
            var evidence = blocked(request, VALID, "ACCEPT_MISMATCH");
            equal(List.of("ACCEPT_MISMATCH"), evidence.get("failureReasons"));
            equal(true, evidence.get("matchesExpectedFixture"));
        });
        check("missing Accept", () -> {
            var request = request(VALID);
            request.removeHeader("Accept");
            blocked(request, VALID, "ACCEPT_HEADER_COUNT");
        });
        for (String accept : List.of("text/plain", "*/*", "application/json, text/plain", "application/json;q=1")) {
            check("non-canonical Accept " + accept, () -> {
                var request = request(VALID);
                request.accept(accept);
                blocked(request, VALID, "ACCEPT_MISMATCH");
            });
        }
        check("duplicate Accept", () -> {
            var request = request(VALID);
            request.config(RestAssuredConfig.config().headerConfig(
                    HeaderConfig.headerConfig().mergeHeadersWithName("Accept")));
            request.header("Accept", "application/json");
            var evidence = blocked(request, VALID, "ACCEPT_HEADER_COUNT");
            equal(2L, evidence.get("acceptHeaderCount"));
        });
        check("wrong Content-Type", () -> {
            var request = request(json(VALID));
            request.contentType("text/plain");
            blocked(request, VALID, "CONTENT_TYPE_MISMATCH");
        });
        check("missing Content-Type", () -> {
            var request = request(json(VALID));
            request.removeHeader("Content-Type");
            blocked(request, VALID, "CONTENT_TYPE_HEADER_COUNT");
        });
        check("duplicate Content-Type", () -> {
            var request = request(json(VALID));
            request.config(RestAssuredConfig.config().headerConfig(
                    HeaderConfig.headerConfig().mergeHeadersWithName("Content-Type")));
            request.header("Content-Type", "application/json");
            var evidence = blocked(request, VALID, "CONTENT_TYPE_HEADER_COUNT");
            equal(2L, evidence.get("contentTypeHeaderCount"));
        });
        check("unreadable JSON", () -> blocked(request("{"), VALID, "JSON_UNREADABLE"));
        check("trailing JSON rejected", () -> blocked(request(json(VALID) + "{}"), VALID, "JSON_UNREADABLE"));
        check("array is not a registry JSON object", () ->
                blocked(request("[1]"), List.of(1), "JSON_ROOT_NOT_OBJECT"));
        check("JSON null is not a registry JSON object", () ->
                blocked(request("null"), VALID, "JSON_ROOT_NOT_OBJECT"));
        check("absent body", () -> blocked(request(null), VALID, "JSON_UNREADABLE"));
        check("oversized text bounded", () -> blocked(request(" ".repeat(65537)), VALID, "JSON_UNREADABLE"));
        check("oversized bytes bounded", () -> blocked(request(new byte[65537]), VALID, "JSON_UNREADABLE"));
        check("changed fixture rejected", () ->
                blocked(request(Map.of("page", 1, "size", 1)), VALID, "FIXTURE_MISMATCH"));
        check("non-registry requests not guarded", () -> {
            var request = request("intentionally not JSON");
            request.accept("text/plain").contentType("text/plain");
            var evidence = SchedulerRequestDiagnostics.inspect(request, null);
            equal(false, evidence.get("registryGuardEnabled"));
            equal("NOT_REQUESTED", evidence.get("bodyInspection"));
            SchedulerRequestDiagnostics.requireRegistryContract(evidence);
        });
        check("body values omitted from metadata", () -> {
            String sentinel = "PRIVATE_VALUE_SENTINEL";
            Map<String, Object> fixture = Map.of("page", sentinel, "size", 1);
            var evidence = allowed(request(fixture), fixture);
            require(!JSON.writeValueAsString(evidence).contains(sentinel), "Body value leaked");
        });
        check("arbitrary Accept is not printed", () -> {
            String sentinel = "HEADER_VALUE_SENTINEL";
            var request = request(VALID);
            request.accept(sentinel);
            var evidence = blocked(request, VALID, "ACCEPT_MISMATCH");
            require(!JSON.writeValueAsString(evidence).contains(sentinel), "Header value leaked");
        });
        check("only allowlisted failure codes appear in exceptions", () -> {
            Map<String, Object> evidence = Map.of("registryGuardEnabled", true,
                    "requestContractSatisfied", false, "failureReasons", List.of("SECRET_TEXT_SENTINEL"));
            try {
                SchedulerRequestDiagnostics.requireRegistryContract(evidence);
                throw new AssertionError("Guard unexpectedly allowed an invalid request");
            } catch (SchedulerRequestDiagnostics.RequestContractException expected) {
                require(expected.getMessage().contains("UNSPECIFIED_LOCAL_CONTRACT"), "Missing fallback reason");
                require(!expected.getMessage().contains("SECRET_TEXT_SENTINEL"), "Unsafe reason leaked");
            }
        });
        System.out.println("LocalGuardChecksPassed=" + passed);
        System.out.println("NetworkDispatches=0");
        System.out.println("CorporateTestsExecuted=0");
    }

    private static FilterableRequestSpecification request(Object body) {
        var request = RestAssured.given().config(RestAssuredConfig.config().headerConfig(
                HeaderConfig.headerConfig().overwriteHeadersWithName("Content-Type", "Accept")))
                .contentType("application/json").accept("application/json");
        if (body instanceof byte[] bytes) request.body(bytes);
        else if (body != null) request.body(body);
        return (FilterableRequestSpecification) request;
    }

    private static Map<String, Object> allowed(FilterableRequestSpecification request, Object fixture) {
        var evidence = SchedulerRequestDiagnostics.inspect(request, fixture);
        equal(true, evidence.get("requestContractSatisfied"));
        equal(List.of(), evidence.get("failureReasons"));
        equal("CONTINUE_FILTER_CHAIN", evidence.get("dispatchDecision"));
        equal(1L, evidence.get("acceptHeaderCount"));
        SchedulerRequestDiagnostics.requireRegistryContract(evidence);
        return evidence;
    }

    private static Map<String, Object> blocked(FilterableRequestSpecification request, Object fixture, String reason) {
        var evidence = SchedulerRequestDiagnostics.inspect(request, fixture);
        equal(false, evidence.get("requestContractSatisfied"));
        equal("BLOCKED_LOCAL_CONTRACT", evidence.get("dispatchDecision"));
        require(((List<?>) evidence.get("failureReasons")).contains(reason), "Expected safe failure code " + reason);
        try {
            SchedulerRequestDiagnostics.requireRegistryContract(evidence);
            throw new AssertionError("Guard unexpectedly allowed an invalid request");
        } catch (SchedulerRequestDiagnostics.RequestContractException expected) {
            require(expected.getMessage().contains(reason), "Exception must identify the safe failure code");
        }
        return evidence;
    }

    private static String json(Object value) throws Exception { return JSON.writeValueAsString(value); }
    private static void equal(Object expected, Object actual) {
        require(Objects.equals(expected, actual), "Local request-contract assertion failed");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @FunctionalInterface private interface Check { void run() throws Exception; }
    private static void check(String name, Check action) throws Exception {
        try { action.run(); }
        catch (Throwable failure) { throw new AssertionError("Local guard check failed: " + name, failure); }
        passed++;
        System.out.println("PASS " + name);
    }
}
