package dto.configurations.v2;

import dto.enums.RequestSourceSplitConfig;

import java.util.UUID;

public class ConfigurationsV2GenerateSplittingConfigPostRequestDtoBuilder {

    public static ConfigurationsV2GenerateSplittingConfigPostRequestDto buildDefaultDto() {
        return ConfigurationsV2GenerateSplittingConfigPostRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .requestSource(RequestSourceSplitConfig.CONFIG_SERVICE)
                .splittingPoint("MAPPER")
                .build();
    }
}
