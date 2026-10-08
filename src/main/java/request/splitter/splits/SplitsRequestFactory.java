package request.splitter.splits;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.splits.SplitRequestDto;

public class SplitsRequestFactory {

    private final ObjectMapper mapper;

    public SplitsRequestFactory() {
        this.mapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public SplitRequestDto build(SplitsParams p) {
        return SplitRequestDto.builder()
                .id(p.getId())
                .type(p.getType())
                .name(p.getName())
                .desc(p.getDesc())
                .status(p.getStatus())
                .salt(p.getSalt())
                .hashAlgorithm(p.getHashAlgorithm())
                .quantum(p.getQuantum())
                .actionType(p.getActionType())
                .startDt(p.getStartDt())
                .startDt(p.getStartDt())
                .endDt(p.getEndDt())
                .autoStart(p.isAutoStart())
                .autoStop(p.isAutoStop())
                .realStartDt(p.getRealStartDt())
                .realEndDt(p.getRealEndDt())
                .createdDt(p.getCreatedDt())
                .createdBy(p.getCreatedBy())
                .updatedDt(p.getUpdatedDt())
                .updatedBy(p.getUpdatedBy())
                .priority(p.getPriority())
                .version(p.getVersion())
                .groups(p.getGroups())
                .build();
    }

    public String toJson(SplitRequestDto dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса messages", e);
        }
    }
}
