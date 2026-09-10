package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Request names follow the v7 example; response names are checked separately. */
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplittingObjectsLinksRequestDto {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String splittingPointCode;
    private List<String> objectIds;
    private List<String> parentObjectIds;
    private List<Experiment> exps;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Experiment {
        private Long expId;
        private List<Condition> objectsSelectConditions;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Condition {
        // Integer permits negative tests outside int16 without client-side truncation.
        private Integer number;
        private List<List<SplittingObjectRuleDto>> rules;
    }

    public static String toJson(SplittingObjectsLinksRequestDto request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Не удалось сериализовать запрос splitting-objects-links", exception);
        }
    }
}
