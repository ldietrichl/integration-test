package ru.sber.qa.splitter.EXPLAB_2398;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO ожиданий для структурированного monitoring-сообщения метода calculatePreliminary.
 */
final class PrecalcMonitoring2398EventExpectation {

    private final String requestId;
    private final String result;
    private final long soConfigVersion;
    private final Map<String, Long> counters = new LinkedHashMap<>();

    private PrecalcMonitoring2398EventExpectation(String requestId, String result, long soConfigVersion) {
        this.requestId = requestId;
        this.result = result;
        this.soConfigVersion = soConfigVersion;
    }

    static PrecalcMonitoring2398EventExpectation event(String requestId, String result, long soConfigVersion) {
        return new PrecalcMonitoring2398EventExpectation(requestId, result, soConfigVersion);
    }

    PrecalcMonitoring2398EventExpectation counter(String field, long expectedValue) {
        counters.put(field, expectedValue);
        return this;
    }

    String requestId() {
        return requestId;
    }

    String result() {
        return result;
    }

    long soConfigVersion() {
        return soConfigVersion;
    }

    Map<String, Long> counters() {
        return counters;
    }
}
