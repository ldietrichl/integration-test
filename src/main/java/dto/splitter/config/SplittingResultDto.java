package dto.splitter.config;

import dto.splitter.common.ParamDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplittingResultDto {
    private Integer conditionId;
    private List<ParamDto> resultParams;
}
