package dto.splitter.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDto {
    private String code;
    private List<ShareDto> shares;
    private List<SplittingResultDto> splittingResults;
}
