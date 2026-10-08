package dto.experiments.v2.layers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LayerRegistryV2RequestDto {
    private Integer page;
    private Integer size;
    private String search;
    private String splittingPointCode;
    private List<List<Filter>> filters;
    private List<Sort> sorts;

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

    public static String toJson(LayerRegistryV2RequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса реестра слоев v2", e);
        }
    }
}
