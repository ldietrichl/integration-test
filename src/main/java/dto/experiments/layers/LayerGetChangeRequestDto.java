package dto.experiments.layers;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LayerGetChangeRequestDto {

    private Long id;
    private String name;
    private String description;
    private int priority;
    private long startDt;
    private long endDt;
    private String salt;
    private List<Share> shares;
    private String splittingPointCode;
    private Integer sourceExp;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class Share {
        private long shareFrom;
        private long shareTo;
    }
}
