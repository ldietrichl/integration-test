package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SplittingObjectRuleDto {
    private String dataType;
    private String parameterCode;
    private String operatorCode;
    private List<String> values;
}
