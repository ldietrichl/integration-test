package util.splittercheck.strict;

import com.fasterxml.jackson.databind.JsonNode;
import util.splittercheck.SplitterCheckCollector;
import util.splittercheck.SplitterCheckMode;
import util.splittercheck.SplitterResponseReader;
import util.splittercheck.SplitterResponseSnapshot;

public final class SplitterPrecalcStrictChecks {

    private SplitterPrecalcStrictChecks() {
    }

    public static void verifyDocumentedPrecalcSuccessEnvelope(Object responseWrapper,
                                                              SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("precalc-success-envelope");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Успешный predcalc должен возвращать JSON body c responseId и soConfigVersion");
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode root = snapshot.getJsonBody();
        if (!root.hasNonNull("responseId") || root.path("responseId").asText().isBlank()) {
            collector.addViolation("Отсутствует responseId в успешном ответе predcalc");
        }
        if (!root.has("soConfigVersion")) {
            collector.addViolation("Отсутствует soConfigVersion в успешном ответе predcalc");
        }
        collector.finish(mode, snapshot);
    }

    public static void verifyDocumentedConfigLoadEnvelope(Object responseWrapper,
                                                          SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("config-load-envelope");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Успешная загрузка config должна возвращать JSON body");
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode root = snapshot.getJsonBody();
        if (!root.hasNonNull("result")) {
            collector.addViolation("Отсутствует result в ответе loadConfig");
        }
        if (!root.has("currentConfigVersion")) {
            collector.addViolation("Отсутствует currentConfigVersion в ответе loadConfig");
        }
        collector.finish(mode, snapshot);
    }
}
