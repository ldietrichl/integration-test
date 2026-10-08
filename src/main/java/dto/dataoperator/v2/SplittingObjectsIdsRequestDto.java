package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplittingObjectsIdsRequestDto {
    private String splittingPointCode;
    private Boolean parent;
    private List<List<SplittingObjectRuleDto>> rules;

    public static String toJson(SplittingObjectsIdsRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать запрос идентификаторов data-operator", exception);
        }
    }
}
