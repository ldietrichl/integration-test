package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplittingObjectFieldDto {
    private String code;
    private List<SplittingObjectFieldValueDto> values;
}
