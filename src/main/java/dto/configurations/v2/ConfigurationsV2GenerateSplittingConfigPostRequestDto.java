package dto.configurations.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import dto.enums.RequestSourceSplitConfig;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationsV2GenerateSplittingConfigPostRequestDto {
    // все поля обязательны
    private String requestId; //UUID
    private RequestSourceSplitConfig requestSource;
    private String splittingPoint;
}
