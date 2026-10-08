package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoFieldValuesDictResponseDto {
    private Boolean moreResultsAvailable;
    private List<SoFieldValuesDictItemDto> content;
}
