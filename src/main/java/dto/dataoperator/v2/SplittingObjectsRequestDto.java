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
public class SplittingObjectsRequestDto {
    private Integer page;
    private Integer size;
    private Boolean returnIds;
    private String splittingPointCode;
    private Boolean parent;
    private String parentId;
    private List<List<SplittingObjectRuleDto>> rules;

    public static String toJson(SplittingObjectsRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать запрос получения объектов data-operator", exception);
        }
    }
}
