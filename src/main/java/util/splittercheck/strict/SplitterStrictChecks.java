package util.splittercheck.strict;

import com.fasterxml.jackson.databind.JsonNode;
import util.splittercheck.SplitterCheckCollector;
import util.splittercheck.SplitterCheckMode;
import util.splittercheck.SplitterResponseReader;
import util.splittercheck.SplitterResponseSnapshot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SplitterStrictChecks {

    private SplitterStrictChecks() {
    }

    public static void verifyRuleCodes(Object responseWrapper,
                                       String objectId,
                                       Set<String> expectedRuleCodes,
                                       SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("rule-codes");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Строгая проверка ruleCode ожидает JSON body");
            collector.finish(mode, snapshot);
            return;
        }
        JsonNode root = snapshot.getJsonBody();
        JsonNode objectNode = findObject(root, objectId);
        if (objectNode == null) {
            collector.addViolation("Не найден objectId=" + objectId);
            collector.finish(mode, snapshot);
            return;
        }
        Set<String> actualRuleCodes = new LinkedHashSet<>();
        JsonNode objectResults = objectNode.path("objectResults");
        if (!objectResults.isArray()) {
            collector.addViolation("У объекта " + objectId + " objectResults не является массивом");
            collector.finish(mode, snapshot);
            return;
        }
        for (JsonNode objectResult : objectResults) {
            actualRuleCodes.add(objectResult.path("ruleCode").asText());
        }
        if (!actualRuleCodes.equals(expectedRuleCodes)) {
            collector.addViolation("Ожидали ruleCode=" + expectedRuleCodes + ", фактически=" + actualRuleCodes);
        }
        collector.finish(mode, snapshot);
    }

    public static void verifyMainIsSubsetOfAll(Object responseWrapper,
                                               String objectId,
                                               SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("main-subset-of-all");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Строгая проверка MAIN/ALL ожидает JSON body");
            collector.finish(mode, snapshot);
            return;
        }
        JsonNode root = snapshot.getJsonBody();
        JsonNode objectNode = findObject(root, objectId);
        if (objectNode == null) {
            collector.addViolation("Не найден objectId=" + objectId);
            collector.finish(mode, snapshot);
            return;
        }

        Set<Long> mainExpIds = collectExpIds(findRule(objectNode, "MAIN"));
        Set<Long> allExpIds = collectExpIds(findRule(objectNode, "ALL"));
        if (!allExpIds.containsAll(mainExpIds)) {
            collector.addViolation("ALL должен содержать все expId из MAIN. MAIN=" + mainExpIds + ", ALL=" + allExpIds);
        }
        collector.finish(mode, snapshot);
    }

    public static void verifyNoDuplicateExpIds(Object responseWrapper,
                                               String objectId,
                                               SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("duplicate-exp-id");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Строгая проверка duplicate expId ожидает JSON body");
            collector.finish(mode, snapshot);
            return;
        }
        JsonNode root = snapshot.getJsonBody();
        JsonNode objectNode = findObject(root, objectId);
        if (objectNode == null) {
            collector.addViolation("Не найден objectId=" + objectId);
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode objectResults = objectNode.path("objectResults");
        if (!objectResults.isArray()) {
            collector.addViolation("У объекта " + objectId + " objectResults не является массивом");
            collector.finish(mode, snapshot);
            return;
        }

        for (JsonNode objectResult : objectResults) {
            String ruleCode = objectResult.path("ruleCode").asText("<unknown>");
            JsonNode resultExps = objectResult.path("resultExps");
            if (!resultExps.isArray()) {
                continue;
            }
            Set<Long> unique = new HashSet<>();
            List<Long> duplicates = new ArrayList<>();
            for (JsonNode resultExp : resultExps) {
                long expId = resultExp.path("expId").asLong(Long.MIN_VALUE);
                if (!unique.add(expId)) {
                    duplicates.add(expId);
                }
            }
            if (!duplicates.isEmpty()) {
                collector.addViolation("Для ruleCode=" + ruleCode + " найдены дубли expId=" + duplicates);
            }
        }
        collector.finish(mode, snapshot);
    }

    public static void verifyExpectedResultParam(Object responseWrapper,
                                                 String objectId,
                                                 String ruleCode,
                                                 long expId,
                                                 String paramCode,
                                                 String expectedValue,
                                                 SplitterCheckMode mode) {
        if (!mode.isEnabled()) {
            return;
        }
        SplitterResponseSnapshot snapshot = SplitterResponseReader.snapshot(responseWrapper);
        SplitterCheckCollector collector = new SplitterCheckCollector("expected-result-param");
        if (!snapshot.hasJsonBody()) {
            collector.addViolation("Строгая проверка result param ожидает JSON body");
            collector.finish(mode, snapshot);
            return;
        }
        JsonNode root = snapshot.getJsonBody();
        JsonNode objectNode = findObject(root, objectId);
        if (objectNode == null) {
            collector.addViolation("Не найден objectId=" + objectId);
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode ruleNode = findRule(objectNode, ruleCode);
        if (ruleNode == null) {
            collector.addViolation("Не найден ruleCode=" + ruleCode + " для объекта " + objectId);
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode expNode = findExp(ruleNode, expId);
        if (expNode == null) {
            collector.addViolation("Не найден expId=" + expId + " внутри ruleCode=" + ruleCode);
            collector.finish(mode, snapshot);
            return;
        }

        JsonNode params = expNode.path("groupResultParams");
        if (!params.isArray()) {
            collector.addViolation("groupResultParams отсутствует у expId=" + expId);
            collector.finish(mode, snapshot);
            return;
        }

        for (JsonNode param : params) {
            if (Objects.equals(paramCode, param.path("paramCode").asText(null))) {
                JsonNode values = param.path("paramValues");
                if (!values.isArray() || values.isEmpty()) {
                    collector.addViolation("paramCode=" + paramCode + " не содержит значений");
                } else if (!Objects.equals(expectedValue, values.get(0).asText())) {
                    collector.addViolation("Ожидали paramCode=" + paramCode + " value=" + expectedValue
                            + ", фактически=" + values.get(0).asText());
                }
                collector.finish(mode, snapshot);
                return;
            }
        }

        collector.addViolation("Не найден paramCode=" + paramCode + " у expId=" + expId);
        collector.finish(mode, snapshot);
    }

    private static JsonNode findObject(JsonNode root, String objectId) {
        JsonNode splittingResults = root.path("splittingResults");
        if (!splittingResults.isArray()) {
            return null;
        }
        for (JsonNode result : splittingResults) {
            if (Objects.equals(objectId, result.path("objectId").asText(null))) {
                return result;
            }
        }
        return null;
    }

    private static JsonNode findRule(JsonNode objectNode, String ruleCode) {
        JsonNode objectResults = objectNode.path("objectResults");
        if (!objectResults.isArray()) {
            return null;
        }
        for (JsonNode objectResult : objectResults) {
            if (Objects.equals(ruleCode, objectResult.path("ruleCode").asText(null))) {
                return objectResult;
            }
        }
        return null;
    }

    private static JsonNode findExp(JsonNode ruleNode, long expId) {
        JsonNode resultExps = ruleNode.path("resultExps");
        if (!resultExps.isArray()) {
            return null;
        }
        for (JsonNode resultExp : resultExps) {
            if (resultExp.path("expId").asLong(Long.MIN_VALUE) == expId) {
                return resultExp;
            }
        }
        return null;
    }

    private static Set<Long> collectExpIds(JsonNode ruleNode) {
        Set<Long> result = new LinkedHashSet<>();
        if (ruleNode == null) {
            return result;
        }
        JsonNode resultExps = ruleNode.path("resultExps");
        if (!resultExps.isArray()) {
            return result;
        }
        for (JsonNode resultExp : resultExps) {
            result.add(resultExp.path("expId").asLong(Long.MIN_VALUE));
        }
        return result;
    }
}
