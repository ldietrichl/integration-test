package util.dataoperator;

import com.fasterxml.jackson.databind.JsonNode;
import dto.dataoperator.response.DataOperatorErrorResponseDto;
import dto.dataoperator.response.SplittingObjectIdsResponseDto;
import dto.dataoperator.response.SplittingObjectsResponseDto;
import util.TestAssertions;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.qameta.allure.Allure.step;

public final class DataOperatorAssertions {

    private DataOperatorAssertions() {
    }

    public static void shouldHaveIdsEnvelope(ValidatableResponseWrapper response) {
        step("Проверяем контракт спецификации: корневой объект с обязательным массивом ids", () -> {
            JsonNode root = DataOperatorResponseMapper.responseTree(response);
            TestAssertions.assertTrue(root.isObject(),
                    "По спецификации успешный ответ должен быть JSON-объектом {\"ids\":[...]}, "
                            + "но получен тип " + root.getNodeType() + ". Body=" + root);
            TestAssertions.assertTrue(root.has("ids"),
                    "В успешном ответе отсутствует обязательное поле ids. Body=" + root);
            TestAssertions.assertTrue(root.get("ids").isArray(),
                    "Поле ids должно быть JSON-массивом. Body=" + root);
        });
    }

    public static void shouldContainOnlyIdsField(ValidatableResponseWrapper response) {
        step("Проверяем, что новый endpoint не возвращает поля пагинации и content", () -> {
            JsonNode root = DataOperatorResponseMapper.responseTree(response);
            TestAssertions.assertTrue(root.isObject(),
                    "Проверка полей envelope применима только к JSON-объекту. Body=" + root);
            Set<String> fields = new HashSet<>();
            root.fieldNames().forEachRemaining(fields::add);
            TestAssertions.assertEquals(Set.of("ids"), fields,
                    "Успешный ответ нового endpoint должен содержать только поле ids. Body=" + root);
        });
    }

    public static List<String> ids(ValidatableResponseWrapper response) {
        SplittingObjectIdsResponseDto dto = DataOperatorResponseMapper.idsResponse(response);
        return dto.getIds() == null ? List.of() : dto.getIds();
    }

    public static List<String> controlObjectIds(ValidatableResponseWrapper response) {
        SplittingObjectsResponseDto dto = DataOperatorResponseMapper.objectsResponse(response);
        if (dto.getResultStatistics() == null || dto.getResultStatistics().getObjectIds() == null) {
            return List.of();
        }
        return dto.getResultStatistics().getObjectIds();
    }

    public static List<String> normalizedControlIds(ValidatableResponseWrapper response) {
        return controlObjectIds(response).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .limit(20_000)
                .toList();
    }

    public static void shouldHaveIdsExactly(ValidatableResponseWrapper response, List<String> expected) {
        step("Проверяем точный состав и порядок ids", () -> TestAssertions.assertEquals(
                expected,
                ids(response),
                "Новый endpoint должен вернуть ожидаемый отсортированный состав идентификаторов"));
    }

    public static void shouldHaveEmptyIds(ValidatableResponseWrapper response) {
        step("Проверяем пустой результат идентификаторов", () -> TestAssertions.assertEquals(
                List.of(), ids(response), "При отсутствии объектов должен вернуться пустой массив"));
    }

    public static void shouldHaveUniqueIds(ValidatableResponseWrapper response) {
        step("Проверяем уникальность идентификаторов", () -> {
            List<String> actual = ids(response);
            Set<String> unique = new HashSet<>(actual);
            TestAssertions.assertEquals(actual.size(), unique.size(),
                    "В результате не должно быть дублей. Фактический результат=" + actual);
        });
    }

    public static void shouldHaveAscendingIds(ValidatableResponseWrapper response) {
        step("Проверяем сортировку идентификаторов по возрастанию", () -> {
            List<String> actual = ids(response);
            List<String> sorted = new ArrayList<>(actual);
            sorted.sort(String::compareTo);
            TestAssertions.assertEquals(sorted, actual,
                    "Спецификация требует сортировку идентификаторов по возрастанию");
        });
    }

    public static void shouldNotExceedLimit(ValidatableResponseWrapper response, int maxSize) {
        step("Проверяем лимит результата <= " + maxSize, () -> TestAssertions.assertTrue(
                ids(response).size() <= maxSize,
                "Количество идентификаторов превышает лимит " + maxSize));
    }

    public static void shouldContainOnlyStrings(ValidatableResponseWrapper response) {
        step("Проверяем, что элементы ответа имеют JSON-тип string", () -> {
            JsonNode root = DataOperatorResponseMapper.responseTree(response);
            JsonNode idsNode = root.isArray() ? root : root.get("ids");
            TestAssertions.assertNotNull(idsNode, "В ответе отсутствует массив идентификаторов. Body=" + root);
            TestAssertions.assertTrue(idsNode.isArray(), "Идентификаторы должны быть JSON-массивом. Body=" + root);
            for (JsonNode item : idsNode) {
                TestAssertions.assertTrue(item.isTextual(),
                        "Каждый идентификатор должен иметь JSON-тип string. Элемент=" + item + ", Body=" + root);
            }
        });
    }

    public static void shouldHaveSpecificationErrorEnvelope(ValidatableResponseWrapper response) {
        step("Проверяем контракт ошибки id/message", () -> {
            DataOperatorErrorResponseDto error = DataOperatorResponseMapper.errorResponse(response);
            TestAssertions.assertNotNull(error.getId(), "В ошибке должен присутствовать id");
            TestAssertions.assertDoesNotThrow(() -> UUID.fromString(error.getId()),
                    "Поле id должно быть UUID: " + error.getId());
            TestAssertions.assertNotNull(error.getMessage(), "В ошибке должен присутствовать message");
            TestAssertions.assertFalse(error.getMessage().isBlank(), "message не должен быть пустым");
        });
    }
}

