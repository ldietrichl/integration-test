package dto.splitter.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoadConfigRequestDto {
    private String messageId;
    private String requestId;
    private Long configVersion;
    private Boolean forceConfigLoad;
    private String splittingPointCode;
    private SplittingConfigDto splittingConfig;
}
