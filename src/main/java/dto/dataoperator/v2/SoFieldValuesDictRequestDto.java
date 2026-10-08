package dto.dataoperator.v2;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class SoFieldValuesDictRequestDto {
    private String splittingPointCode;
    private String dictParamCode;
    private String search;
    private Integer responseLimit;
}
