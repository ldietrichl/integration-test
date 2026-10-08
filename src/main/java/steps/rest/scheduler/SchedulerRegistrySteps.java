package steps.rest.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import steps.container.KubernetesTunnelSteps;
import steps.rest.dictionaries.v1.DictionariesV1Steps;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.qameta.allure.Allure.step;
import static steps.rest.scheduler.SchedulerSteps.expect;

/** Read-only V2 registry probes. Metadata and DTO mappings are explicit, never borrowed from V1. */
public final class SchedulerRegistrySteps {
    public static final String FORM_CODE = "AUTOSTART_TASK_LIST";
    public static final int PAGE_SIZE = 20;
    private final SchedulerSteps scheduler;
    private final DictionariesV1Steps dictionary;

    public SchedulerRegistrySteps(SchedulerSteps scheduler, DictionariesV1Steps dictionary) {
        this.scheduler = scheduler;
        this.dictionary = dictionary;
    }

    public enum Kind { NUMBER, TEXT, ENUM, TIME }

    public record Field(String code, String pointer, Kind kind, boolean filterable, boolean sortable) {
        public JsonNode value(JsonNode row) {
            JsonNode value = row.at(pointer);
            require(!value.isMissingNode(), "Mapped V2 DTO field is missing: " + code + " -> " + pointer);
            if (!value.isNull()) compare(value, value);
            return value;
        }

        /** Numeric time is only ordered, never reinterpreted as seconds or milliseconds. */
        public int compare(JsonNode left, JsonNode right) {
            require(!left.isNull() && !right.isNull(), "Cannot compare null values for " + code);
            if (kind == Kind.NUMBER || (kind == Kind.TIME && left.isNumber() && right.isNumber())) {
                require(left.isNumber() && right.isNumber(), "Expected numeric values for " + code);
                return left.decimalValue().compareTo(right.decimalValue());
            }
            if (kind == Kind.TIME) {
                require(left.isTextual() && right.isTextual(), "Mixed or unsupported date representations for " + code);
                try {
                    return OffsetDateTime.parse(left.asText()).toInstant()
                            .compareTo(OffsetDateTime.parse(right.asText()).toInstant());
                } catch (java.time.format.DateTimeParseException invalid) {
                    throw new AssertionError("Text dates must carry an ISO-8601 offset for " + code);
                }
            }
            require(left.isTextual() && right.isTextual(), "Expected text/enum values for " + code);
            return left.asText().compareTo(right.asText());
        }
    }

    private record Mapping(String code, String pointer, Kind kind) { }
    public record FilterChoice(Field field, JsonNode value) { }

