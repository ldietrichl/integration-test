package dto.splitter.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentDto {
    private Integer id;
    private String purpose;
    private String salt;
    private Integer layerId;
    private Integer layerPriority;
    private List<ObjectSelectConditionDto> objectSelectConditions;
    private List<GroupDto> groups;
}
