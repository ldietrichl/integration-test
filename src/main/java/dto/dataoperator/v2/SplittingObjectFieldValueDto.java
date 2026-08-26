package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplittingObjectFieldValueDto {
    private String value;
    private String name;
}
