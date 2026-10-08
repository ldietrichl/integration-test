package request.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.messages.MessageEnvelopeDto;
import dto.messages.MessageParamDto;
import dto.messages.MessagePayloadDto;

import java.util.List;

public class MessagesRequestFactory {

    private final ObjectMapper mapper;

    public MessagesRequestFactory() {
        this.mapper = new ObjectMapper()
                // как в проекте: сериализуем null-поля
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    /** Построить DTO конверта, где message — экранированная JSON-строка. */
    public MessageEnvelopeDto build(MessagesParams p) {
        // 1) собираем payload
        MessagePayloadDto payload = MessagePayloadDto.builder()
                .id(p.getId())
                .metamodelVersion(p.getMetamodelVersion())
                .module(p.getModule())
                .metamodelHash(p.getMetamodelHash())
                .createdAt(p.getCreatedAt())
                .session(p.getSession())
                .userLogin(p.getUserLogin())
                .userName(p.getUserName())
                .userNode(p.getUserNode())
                .nodeId(p.getNodeId())
                .sourceSystem(p.getSourceSystem())
                .requestId(p.getRequestId())
                .eventCode(p.getEventCode())
                .name(p.getName())
                .build();

        // 2) сериализуем payload в строку; при сериализации Envelope строка попадёт как экранированный JSON
        String payloadAsString;
        try {
            payloadAsString = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать message payload", e);
        }

        // 3) копируем params
        List<MessageParamDto> params = p.getParams();

        // 4) собираем конверт
        return MessageEnvelopeDto.builder()
                .message(payloadAsString)
                .params(params)
                .build();
    }

    /** Сериализовать весь envelope в JSON-строку (включая null-поля). */
    public String toJson(MessageEnvelopeDto dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса messages", e);
        }
    }

    /** Удобный шорткат: по Params сразу получить готовый JSON тела запроса. */
    public String toJson(MessagesParams p) {
        return toJson(build(p));
    }
}
