package dto.pilot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PilotStatusUpdateRequestDto {
    private String launchStatus;
    private Long businessPreApproverId;
    private Long businessApproverId;
    private PilotRequestDto.Comment comments;
    private List<String> initiatorLabel;
    private List<String> validatorLabel;

    public static String toJson(PilotStatusUpdateRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса изменения статуса пилота", e);
        }
    }
}
