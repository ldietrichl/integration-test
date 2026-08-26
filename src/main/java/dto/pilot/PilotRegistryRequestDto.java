package dto.pilot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PilotRegistryRequestDto {
    private Integer page;
    private Integer size;
    private String search;
    private List<List<Filter>> filters;
    private List<Sort> sorts;
    private Boolean isOwn;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Filter {
        private String paramCode;
        private String operatorCode;
        private List<String> paramValues;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Sort {
        private String paramCode;
        private String direction;
    }

    public static String toJson(PilotRegistryRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса реестра пилотов", e);
        }
    }
}
