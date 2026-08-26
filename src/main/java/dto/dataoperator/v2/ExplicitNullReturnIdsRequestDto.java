package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO для проверки явного JSON null в returnIds. Остальные необязательные поля
 * могут быть исключены, но returnIds всегда сериализуется.
 */
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExplicitNullReturnIdsRequestDto {
    private Integer page;
    private Integer size;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Boolean returnIds;

    private String splittingPointCode;
    private Boolean parent;
    private String parentId;
    private List<List<SplittingObjectRuleDto>> rules;

    public static String toJson(ExplicitNullReturnIdsRequestDto dto) {
        try {
            return new ObjectMapper().writeValueAsString(dto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать запрос с явным returnIds=null", exception);
        }
    }
}
