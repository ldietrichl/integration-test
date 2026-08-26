package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO только для negative-contract сценариев. Тип Object позволяет передать
 * заведомо несовместимую с Boolean структуру без inline JSON в тесте.
 */
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvalidSplittingObjectsRequestDto {
    private Integer page;
    private Integer size;
    private Object returnIds;
    private String splittingPointCode;
    private Boolean parent;
    private List<List<SplittingObjectRuleDto>> rules;

    public static String toJson(InvalidSplittingObjectsRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать некорректный запрос data-operator", exception);
        }
    }
}
