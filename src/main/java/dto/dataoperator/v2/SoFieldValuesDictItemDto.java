package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoFieldValuesDictItemDto {
    private String code;
    private String name;
    private List<String> desc;
}
