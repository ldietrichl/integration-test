package dto.splitter.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParamDto {
    private String paramCode;
    private List<String> paramValues;
    private String dataType;
}
