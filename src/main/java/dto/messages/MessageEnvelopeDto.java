package dto.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.ALWAYS)
public class MessageEnvelopeDto {
    /**
     * Сериализованный JSON полезной нагрузки (как строка)
     */
    private String message;
    private List<MessageParamDto> params;
}
