package util.pilot;

import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.TestAssertions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.qameta.allure.Allure.step;

public class PilotAssertions {
    private PilotAssertions() {
    }

    public static void shouldHavePilotDto(ValidatableResponseWrapper response) {
        step("Проверяем базовую структуру DTO пилота", () -> response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('id')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('name')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('launchStatus')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("launchStatus.code != null")
        ));
    }

    public static void shouldHavePilotNameAndStatus(ValidatableResponseWrapper response, String expectedName, String expectedStatus) {
        step("Проверяем имя и статус пилота", () -> {
            TestAssertions.assertEquals(expectedName, response.toJsonPath().getString("name"), "Неверное имя пилота");
            TestAssertions.assertEquals(expectedStatus, response.toJsonPath().getString("launchStatus.code"), "Неверный статус пилота");
        });
    }

    public static void shouldHaveEmptyLinkedArrays(ValidatableResponseWrapper response) {
        step("Проверяем обязательные пустые массивы связей пилота", () -> response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("linkedConfigComIds != null"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("linkedCampaignIds != null"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("linkedExperimentPromIds != null"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("linkedExperimentDkgIds != null")
        ));
    }

    public static void shouldHaveRegistryEnvelope(ValidatableResponseWrapper response) {
        step("Проверяем базовую структуру реестра пилотов", () -> response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('totalPages')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('content')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content != null")
        ));
    }

    public static void shouldHaveContentAtMost(ValidatableResponseWrapper response, int expectedMaxSize) {
        step("Проверяем, что размер content не превышает %s".formatted(expectedMaxSize), () -> {
            List<?> content = response.toJsonPath().getList("content");
            TestAssertions.assertTrue(
                    content.size() <= expectedMaxSize,
                    "Размер content должен быть <= %s. Фактический размер=%s".formatted(expectedMaxSize, content.size()));
        });
    }

    public static void shouldContainPilot(ValidatableResponseWrapper response, Long expectedId, String expectedName) {
        step("Проверяем, что реестр содержит пилот id=%s, name=%s".formatted(expectedId, expectedName), () -> {
            List<Long> ids = numericList(response, "content.id");
            List<String> names = response.toJsonPath().getList("content.name", String.class);
            TestAssertions.assertTrue(ids.contains(expectedId), "В реестре должен быть id=%s. Фактические id=%s".formatted(expectedId, ids));
            TestAssertions.assertTrue(names.contains(expectedName), "В реестре должен быть name=%s. Фактические name=%s".formatted(expectedName, names));
        });
    }

    public static void shouldContainOnlyNamesWithSearch(ValidatableResponseWrapper response, String search) {
        step("Проверяем поиск по имени пилота без учета регистра", () -> {
            List<String> names = response.toJsonPath().getList("content.name", String.class);
            String normalizedSearch = search.toLowerCase(Locale.ROOT);
            TestAssertions.assertFalse(names.isEmpty(), "Поиск должен вернуть непустой content");
            TestAssertions.assertTrue(
                    names.stream().allMatch(name -> name != null && name.toLowerCase(Locale.ROOT).contains(normalizedSearch)),
                    "Все имена должны содержать search=%s. Фактические name=%s".formatted(search, names));
        });
    }

    public static void shouldBeSortedByNameAsc(ValidatableResponseWrapper response) {
        step("Проверяем сортировку реестра пилотов по name ASC", () -> {
            List<String> actual = response.toJsonPath().getList("content.name", String.class);
            List<String> expected = new ArrayList<>(actual);
            expected.sort(String.CASE_INSENSITIVE_ORDER);
            TestAssertions.assertEquals(expected, actual, "Пилоты должны быть отсортированы по name ASC");
        });
    }

    public static void shouldHaveErrorDto(ValidatableResponseWrapper response) {
        step("Проверяем базовую структуру ошибки", () -> response.should(
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('id')"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('message')")
        ));
    }

    public static void shouldNotContainPilot(ValidatableResponseWrapper response, Long forbiddenId) {
        step("Проверяем, что реестр не содержит удаленный пилот id=%s".formatted(forbiddenId), () -> {
            List<Long> ids = numericList(response, "content.id");
            TestAssertions.assertFalse(ids.contains(forbiddenId), "Реестр не должен содержать id=%s. Фактические id=%s".formatted(forbiddenId, ids));
        });
    }

    private static List<Long> numericList(ValidatableResponseWrapper response, String jsonPath) {
        List<Object> rawValues = response.toJsonPath().getList(jsonPath);
        if (rawValues == null) {
            return List.of();
        }
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
