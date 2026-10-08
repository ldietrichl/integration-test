package dto.experiments.layers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LayerRegistryGetRequestDto {

    private int page;
    private int size;
    private List<LayerRegistryGetRequestDto.Filter> filters;
    private List<LayerRegistryGetRequestDto.Sort> sorts;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class Filter {
        private String code;
        private String operator;
        private List<String> values;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class Sort {
        private String code;
        private String direction;
    }
}