    // Only fields whose entity -> TaskContent mapping is present in the supplied service source.
    // taskNumber is deliberately excluded: its converter currently emits a constant.
    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping("id", "/id", Kind.NUMBER),
            new Mapping("createdAt", "/createdAt", Kind.TIME),
            new Mapping("updatedAt", "/updatedAt", Kind.TIME),
            new Mapping("scheduleDatetime", "/planDt", Kind.TIME),
            new Mapping("createdBy", "/createdBy", Kind.NUMBER),
            new Mapping("updatedBy", "/updatedBy", Kind.NUMBER),
            new Mapping("splittingPoint", "/splittingPoint/code", Kind.TEXT),
            new Mapping("status", "/status/code", Kind.ENUM)
    );

    public List<Field> metadata() {
        return step("Read V2 AUTOSTART_TASK_LIST metadata through the project's dictionary REST step", () -> {
            JsonNode response = expect(dictionary.getExpressionParameterDict(
                    ExpressionParameterDictReqDto.builder().formCode(FORM_CODE).build()), 200);
            require(response.isArray() && !response.isEmpty(), "V2 dictionary must return a non-empty array");
            Map<String, JsonNode> byCode = new LinkedHashMap<>();
            List<Map<String, Object>> evidence = new ArrayList<>();
            for (JsonNode item : response) {
                require(item.isObject() && item.path("paramCode").isTextual()
                                && !item.path("paramCode").asText().isBlank(),
                        "Expected paramCode in the V2 expression dictionary, not V1 code");
                String code = item.path("paramCode").asText();
                require(byCode.putIfAbsent(code, item) == null, "Duplicate V2 dictionary paramCode: " + code);
                if (item.hasNonNull("formCode"))
                    require(FORM_CODE.equals(item.path("formCode").asText()), "Dictionary returned another formCode");
                List<String> operators = new ArrayList<>();
                if (item.path("validOperators").isArray()) {
                    for (JsonNode operator : item.path("validOperators")) {
                        require(operator.isObject() && operator.path("code").isTextual(),
                                "Expected V2 validOperators objects");
                        operators.add(operator.path("code").asText());
                    }
                } else {
                    require(!item.path("filterFlag").asBoolean(), "Filterable V2 field has no operator array: " + code);
                }
                evidence.add(Map.of("paramCode", code,
                        "dataType", item.path("dataType").asText(""),
                        "filterFlag", item.path("filterFlag").asBoolean(),
                        "orderFlag", item.path("orderFlag").asBoolean(),
                        "operators", operators));
            }
            KubernetesTunnelSteps.evidence("V2 registry dictionary capabilities", Map.of(
                    "formCode", FORM_CODE,
                    "endpoint", constants.Endpoints.DictionariesV2.V2_EXPRESSION_PARAMETER_DICT,
                    "fields", evidence,
                    "excludedFromPositiveOracle", List.of("taskNumber"),
                    "note", "V1 history metadata is not used; dates keep their separate strict contract check"));
            List<Field> fields = new ArrayList<>();
            for (Mapping mapping : MAPPINGS) {
                JsonNode item = byCode.get(mapping.code());
                if (item == null) continue;
                String type = item.path("dataType").asText("").toUpperCase(Locale.ROOT);
                boolean compatible = switch (mapping.kind()) {
                    case NUMBER -> Set.of("INTEGER", "NUMBER").contains(type);
                    // Numeric dates may be ordered without accepting their wire format in SCH-INF-007.
                    case TIME -> Set.of("DATE", "DATETIME", "INTEGER", "NUMBER").contains(type);
                    case TEXT -> "STRING".equals(type);
                    case ENUM -> "ENUM".equals(type);
                };
                if (!compatible) continue;
                boolean equal = false;
                for (JsonNode operator : item.path("validOperators"))
                    if ("equal".equals(operator.path("code").asText())) equal = true;
                fields.add(new Field(mapping.code(), mapping.pointer(), mapping.kind(),
                        item.path("filterFlag").asBoolean() && equal && mapping.kind() != Kind.TIME,
                        item.path("orderFlag").asBoolean()
                                && (mapping.kind() == Kind.NUMBER || mapping.kind() == Kind.TIME)));
            }
            KubernetesTunnelSteps.evidence("V2 mapped oracle fields", Map.of(
                    "equalFilterCandidates", fields.stream().filter(Field::filterable).map(Field::code).toList(),
                    "sortCandidates", fields.stream().filter(Field::sortable).map(Field::code).toList(),
                    "scope", "Finite source-backed mapping; no arbitrary JSON-path guessing or V1 fallback"));
            return List.copyOf(fields);
        });
    }

    /** Keep equality semantics; ENUM often advertises singleton IN rather than equal. */
    public Map<String, Object> exactFilter(String field, String value) {
        return step("Select an advertised exact-match operator for " + field, () -> {
            JsonNode metadata = expect(dictionary.getExpressionParameterDict(
                    ExpressionParameterDictReqDto.builder().formCode(FORM_CODE).build()), 200);
            require(metadata.isArray(), "Expected the V2 expression dictionary array");
            List<JsonNode> matches = new ArrayList<>();
            metadata.forEach(item -> { if (field.equals(item.path("paramCode").asText())) matches.add(item); });
            require(matches.size() == 1, "Missing or duplicate V2 filter metadata: " + field);
            JsonNode item = matches.get(0);
            require(item.path("filterFlag").asBoolean(), "Field is not advertised as filterable: " + field);
            if (item.hasNonNull("formCode"))
                require(FORM_CODE.equals(item.path("formCode").asText()), "Dictionary returned another formCode");
            Set<String> operators = new java.util.HashSet<>();
            for (JsonNode operator : item.path("validOperators"))
                operators.add(operator.path("code").asText());
            String selected = operators.contains("equal") ? "equal" : operators.contains("in") ? "in" : null;
            require(selected != null, "No advertised equal or singleton-in operator: " + field);
            KubernetesTunnelSteps.evidence("Exact filter capability", Map.of(
                    "paramCode", field, "operator", selected, "formCode", FORM_CODE, "valueAttached", false));
            return request.scheduler.SchedulerTestDataFactory.filter(field, selected, value);
        });
    }

    public String splittingPointName(String code) {
        return step("Resolve the splitting-point display name from the real V2 dictionary", () -> {
            JsonNode response = expect(dictionary.getSplittingPoints(), 200);
            require(response.isArray(), "Expected the V2 splitting-points array");
            List<JsonNode> matches = new ArrayList<>();
            response.forEach(item -> { if (code.equals(item.path("code").asText())) matches.add(item); });
            require(matches.size() == 1, "Splitting-point dictionary code is missing or duplicated: " + code);
            JsonNode name = matches.get(0).path("name");
            require(name.isTextual() && !name.asText().isBlank(), "Dictionary display name must be nonblank");
            return name.asText();
        });
    }

    public List<JsonNode> baseline() { return page(Map.of("page", 0, "size", PAGE_SIZE)); }

    public Optional<FilterChoice> chooseFilter(List<Field> fields, List<JsonNode> rows) {
        for (Field field : fields) {
            if (!field.filterable()) continue;
            for (JsonNode row : rows) {
                JsonNode value = field.value(row);
                if (!value.isNull()) {
                    KubernetesTunnelSteps.evidence("V2 equal filter selection", Map.of(
                            "formCode", FORM_CODE, "paramCode", field.code(), "dtoPointer", field.pointer(),
                            "operator", "equal", "baselineRows", rows.size(), "valueAttached", false));
                    return Optional.of(new FilterChoice(field, value));
                }
            }
        }
        KubernetesTunnelSteps.evidence("V2 equal filter unavailable", Map.of(
                "reason", "No advertised equal filter with a mapped, non-null observed scalar",
                "baselineRows", rows.size()));
        return Optional.empty();
    }

    public List<JsonNode> filtered(FilterChoice choice) {
        return page(Map.of("page", 0, "size", PAGE_SIZE, "filters",
                List.of(List.of(Map.of("code", choice.field().code(),
                        "operator", "equal", "values", List.of(choice.value().asText()))))));
    }

    public Optional<Field> chooseSort(List<Field> fields, List<JsonNode> rows) {
        for (Field field : fields) {
            if (!field.sortable() || rows.size() < 2) continue;
            List<JsonNode> values = rows.stream().map(field::value).toList();
            // Null placement/collation is not inferred from another service's contract.
            if (values.stream().anyMatch(JsonNode::isNull)) continue;
            boolean varied = values.stream().anyMatch(value -> field.compare(values.get(0), value) != 0);
            if (varied) {
                KubernetesTunnelSteps.evidence("V2 sort selection", Map.of(
                        "formCode", FORM_CODE, "paramCode", field.code(), "dtoPointer", field.pointer(),
                        "baselineRows", rows.size(), "baselineHasDistinctValues", true,
                        "excludedConstantField", "taskNumber"));
                return Optional.of(field);
            }
        }
        KubernetesTunnelSteps.evidence("V2 sort unavailable", Map.of(
                "reason", "No mapped sortable numeric/date field with non-null varied values on the bounded baseline",
                "baselineRows", rows.size()));
        return Optional.empty();
    }

    public List<JsonNode> sorted(Field field, String direction) {
        if (!field.sortable() || !Set.of("ASC", "DESC").contains(direction))
            throw new IllegalArgumentException("Expected an advertised V2 field and ASC/DESC");
        return page(Map.of("page", 0, "size", PAGE_SIZE,
                "sorts", List.of(Map.of("code", field.code(), "direction", direction))));
    }

    private List<JsonNode> page(Map<String, Object> body) {
        JsonNode response = expect(scheduler.registry(body), 200);
        JsonNode content = response.path("content");
        require(content.isArray(), "V2 registry content must be an array");
        require(content.size() <= PAGE_SIZE, "V2 registry exceeded the requested bounded page");
        require(response.path("totalPages").isIntegralNumber() && response.path("totalPages").asLong() >= 0,
                "V2 registry totalPages must be a non-negative integer");
        List<JsonNode> rows = new ArrayList<>();
        content.forEach(row -> {
            require(row.isObject(), "V2 registry row must be an object");
            rows.add(row);
        });
        return List.copyOf(rows);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
