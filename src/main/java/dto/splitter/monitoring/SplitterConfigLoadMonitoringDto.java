package dto.splitter.monitoring;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitterConfigLoadMonitoringDto {
    private String message;
    private String function;
    private String result;
    private String resultDetails;
    private Long completedTimestamp;
    private String loadMethod;
    private String requestIdIn;

    @JsonAlias({"splittingPoing", "splittingPointCode", "splittingPoin"})
    private String splittingPoint;

    private Long currentConfigVersion;
    private Long newConfigVersion;
    private Long soConfigVersion;
    private Integer notLinkedObjects;
    private Integer totalObjects;
    private Integer linkedExps;

    /**
     * Старое ошибочное имя поля. По EXPLAB-2400 оно не должно формироваться вместо linkedExps.
     */
    private Integer notLinkedExps;

    private Integer totalExps;
    private String service;
}
