package dto.configurations.v2;

import dto.enums.Action;
import dto.enums.RequestSource;

import java.util.UUID;

public class ConfigurationsV2ActionPostRequestDtoBuilder {

    public static ConfigurationsV2ActionPostRequestDto buildDefaultConfigsPostActionRequestDto(Long expId) {
        return ConfigurationsV2ActionPostRequestDto.builder()
                .requestId(String.valueOf(UUID.randomUUID()))
                .requestSource(RequestSource.UI)
                .splittingPoint("MAPPER")
                .expId(expId)
                .action(Action.START)
                .build();
    }
}
