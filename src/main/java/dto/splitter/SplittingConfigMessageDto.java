package dto.splitter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@JsonInclude
@JsonIgnoreProperties(ignoreUnknown = true)
public record SplittingConfigMessageDto(
        String messageId,
        String requestId,
        Long configVersion,
        Boolean forceConfigLoad,
        String splittingPointCode,
        JsonNode splittingConfig
) {}