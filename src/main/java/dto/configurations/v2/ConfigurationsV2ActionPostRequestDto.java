package dto.configurations.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import dto.enums.Action;
import dto.enums.RequestSource;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationsV2ActionPostRequestDto {
    // все поля обязательны
    private String requestId; //UUID
    private RequestSource requestSource;
    private String splittingPoint;
    private Long expId;
    private Action action;
}
