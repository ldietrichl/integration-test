package dto.layers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude
public class LayerRegistryRequestDto {

    private int page;
    private int size;
    private List<Filter> filters;
    private List<Sort> sorts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude
    public static class Filter {
        private String code;
        private String operator;
        private List<String> values;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude
    public static class Sort {
        private String code;
        private String direction;
    }
}
