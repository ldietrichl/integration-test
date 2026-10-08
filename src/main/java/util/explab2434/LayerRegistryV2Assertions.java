package util.explab2434;

import util.TestAssertions;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static io.qameta.allure.Allure.step;

public class LayerRegistryV2Assertions {

    private LayerRegistryV2Assertions() {
    }

    public static void shouldHaveRegistryEnvelope(ValidatableResponseWrapper response) {
        step("Проверяем базовую структуру ответа реестра слоев v2", () -> response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('totalPages')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('content')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content != null")
        ));
    }

    public static void shouldContainLayer(ValidatableResponseWrapper response, Long layerId, String layerName) {
        step("Проверяем, что реестр содержит созданный слой id=%s, name=%s".formatted(layerId, layerName), () -> {
            List<Long> ids = numericList(response, "content.id");
            List<String> names = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertTrue(
                    ids.stream().anyMatch(id -> id.equals(layerId)),
                    "В content должен быть слой с id=%s. Фактические id=%s".formatted(layerId, ids));
            TestAssertions.assertTrue(
                    names.contains(layerName),
                    "В content должен быть слой с name=%s. Фактические name=%s".formatted(layerName, names));
        });
    }

    public static void shouldContainOnlyNamesWithPrefix(ValidatableResponseWrapper response, String prefix) {
        step("Проверяем, что в content только слои с тестовым префиксом %s".formatted(prefix), () -> {
            List<String> names = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertFalse(names.isEmpty(), "По тестовому префиксу должен вернуться непустой content");
            TestAssertions.assertTrue(
                    names.stream().allMatch(name -> name != null && name.startsWith(prefix)),
                    "Все имена должны начинаться с '%s'. Фактические name=%s".formatted(prefix, names));
        });
    }

    public static void shouldContainAllExpectedNames(ValidatableResponseWrapper response, List<String> expectedNames) {
        step("Проверяем, что реестр содержит все ожидаемые слои", () -> {
            List<String> actualNames = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertTrue(
                    actualNames.containsAll(expectedNames),
                    "Реестр должен содержать expected=%s. Фактические name=%s".formatted(expectedNames, actualNames));
        });
    }

    public static void shouldHaveAllStatuses(ValidatableResponseWrapper response, String expectedStatusCode) {
        step("Проверяем, что все записи имеют статус %s".formatted(expectedStatusCode), () -> {
            List<String> statuses = response.toJsonPath().getList("content.status.code", String.class);
            TestAssertions.assertFalse(statuses.isEmpty(), "Список статусов не должен быть пустым");
            TestAssertions.assertTrue(
                    statuses.stream().allMatch(expectedStatusCode::equals),
                    "Все статусы должны быть %s. Фактические status.code=%s".formatted(expectedStatusCode, statuses));
        });
    }

    public static void shouldHaveShareTotalForLayer(ValidatableResponseWrapper response, String layerName, long expectedShareTotal) {
        step("Проверяем shareTotal для слоя %s".formatted(layerName), () -> {
            List<?> content = response.toJsonPath().getList("content");
            boolean found = content.stream().anyMatch(item -> {
                @SuppressWarnings("unchecked")
                var layer = (java.util.Map<String, Object>) item;
                Object actualName = layer.get("name");
                Object actualShareTotal = layer.get("shareTotal");
                return Objects.equals(actualName, layerName)
                        && Objects.equals(Long.valueOf(String.valueOf(actualShareTotal)), expectedShareTotal);
            });
            TestAssertions.assertTrue(found, "Для слоя %s ожидался shareTotal=%s. Content=%s"
                    .formatted(layerName, expectedShareTotal, content));
        });
    }

    public static void shouldNotHaveDuplicateIds(ValidatableResponseWrapper response) {
        step("Проверяем отсутствие дублей по id в content", () -> {
            List<Long> ids = numericList(response, "content.id");
            Set<Long> unique = new HashSet<>(ids);
            TestAssertions.assertEquals(ids.size(), unique.size(), "В content не должно быть дублей по id. Фактические id=%s".formatted(ids));
        });
    }

    public static void shouldBeSortedByIntegerField(ValidatableResponseWrapper response, String jsonPath, boolean asc) {
        step("Проверяем сортировку числового поля %s, asc=%s".formatted(jsonPath, asc), () -> {
            List<Long> actual = numericList(response, jsonPath);
            List<Long> expected = new ArrayList<>(actual);
            expected.sort(asc ? Long::compareTo : Comparator.reverseOrder());
            TestAssertions.assertEquals(expected, actual, "Поле %s должно быть отсортировано. Фактический порядок=%s".formatted(jsonPath, actual));
        });
    }

    public static void shouldBeSortedByStringField(ValidatableResponseWrapper response, String jsonPath, boolean asc) {
        step("Проверяем сортировку строкового поля %s, asc=%s".formatted(jsonPath, asc), () -> {
            List<String> actual = response.toJsonPath().getList(jsonPath, String.class);
            List<String> expected = new ArrayList<>(actual);
            expected.sort(String.CASE_INSENSITIVE_ORDER);
            if (!asc) {
                expected.sort(String.CASE_INSENSITIVE_ORDER.reversed());
            }
            TestAssertions.assertEquals(expected, actual, "Поле %s должно быть отсортировано. Фактический порядок=%s".formatted(jsonPath, actual));
        });
    }

    public static void shouldBeSortedByDateStringField(ValidatableResponseWrapper response, String jsonPath, boolean asc) {
        step("Проверяем сортировку даты %s, asc=%s".formatted(jsonPath, asc), () -> {
            List<String> actual = response.toJsonPath().getList(jsonPath, String.class);
            List<String> expected = new ArrayList<>(actual);
            expected.sort(Comparator.naturalOrder());
            if (!asc) {
                expected.sort(Comparator.reverseOrder());
            }
            TestAssertions.assertEquals(expected, actual, "Поле %s должно быть отсортировано как дата/ISO строка. Фактический порядок=%s".formatted(jsonPath, actual));
        });
    }

    public static void shouldMatchSearchByNameIgnoreCase(ValidatableResponseWrapper response, String search) {
        step("Проверяем поиск по name без учета регистра: %s".formatted(search), () -> {
            String normalizedSearch = search.toLowerCase(Locale.ROOT);
            List<String> names = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertFalse(names.isEmpty(), "Поиск по name должен вернуть непустой content");
            TestAssertions.assertTrue(
                    names.stream().allMatch(name -> name.toLowerCase(Locale.ROOT).contains(normalizedSearch)),
                    "Все name должны содержать search=%s. Фактические name=%s".formatted(search, names));
        });
    }


    public static void shouldContainOnlyIds(ValidatableResponseWrapper response, List<Long> expectedIds) {
        step("Проверяем, что в content только ожидаемые id", () -> {
            List<Long> actualIds = numericList(response, "content.id");
            TestAssertions.assertEquals(
                    new HashSet<>(expectedIds),
                    new HashSet<>(actualIds),
                    "В content должны быть только expectedIds=%s. Фактические id=%s".formatted(expectedIds, actualIds));
        });
    }

    public static void shouldNotContainNames(ValidatableResponseWrapper response, List<String> forbiddenNames) {
        step("Проверяем, что реестр не содержит запрещенные имена", () -> {
            List<String> actualNames = response.toJsonPath().getList("content.name", String.class);
            List<String> intersection = actualNames.stream().filter(forbiddenNames::contains).toList();
            TestAssertions.assertTrue(
                    intersection.isEmpty(),
                    "Реестр не должен содержать forbidden=%s. Фактические совпадения=%s, все name=%s"
                            .formatted(forbiddenNames, intersection, actualNames));
        });
    }

    public static void shouldHaveOnlyPriorities(ValidatableResponseWrapper response, List<Long> expectedPriorities) {
        step("Проверяем, что в content только ожидаемые priority", () -> {
            Set<Long> expected = new HashSet<>(expectedPriorities);
            List<Long> actual = numericList(response, "content.priority");
            TestAssertions.assertFalse(actual.isEmpty(), "Список priority не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(expected::contains),
                    "Все priority должны входить в expected=%s. Фактические priority=%s".formatted(expected, actual));
        });
    }

    public static void shouldNotHavePriorities(ValidatableResponseWrapper response, List<Long> forbiddenPriorities) {
        step("Проверяем, что в content нет запрещенных priority", () -> {
            Set<Long> forbidden = new HashSet<>(forbiddenPriorities);
            List<Long> actual = numericList(response, "content.priority");
            TestAssertions.assertTrue(
                    actual.stream().noneMatch(forbidden::contains),
                    "В content не должно быть priority=%s. Фактические priority=%s".formatted(forbidden, actual));
        });
    }

    public static void shouldHaveAllStartDtBetween(ValidatableResponseWrapper response, long fromInclusive, long toInclusive) {
        step("Проверяем, что все startDt входят в диапазон", () -> {
            List<Long> actual = numericList(response, "content.startDt");
            TestAssertions.assertFalse(actual.isEmpty(), "Список startDt не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(value -> value >= fromInclusive && value <= toInclusive),
                    "Все startDt должны быть в диапазоне [%s, %s]. Фактические startDt=%s"
                            .formatted(fromInclusive, toInclusive, actual));
        });
    }

    public static void shouldHaveAllEndDtBetween(ValidatableResponseWrapper response, long fromInclusive, long toInclusive) {
        step("Проверяем, что все endDt входят в диапазон", () -> {
            List<Long> actual = numericList(response, "content.endDt");
            TestAssertions.assertFalse(actual.isEmpty(), "Список endDt не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(value -> value >= fromInclusive && value <= toInclusive),
                    "Все endDt должны быть в диапазоне [%s, %s]. Фактические endDt=%s"
                            .formatted(fromInclusive, toInclusive, actual));
        });
    }

    public static void shouldHaveContentSize(ValidatableResponseWrapper response, int expectedSize) {
        step("Проверяем размер content = %s".formatted(expectedSize), () -> {
            List<?> content = response.toJsonPath().getList("content");
            TestAssertions.assertEquals(expectedSize, content.size(), "Неверный размер content. Content=%s".formatted(content));
        });
    }

    public static void shouldHaveTotalPages(ValidatableResponseWrapper response, int expectedTotalPages) {
        step("Проверяем totalPages = %s".formatted(expectedTotalPages), () -> {
            Integer actualTotalPages = response.toJsonPath().getInt("totalPages");
            TestAssertions.assertEquals(expectedTotalPages, actualTotalPages, "Неверный totalPages");
        });
    }

    public static void shouldHaveAllSplittingPointCode(ValidatableResponseWrapper response, String expectedSplittingPointCode) {
        step("Проверяем, что все записи имеют splittingPointCode=%s".formatted(expectedSplittingPointCode), () -> {
            List<String> actual = response.toJsonPath().getList("content.splittingPointCode", String.class);
            TestAssertions.assertFalse(actual.isEmpty(), "Список splittingPointCode не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(expectedSplittingPointCode::equals),
                    "Все splittingPointCode должны быть %s. Фактические=%s".formatted(expectedSplittingPointCode, actual));
        });
    }


    public static void shouldHaveNamesInOrder(ValidatableResponseWrapper response, List<String> expectedNames) {
        step("Проверяем порядок имен в content", () -> {
            List<String> actualNames = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertEquals(expectedNames, actualNames,
                    "Ожидался порядок name=%s. Фактический порядок=%s".formatted(expectedNames, actualNames));
        });
    }

    public static void shouldHaveStatusesInOrder(ValidatableResponseWrapper response, List<String> expectedStatuses) {
        step("Проверяем порядок статусов в content", () -> {
            List<String> actualStatuses = response.toJsonPath().getList("content.status.code", String.class);
            TestAssertions.assertEquals(expectedStatuses, actualStatuses,
                    "Ожидался порядок status.code=%s. Фактический порядок=%s".formatted(expectedStatuses, actualStatuses));
        });
    }

    public static void shouldHaveOnlyStatuses(ValidatableResponseWrapper response, List<String> expectedStatusCodes) {
        step("Проверяем, что в content только ожидаемые статусы", () -> {
            Set<String> expected = new HashSet<>(expectedStatusCodes);
            List<String> actual = response.toJsonPath().getList("content.status.code", String.class);
            TestAssertions.assertFalse(actual.isEmpty(), "Список status.code не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(expected::contains),
                    "Все status.code должны входить в expected=%s. Фактические status.code=%s".formatted(expected, actual));
        });
    }

    public static void shouldNotHaveStatuses(ValidatableResponseWrapper response, List<String> forbiddenStatusCodes) {
        step("Проверяем, что в content нет запрещенных статусов", () -> {
            Set<String> forbidden = new HashSet<>(forbiddenStatusCodes);
            List<String> actual = response.toJsonPath().getList("content.status.code", String.class);
            TestAssertions.assertTrue(
                    actual.stream().noneMatch(forbidden::contains),
                    "В content не должно быть status.code=%s. Фактические status.code=%s".formatted(forbidden, actual));
        });
    }

    public static void shouldNotHaveIds(ValidatableResponseWrapper response, List<Long> forbiddenIds) {
        step("Проверяем, что в content нет запрещенных id", () -> {
            Set<Long> forbidden = new HashSet<>(forbiddenIds);
            List<Long> actual = numericList(response, "content.id");
            TestAssertions.assertTrue(
                    actual.stream().noneMatch(forbidden::contains),
                    "В content не должно быть id=%s. Фактические id=%s".formatted(forbidden, actual));
        });
    }

    public static void shouldHaveAllStartDtEqualTo(ValidatableResponseWrapper response, long expectedStartDt) {
        step("Проверяем, что все startDt равны ожидаемому значению", () -> {
            List<Long> actual = numericList(response, "content.startDt");
            TestAssertions.assertFalse(actual.isEmpty(), "Список startDt не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(value -> value == expectedStartDt),
                    "Все startDt должны быть %s. Фактические startDt=%s".formatted(expectedStartDt, actual));
        });
    }

    public static void shouldHaveAllEndDtEqualTo(ValidatableResponseWrapper response, long expectedEndDt) {
        step("Проверяем, что все endDt равны ожидаемому значению", () -> {
            List<Long> actual = numericList(response, "content.endDt");
            TestAssertions.assertFalse(actual.isEmpty(), "Список endDt не должен быть пустым");
            TestAssertions.assertTrue(
                    actual.stream().allMatch(value -> value == expectedEndDt),
                    "Все endDt должны быть %s. Фактические endDt=%s".formatted(expectedEndDt, actual));
        });
    }

    public static void shouldHaveRegistryLayerDictionaryParams(ValidatableResponseWrapper response) {
        step("Проверяем параметры справочника REGISTRY_LAYER", () -> {
            List<java.util.Map<String, Object>> params = rootList(response);
            List<String> actualParamCodes = params.stream()
                    .map(param -> String.valueOf(param.get("paramCode")))
                    .toList();
            TestAssertions.assertTrue(
                    actualParamCodes.containsAll(List.of("id", "name", "status", "priority", "startDt", "endDt")),
                    "В справочнике REGISTRY_LAYER должны быть базовые параметры. Фактические paramCode=%s"
                            .formatted(actualParamCodes));
        });
    }

    public static void shouldHaveRegistryLayerOperators(ValidatableResponseWrapper response, String paramCode, List<String> expectedOperators) {
        step("Проверяем операторы справочника REGISTRY_LAYER для paramCode=%s".formatted(paramCode), () -> {
            List<java.util.Map<String, Object>> params = rootList(response);
            java.util.Map<String, Object> target = params.stream()
                    .filter(param -> Objects.equals(String.valueOf(param.get("paramCode")), paramCode))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Не найден paramCode=%s. Доступные параметры=%s"
                            .formatted(paramCode, params)));

            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> operators = (List<java.util.Map<String, Object>>) target.get("validOperators");
            List<String> actualOperators = operators.stream()
                    .map(operator -> String.valueOf(operator.get("code")))
                    .toList();
            TestAssertions.assertTrue(
                    actualOperators.containsAll(expectedOperators),
                    "Для paramCode=%s ожидались операторы %s. Фактические операторы=%s"
                            .formatted(paramCode, expectedOperators, actualOperators));
        });
    }


    public static void shouldHaveRegistryLayerStatusEnumValues(ValidatableResponseWrapper response) {
        step("Проверяем ENUM значения статуса слоя в REGISTRY_LAYER", () -> {
            List<java.util.Map<String, Object>> params = rootList(response);

            java.util.Map<String, Object> statusParam = params.stream()
                    .filter(param -> Objects.equals(String.valueOf(param.get("paramCode")), "status"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Не найден paramCode=status. Доступные параметры=%s"
                                    .formatted(params.stream()
                                            .map(param -> String.valueOf(param.get("paramCode")))
                                            .toList())
                    ));

            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> enumValues =
                    (List<java.util.Map<String, Object>>) statusParam.get("enumValues");

            TestAssertions.assertNotNull(
                    enumValues,
                    "Для paramCode=status поле enumValues не должно быть null. Фактический statusParam=%s"
                            .formatted(statusParam)
            );

            List<String> actualEnumCodes = enumValues.stream()
                    .map(enumValue -> String.valueOf(enumValue.get("code")))
                    .toList();

            List<String> expectedEnumCodes = List.of("DRAFT", "IN_PROGRESS", "STOPPED");

            TestAssertions.assertTrue(
                    actualEnumCodes.containsAll(expectedEnumCodes),
                    "Для status ожидались ENUM %s. Фактические enumCodes=%s"
                            .formatted(expectedEnumCodes, actualEnumCodes)
            );
        });
    }


    private static List<java.util.Map<String, Object>> rootList(ValidatableResponseWrapper response) {
        List<java.util.Map<String, Object>> result = response.toJsonPath().getList("");
        if (result == null) {
            result = response.toJsonPath().getList("$");
        }
        return result;
    }

    private static List<Long> numericList(ValidatableResponseWrapper response, String jsonPath) {
        List<Object> rawValues = response.toJsonPath().getList(jsonPath);
        return rawValues.stream()
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    return Long.valueOf(String.valueOf(value));
                })
                .toList();
    }
}

