package dto.dataoperator.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import dto.dataoperator.DataOperatorRuleDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplittingObjectsRequestDto {
    private Integer page;
    private Integer size;
    private Boolean returnIds;
    private String splittingPointCode;
    private Boolean parent;
    private String parentId;
    private List<List<DataOperatorRuleDto>> rules;
}
