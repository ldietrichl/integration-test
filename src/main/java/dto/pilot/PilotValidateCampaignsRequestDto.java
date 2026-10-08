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
public class PilotValidateCampaignsRequestDto {
    private List<CampaignRef> ids;
    private Long finalClientAllocReq;
    private Long productId;
    private String textOfferId;
    private List<Long> channels;
    private Long startDt;
    private Long endDt;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CampaignRef {
        private Long cjId;
        private Long configComId;
    }

    public static String toJson(PilotValidateCampaignsRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса валидации кампаний пилота", e);
        }
    }
}
