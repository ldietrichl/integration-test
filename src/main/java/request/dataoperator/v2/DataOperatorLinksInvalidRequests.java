package request.dataoperator.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/** Each mutation starts from a fresh request and changes one contract condition. */
public final class DataOperatorLinksInvalidRequests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXP = "/exps/0";
    private static final String CONDITIONS = EXP + "/objectsSelectConditions";
    private static final String CONDITION = CONDITIONS + "/0";
    private static final String RULES = CONDITION + "/rules";
    private static final String RULE = RULES + "/0/0";

    private DataOperatorLinksInvalidRequests() {
    }

    public record InvalidRequest(String scenario, String variant, String body, boolean withoutBody) {
        @Override
        public String toString() {
            return scenario + ": " + variant;
        }
    }

    public static List<InvalidRequest> forScenario(String scenario) {
        return all().stream().filter(request -> scenario.equals(request.scenario())).toList();
    }

    public static List<InvalidRequest> all() {
        List<InvalidRequest> result = new ArrayList<>();
        absentAndNull(result, "SL-35", "/splittingPointCode", "/exps");
        replace(result, "SL-36", "/exps", "[]");
        absentAndNull(result, "SL-37", EXP + "/expId", CONDITIONS, CONDITION + "/number", RULES);
        absentAndNull(result, "SL-38", RULE + "/dataType", RULE + "/parameterCode", RULE + "/operatorCode");
        for (String field : List.of("/splittingPointCode", RULE + "/parameterCode")) {
            replace(result, "SL-39", field, "123", "true", "{}", "[]");
        }
        for (String field : List.of("/exps", CONDITIONS)) {
            replace(result, "SL-39", field, "{}", "\"abc\"", "123", "true");
            replace(result, "SL-39", field + "/0", "null", "\"abc\"", "123", "[]");
        }
        replace(result, "SL-39", RULES, "{}", "\"abc\"", "[null]", "[[null]]");
        ObjectNode baseline = baseline();
        replace(result, "SL-39", RULES, "[" + baseline.at(RULE) + "]");
        for (String field : List.of("/objectIds", "/parentObjectIds", RULE + "/values")) {
            replace(result, "SL-40", field, "\"abc\"", "{}", "123", "true",
                    "[123]", "[true]", "[{}]", "[[]]", "[null]");
        }
        replace(result, "SL-41", EXP + "/expId", "9223372036854775808", "-9223372036854775809",
                "1.5", "\"abc\"", "true", "{}", "[]");
        replace(result, "SL-42", CONDITION + "/number", "32768", "-32769", "1.5", "\"abc\"", "true", "{}", "[]");
        replace(result, "SL-43", RULE + "/dataType", "\"UNKNOWN\"", "123", "true", "{}", "[]");
        replace(result, "SL-43", RULE + "/operatorCode", "\"unknown_operator\"", "123", "true", "{}", "[]");
        result.add(new InvalidRequest("SL-45", "Повреждённый JSON", "{\"splittingPointCode\":", false));
        result.add(new InvalidRequest("SL-45", "Отсутствует HTTP-тело", null, true));
        result.add(new InvalidRequest("SL-45", "Корневой JSON null", "null", false));
        result.add(new InvalidRequest("SL-45", "Массив вместо корневого объекта", "[]", false));
        return List.copyOf(result);
    }

    private static void absentAndNull(List<InvalidRequest> result, String scenario, String... paths) {
        for (String path : paths) {
            ObjectNode request = baseline();
            ObjectNode parent = (ObjectNode) request.at(path.substring(0, path.lastIndexOf('/')));
            parent.remove(path.substring(path.lastIndexOf('/') + 1));
            result.add(new InvalidRequest(scenario, path + " отсутствует", request.toString(), false));
            replace(result, scenario, path, "null");
        }
    }

    private static void replace(List<InvalidRequest> result, String scenario, String path, String... values) {
        for (String value : values) {
            ObjectNode request = baseline();
            int slash = path.lastIndexOf('/');
            JsonNode parent = request.at(path.substring(0, slash));
            String field = path.substring(slash + 1);
            JsonNode replacement;
            try {
                replacement = MAPPER.readTree(value);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Некорректное описание тестового значения", exception);
            }
            if (parent.isObject()) {
                ((ObjectNode) parent).set(field, replacement);
            } else if (parent.isArray()) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) parent).set(Integer.parseInt(field), replacement);
            } else {
                throw new IllegalArgumentException("Не существует контейнер пути " + path);
            }
            result.add(new InvalidRequest(scenario, path + " = " + value, request.toString(), false));
        }
    }

    private static ObjectNode baseline() {
        return DataOperatorLinksTestDataFactory.baselineTree(DataOperatorV2TestDataFactory.DEFAULT_SPLITTING_POINT);
    }
}
