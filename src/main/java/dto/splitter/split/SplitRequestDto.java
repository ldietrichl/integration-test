package dto.splitter.split;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import dto.splitter.common.ParamDto;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitRequestDto {
    private String requestId;
    private String splittingId;
    private List<ParamDto> requestParams;
    private List<SplittingObjectDto> splittingObjects;
}
