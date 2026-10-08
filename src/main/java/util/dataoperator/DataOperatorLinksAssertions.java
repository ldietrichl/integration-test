package util.dataoperator;

import com.fasterxml.jackson.databind.JsonNode;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.qameta.allure.Allure.step;
import static util.TestAssertions.*;

/** Strict v7 oracle: no DTO coercion, aliases or silently deduplicated responses. */
public final class DataOperatorLinksAssertions {
    private static final Pattern UUID_LOWERCASE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private DataOperatorLinksAssertions() {
    }

    public record Pair(String parentId, String id) {
    }

    public record Selection(long expId, int number) {
    }

    public static void shouldHaveError(ValidatableResponseWrapper response, int status, String message) {
        step("Проверяем HTTP-статус и строгий контракт ошибки links", () -> {
            assertEquals(status, response.toResponse().statusCode(), "Фактический HTTP-статус");
            shouldHaveJsonContentType(response);
            // Existing project check is retained; strict tree checks below reject type coercion.
            DataOperatorAssertions.shouldHaveSpecificationErrorEnvelope(response);
            JsonNode root = DataOperatorResponseMapper.responseTree(response);
            assertTrue(root != null && root.isObject(), "Ошибка должна быть JSON-объектом");
            JsonNode id = root.path("id");
            assertTrue(id.isTextual() && UUID_LOWERCASE.matcher(id.textValue()).matches(),
                    "id должен быть UUID-строкой в нижнем регистре");
            assertTrue(root.path("message").isTextual(), "message должен иметь JSON-тип string");
            if (message != null) {
                assertEquals(message, root.path("message").textValue(), "Текст ошибки по v7");
            }
            // 0 and 255 are unresolved U5, so only the common violation (>255) is rejected here.
            assertTrue(root.path("message").textValue().length() <= 255,
                    "message не может быть длиннее обоих ограничений v7");
            if (root.has("params")) {
                assertTrue(root.get("params").isObject(), "params при наличии должен быть объектом");
            }
        });
    }

    public static void shouldHaveJsonContentType(ValidatableResponseWrapper response) {
        String contentType = response.toResponse().getContentType();
        assertTrue(contentType != null && "application/json".equals(
                        contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)),
                "Ожидается Content-Type application/json, получено " + contentType);
    }

    public static void shouldHaveLinks(ValidatableResponseWrapper response,
                                      String objectType, String parentType,
                                      Map<Long, Set<Integer>> conditions,
                                      Map<Selection, Set<Pair>> expected) {
        step("Проверяем полную структуру и точные связи каждого эксперимента/условия", () -> {
            assertEquals(200, response.toResponse().statusCode(), "HTTP-статус links");
            shouldHaveJsonContentType(response);
            JsonNode root = DataOperatorResponseMapper.responseTree(response);
            assertTrue(root != null && root.isObject(), "Ответ должен быть объектом");
            shouldHaveType(root, "objectIdType", objectType);
            shouldHaveType(root, "parentIdType", parentType);
            assertTrue(root.path("links").isArray(), "links — обязательный массив");
            Map<Long, Set<Integer>> actualConditions = new LinkedHashMap<>();
            Map<Selection, Set<Pair>> actual = new LinkedHashMap<>();
            for (JsonNode experiment : root.path("links")) {
                JsonNode expId = experiment.path("expId");
                assertTrue(expId.isIntegralNumber() && expId.canConvertToLong(), "expId должен быть int64");
                long id = expId.longValue();
                assertFalse(actualConditions.containsKey(id), "Повтор expId=" + id);
                Set<Integer> numbers = new LinkedHashSet<>();
                actualConditions.put(id, numbers);
                assertTrue(experiment.path("objectSelectConditions").isArray(),
                        "objectSelectConditions — обязательный массив");
                for (JsonNode condition : experiment.path("objectSelectConditions")) {
                    JsonNode number = condition.path("number");
                    assertTrue(number.isIntegralNumber() && number.canConvertToInt()
                                    && number.intValue() >= Short.MIN_VALUE && number.intValue() <= Short.MAX_VALUE,
                            "number должен быть int16");
                    assertTrue(numbers.add(number.intValue()), "Повтор number в expId=" + id);
                    assertTrue(condition.path("splittingObjects").isArray(),
                            "splittingObjects — обязательный массив, splittingObjectIds не является алиасом");
                    Set<String> parents = new LinkedHashSet<>();
                    Set<Pair> pairs = new LinkedHashSet<>();
                    for (JsonNode group : condition.path("splittingObjects")) {
                        assertTrue(group.isObject(), "Группа связей должна быть объектом");
                        String parent = null;
                        if (group.has("parentId")) {
                            assertTrue(group.get("parentId").isTextual(),
                                    "parentId должен быть строкой; при отсутствии родителя поле нужно опустить");
                            parent = group.get("parentId").textValue();
                        }
                        assertTrue(parents.add(parent), "Повтор родительской группы: " + parent);
                        assertTrue(group.path("ids").isArray(), "ids — обязательный массив");
                        assertTrue(!group.path("ids").isEmpty(), "Найденная группа не может содержать пустой ids");
                        for (JsonNode objectId : group.path("ids")) {
                            assertTrue(objectId.isTextual(), "Элемент ids должен быть строкой");
                            assertTrue(pairs.add(new Pair(parent, objectId.textValue())),
                                    "Повтор пары parentId/id");
                        }
                    }
                    actual.put(new Selection(id, number.intValue()), pairs);
                }
            }
            assertEquals(conditions, actualConditions, "Эксперименты и номера условий, включая пустые");
            assertEquals(expected, actual, "Полные множества связей, без пропусков и лишних пар");
        });
    }

    private static void shouldHaveType(JsonNode root, String field, String expected) {
        if (expected == null) {
            assertTrue(root.has(field) && root.get(field).isNull(), field + " должен быть явным null");
        } else {
            assertTrue(root.path(field).isTextual(), field + " должен быть строкой");
            assertEquals(expected, root.path(field).textValue(), field + " берётся из метаданных");
        }
    }
}
