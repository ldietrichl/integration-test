package util.dataoperator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.dataoperator.v2.ResultStatisticsDto;
import dto.dataoperator.v2.SoFieldValuesDictResponseDto;
import dto.dataoperator.v2.SplittingObjectFieldDto;
import dto.dataoperator.v2.SplittingObjectFieldValueDto;
import dto.dataoperator.v2.SplittingObjectRuleDto;
import dto.dataoperator.v2.SplittingObjectsResponseDto;
import util.TestAssertions;
import request.dataoperator.v2.DataOperatorV2TestDataFactory;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.qameta.allure.Allure.step;

public final class DataOperatorV2Assertions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private DataOperatorV2Assertions() {
    }

    public static SplittingObjectsResponseDto splittingObjectsDto(ValidatableResponseWrapper response) {
        return read(response, SplittingObjectsResponseDto.class);
    }

    public static SoFieldValuesDictResponseDto soFieldValuesDictDto(ValidatableResponseWrapper response) {
        return read(response, SoFieldValuesDictResponseDto.class);
    }

    public static void shouldHaveSplittingObjectsEnvelope(ValidatableResponseWrapper response) {
        step("Проверяем обязательный envelope ответа splitting-objects", () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            TestAssertions.assertNotNull(dto.getTotalPages(), "Поле totalPages обязательно");
            TestAssertions.assertNotNull(dto.getResultStatistics(), "Поле resultStatistics обязательно");
            TestAssertions.assertNotNull(dto.getResultStatistics().getObjectsFound(),
                    "Поле resultStatistics.objectsFound обязательно");
            TestAssertions.assertNotNull(dto.getContent(), "Поле content обязательно");
            TestAssertions.assertTrue(dto.getTotalPages() >= 0, "totalPages не может быть отрицательным");
            TestAssertions.assertTrue(dto.getResultStatistics().getObjectsFound() >= 0,
                    "objectsFound не может быть отрицательным");
        });
    }

    public static void shouldPlaceObjectIdsInsideResultStatistics(ValidatableResponseWrapper response) {
        step("Проверяем расположение objectIds внутри resultStatistics", () -> {
            JsonNode root = readTree(response);
            TestAssertions.assertFalse(root.has("objectIds"),
                    "По контракту v2.1.0 objectIds не должен находиться в корне ответа: " + root);
            JsonNode statistics = root.path("resultStatistics");
            TestAssertions.assertTrue(statistics.isObject(), "resultStatistics должен быть объектом");
            TestAssertions.assertTrue(statistics.has("objectIds"),
                    "При returnIds=true objectIds должен находиться в resultStatistics: " + root);
            TestAssertions.assertTrue(statistics.get("objectIds").isArray(), "resultStatistics.objectIds должен быть массивом");
        });
    }

    public static void shouldHaveObjectIdsForAllFoundObjects(ValidatableResponseWrapper response) {
        step("Проверяем resultStatistics.objectIds для всех найденных объектов", () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            List<String> ids = requireIds(dto);
            long objectsFound = dto.getResultStatistics().getObjectsFound();
            long expectedSize = Math.min(objectsFound, DataOperatorV2TestDataFactory.OBJECT_IDS_LIMIT);

            TestAssertions.assertEquals(expectedSize, ids.size(),
                    "Количество objectIds должно соответствовать objectsFound с учетом лимита 20000");
            TestAssertions.assertTrue(ids.stream().allMatch(id -> id != null && !id.isBlank()),
                    "objectIds не должен содержать null/пустые значения: " + ids);
            TestAssertions.assertEquals(ids.size(), new HashSet<>(ids).size(),
                    "objectIds должен содержать уникальные идентификаторы");
            TestAssertions.assertTrue(ids.size() <= DataOperatorV2TestDataFactory.OBJECT_IDS_LIMIT,
                    "По SOWA-контракту objectIds должен содержать не более 20000 элементов");
        });
    }

    public static void shouldNotHaveObjectIdsKey(ValidatableResponseWrapper response) {
        step("Проверяем отсутствие resultStatistics.objectIds", () -> {
            JsonNode root = readTree(response);
            TestAssertions.assertFalse(root.has("objectIds"),
                    "objectIds не должен находиться в корне ответа: " + root);
            JsonNode statistics = root.path("resultStatistics");
            TestAssertions.assertFalse(statistics.has("objectIds"),
                    "При returnIds=false/null/отсутствует ключ resultStatistics.objectIds не должен добавляться. Ответ: " + root);
        });
    }

    public static void shouldHaveObjectIdsKey(ValidatableResponseWrapper response) {
        step("Проверяем наличие resultStatistics.objectIds", () -> {
            JsonNode root = readTree(response);
            JsonNode statistics = root.path("resultStatistics");
            TestAssertions.assertTrue(statistics.has("objectIds"),
                    "При returnIds=true и непустом результате resultStatistics.objectIds обязателен. Ответ: " + root);
            TestAssertions.assertTrue(statistics.get("objectIds").isArray(), "resultStatistics.objectIds должен быть массивом");
        });
    }

    public static void shouldHaveSameObjectIds(
            ValidatableResponseWrapper first,
            ValidatableResponseWrapper second
    ) {
        step("Проверяем независимость полного списка objectIds от пагинации content", () -> {
            Set<String> firstIds = new LinkedHashSet<>(requireIds(splittingObjectsDto(first)));
            Set<String> secondIds = new LinkedHashSet<>(requireIds(splittingObjectsDto(second)));
            TestAssertions.assertEquals(firstIds, secondIds,
                    "objectIds должен содержать один и тот же полный набор при разных page/size");

            Long firstFound = splittingObjectsDto(first).getResultStatistics().getObjectsFound();
            Long secondFound = splittingObjectsDto(second).getResultStatistics().getObjectsFound();
            TestAssertions.assertEquals(firstFound, secondFound,
                    "objectsFound не должен зависеть от размера страницы");
        });
    }

    public static void shouldHaveSameContentAndBaseStatistics(
            ValidatableResponseWrapper responseWithIds,
            ValidatableResponseWrapper responseWithoutIds
    ) {
        step("Проверяем отсутствие влияния returnIds на content и базовую статистику", () -> {
            SplittingObjectsResponseDto withIds = splittingObjectsDto(responseWithIds);
            SplittingObjectsResponseDto withoutIds = splittingObjectsDto(responseWithoutIds);
            TestAssertions.assertEquals(withIds.getTotalPages(), withoutIds.getTotalPages(),
                    "returnIds не должен менять totalPages");
            TestAssertions.assertEquals(withIds.getResultStatistics().getObjectsFound(),
                    withoutIds.getResultStatistics().getObjectsFound(),
                    "returnIds не должен менять objectsFound");
            TestAssertions.assertEquals(withIds.getResultStatistics().getParentObjectsFound(),
                    withoutIds.getResultStatistics().getParentObjectsFound(),
                    "returnIds не должен менять parentObjectsFound");
            TestAssertions.assertEquals(withIds.getContent(), withoutIds.getContent(),
                    "returnIds не должен менять content");
        });
    }

    public static void shouldHavePageContentAtMost(ValidatableResponseWrapper response, int pageSize) {
        step("Проверяем размер страницы content <= " + pageSize, () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            TestAssertions.assertTrue(dto.getContent().size() <= pageSize,
                    "Количество объектов в content не должно превышать size=" + pageSize
                            + ", фактически=" + dto.getContent().size());
        });
    }

    public static void shouldHaveEmptyResultWithoutOptionalStatistics(ValidatableResponseWrapper response) {
        step("Проверяем пустой результат и отсутствие необязательной статистики", () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            TestAssertions.assertEquals(0, dto.getTotalPages(), "Для пустого результата totalPages=0");
            TestAssertions.assertEquals(0L, dto.getResultStatistics().getObjectsFound(),
                    "Для пустого результата objectsFound=0");
            TestAssertions.assertTrue(dto.getContent().isEmpty(), "Для пустого результата content=[]");

            JsonNode statistics = readTree(response).path("resultStatistics");
            TestAssertions.assertFalse(statistics.has("objectIds"),
                    "Для пустого результата objectIds не добавляется");
            TestAssertions.assertFalse(statistics.has("parentObjectsFound"),
                    "Для пустого результата parentObjectsFound не добавляется");
        });
    }

    public static SplittingObjectRuleDto ruleFromFirstKnownField(ValidatableResponseWrapper response) {
        return step("Выбираем из ответа стабильный параметр для фильтрационного сценария", () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            for (List<SplittingObjectFieldDto> object : safeList(dto.getContent())) {
                for (SplittingObjectFieldDto field : safeList(object)) {
                    if (!DataOperatorV2TestDataFactory.hasKnownDataType(field.getCode())) {
                        continue;
                    }
                    List<SplittingObjectFieldValueDto> values = safeList(field.getValues());
                    if (!values.isEmpty() && values.get(0).getValue() != null) {
                        return DataOperatorV2TestDataFactory.equalRule(field.getCode(), values.get(0).getValue());
                    }
                }
            }
            throw new AssertionError("В content не найден параметр с известным dataType для построения фильтра. Ответ: "
                    + response.toResponse().asString());
        });
    }


    public static String firstObjectId(ValidatableResponseWrapper response) {
        return step("Получаем первый objectId из resultStatistics.objectIds", () -> {
            List<String> ids = requireIds(splittingObjectsDto(response));
            TestAssertions.assertFalse(ids.isEmpty(), "Для проверки GET object/{id} нужен хотя бы один объект");
            return ids.get(0);
        });
    }

    public static void shouldHaveDictionaryContract(ValidatableResponseWrapper response) {
        step("Проверяем контракт so-field-values-dicts", () -> {
            SoFieldValuesDictResponseDto dto = soFieldValuesDictDto(response);
            TestAssertions.assertNotNull(dto.getContent(), "Ключ content обязателен");
            dto.getContent().forEach(item -> {
                TestAssertions.assertNotNull(item.getCode(), "У записи справочника обязателен code");
                TestAssertions.assertNotNull(item.getName(), "У записи справочника обязателен name");
                TestAssertions.assertNotNull(item.getDesc(), "У записи справочника обязателен desc");
            });
        });
    }

    public static void shouldHaveResultStatisticsConsistentWithIds(ValidatableResponseWrapper response) {
        step("Проверяем согласованность resultStatistics и objectIds", () -> {
            SplittingObjectsResponseDto dto = splittingObjectsDto(response);
            ResultStatisticsDto statistics = dto.getResultStatistics();
            TestAssertions.assertNotNull(statistics, "resultStatistics обязателен");
            if (statistics.getObjectsFound() > 0) {
                shouldHaveObjectIdsKey(response);
                shouldHaveObjectIdsForAllFoundObjects(response);
            } else {
                shouldNotHaveObjectIdsKey(response);
            }
        });
    }

    private static List<String> requireIds(SplittingObjectsResponseDto dto) {
        TestAssertions.assertNotNull(dto.getResultStatistics(), "Поле resultStatistics обязательно");
        TestAssertions.assertNotNull(dto.getResultStatistics().getObjectIds(),
                "Поле resultStatistics.objectIds должно присутствовать");
        return dto.getResultStatistics().getObjectIds();
    }

    private static JsonNode readTree(ValidatableResponseWrapper response) {
        try {
            return OBJECT_MAPPER.readTree(response.toResponse().asString());
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Ответ не является корректным JSON", exception);
        }
    }

    private static <T> T read(ValidatableResponseWrapper response, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(response.toResponse().asString(), type);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Не удалось десериализовать ответ в " + type.getSimpleName()
                    + ": " + response.toResponse().asString(), exception);
        }
    }

    private static <T> List<T> safeList(List<T> source) {
        return source == null ? new ArrayList<>() : source;
    }
}

