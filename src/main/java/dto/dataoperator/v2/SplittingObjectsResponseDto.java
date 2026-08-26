package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplittingObjectsResponseDto {
    private Integer totalPages;
    private ResultStatisticsDto resultStatistics;
    private List<List<SplittingObjectFieldDto>> content;
}
