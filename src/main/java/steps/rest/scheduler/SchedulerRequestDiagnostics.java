package steps.rest.scheduler;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.specification.FilterableRequestSpecification;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Safe request metadata at the REST filter boundary, never a credential/body dump. */
public final class SchedulerRequestDiagnostics {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BODY_BYTES = 65536;

    private enum FailureReason {
        CONTENT_TYPE_MISMATCH, CONTENT_TYPE_HEADER_COUNT,
        ACCEPT_MISMATCH, ACCEPT_HEADER_COUNT,
        JSON_UNREADABLE, JSON_ROOT_NOT_OBJECT, FIXTURE_MISMATCH
    }

    private SchedulerRequestDiagnostics() { }

    public static final class RequestContractException extends IllegalStateException {
        private RequestContractException(String reason) {
            super("SCHEDULER_REQUEST_CONTRACT: " + reason + "; request was not dispatched by this filter");
        }
    }

    public static Map<String, Object> inspect(FilterableRequestSpecification request, Object expectedFixture) {
        Map<String, Object> result = new LinkedHashMap<>();
        String media = mediaType(request.getContentType());
        String accept = safeAccept(request.getHeaders().getValue("Accept"));
        long contentHeaders = request.getHeaders().asList().stream()
                .filter(h -> "Content-Type".equalsIgnoreCase(h.getName())).count();
        long acceptHeaders = request.getHeaders().asList().stream()
                .filter(h -> "Accept".equalsIgnoreCase(h.getName())).count();
        result.put("scope", "REQUEST_FILTER_BOUNDARY_NOT_WIRE_CAPTURE");
        result.put("contentType", media);
        result.put("contentTypeHeaderCount", contentHeaders);
        result.put("accept", accept);
        result.put("acceptHeaderCount", acceptHeaders);
        result.put("bodyValuesOmitted", true);
        result.put("registryGuardEnabled", expectedFixture != null);
        // Other operations can intentionally exercise invalid payloads. Do not normalize them.
        if (expectedFixture == null) {
            result.put("bodyInspection", "NOT_REQUESTED");
            return result;
        }
        Object body = request.getBody();
        JsonNode actual = null;
        result.put("bodyRepresentation", body == null ? "ABSENT" :
                body instanceof byte[] ? "BYTES" : body instanceof CharSequence ? "TEXT" : "OBJECT");
        try {
            if (body instanceof byte[] bytes) {
                if (bytes.length <= MAX_BODY_BYTES)
                    actual = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
            } else if (body instanceof CharSequence text) {
                if (text.length() <= MAX_BODY_BYTES)
                    actual = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text.toString());
            } else if (body != null) {
                actual = JSON.valueToTree(body);
            }
        } catch (Exception ignored) {
            // Parser messages may quote body data or credentials. Keep only a boolean.
        }
        boolean readable = actual != null && !actual.isMissingNode();
        result.put("jsonReadableAtFilter", readable);
        result.put("rootType", readable ? actual.getNodeType().name() : "UNREADABLE");
        if (readable && actual.isObject()) {
            result.put("fieldCount", actual.size());
            result.put("page", fieldShape(actual, "page"));
            result.put("size", fieldShape(actual, "size"));
        }
        boolean matches = false;
        try { matches = readable && actual.equals(JSON.valueToTree(expectedFixture)); }
        catch (RuntimeException ignored) { }
        result.put("matchesExpectedFixture", matches);
        List<String> reasons = new ArrayList<>();
        if (!"application/json".equals(media)) reasons.add(FailureReason.CONTENT_TYPE_MISMATCH.name());
        if (contentHeaders != 1) reasons.add(FailureReason.CONTENT_TYPE_HEADER_COUNT.name());
        if (!"application/json".equals(accept)) reasons.add(FailureReason.ACCEPT_MISMATCH.name());
        if (acceptHeaders != 1) reasons.add(FailureReason.ACCEPT_HEADER_COUNT.name());
        if (!readable) reasons.add(FailureReason.JSON_UNREADABLE.name());
        else {
            if (!actual.isObject()) reasons.add(FailureReason.JSON_ROOT_NOT_OBJECT.name());
            if (!matches) reasons.add(FailureReason.FIXTURE_MISMATCH.name());
        }
        boolean contract = reasons.isEmpty();
        result.put("failureReasons", List.copyOf(reasons));
        result.put("requestContractSatisfied", contract);
        result.put("dispatchDecision", contract ? "CONTINUE_FILTER_CHAIN" : "BLOCKED_LOCAL_CONTRACT");
        return result;
    }

    public static void requireRegistryContract(Map<String, Object> evidence) {
        if (Boolean.TRUE.equals(evidence.get("registryGuardEnabled"))
                && !Boolean.TRUE.equals(evidence.get("requestContractSatisfied"))) {
            List<String> safeReasons = new ArrayList<>();
            // Only fixed codes are allowed into exception messages, never caller-supplied text.
            if (evidence.get("failureReasons") instanceof List<?> reasons) {
                for (FailureReason reason : FailureReason.values()) {
                    if (reasons.contains(reason.name())) safeReasons.add(reason.name());
                }
            }
            throw new RequestContractException(safeReasons.isEmpty()
                    ? "UNSPECIFIED_LOCAL_CONTRACT" : String.join(",", safeReasons));
        }
    }

    private static Map<String, Object> fieldShape(JsonNode object, String name) {
        JsonNode value = object.get(name);
        return Map.of("present", value != null, "type", value == null ? "ABSENT" : value.getNodeType().name());
    }

    private static String mediaType(String value) {
        if (value == null || value.isBlank()) return "ABSENT";
        String type = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.length() <= 100 && type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
                ? type : "OTHER_OR_INVALID";
    }

    private static String safeAccept(String value) {
        if (value == null || value.isBlank()) return "ABSENT";
        // The diagnostic client deliberately uses one literal media type, not ContentType.JSON aliases.
        // This is a local fixture contract, not a general HTTP Accept validator.
        return value.trim().equalsIgnoreCase("application/json") ? "application/json" : "OTHER";
    }
}
