package util.dataoperator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.dataoperator.response.DataOperatorErrorResponseDto;
import dto.dataoperator.response.SplittingObjectIdsResponseDto;
import dto.dataoperator.response.SplittingObjectsResponseDto;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;

public final class DataOperatorResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private DataOperatorResponseMapper() {
    }

    /**
     * Преобразует успешный ответ splitting-objects-ids в DTO.
     *
     * Спецификация описывает envelope {"ids": [...]}, однако стенд на текущей
     * реализации возвращает корневой JSON-массив [...]. Для функциональных
     * проверок поддерживаем обе формы. Проверка соответствия спецификации
     * выполняется отдельно в контрактном тесте DO-IDS-06.
     */
    public static SplittingObjectIdsResponseDto idsResponse(ValidatableResponseWrapper response) {
        String body = response.toResponse().asString();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root.isArray()) {
                List<String> ids = OBJECT_MAPPER.convertValue(root, STRING_LIST_TYPE);
                return SplittingObjectIdsResponseDto.builder().ids(ids).build();
            }
            if (root.isObject()) {
                return OBJECT_MAPPER.treeToValue(root, SplittingObjectIdsResponseDto.class);
            }
            throw new AssertionError("Успешный ответ Data Operator должен быть JSON-массивом "
                    + "или объектом с полем ids. Body=" + body);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new AssertionError("Не удалось преобразовать ответ Data Operator в DTO "
                    + SplittingObjectIdsResponseDto.class.getSimpleName() + ". Body=" + body, exception);
        }
    }

    public static SplittingObjectsResponseDto objectsResponse(ValidatableResponseWrapper response) {
        return read(response, SplittingObjectsResponseDto.class);
    }

    public static DataOperatorErrorResponseDto errorResponse(ValidatableResponseWrapper response) {
        return read(response, DataOperatorErrorResponseDto.class);
    }

    public static JsonNode responseTree(ValidatableResponseWrapper response) {
        String body = response.toResponse().asString();
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Ответ Data Operator не является корректным JSON. Body=" + body, exception);
        }
    }

    private static <T> T read(ValidatableResponseWrapper response, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(response.toResponse().asString(), type);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Не удалось преобразовать ответ Data Operator в DTO " + type.getSimpleName()
                    + ". Body=" + response.toResponse().asString(), exception);
        }
    }
}
