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
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MessagePayloadDto {
    private String id;                 // UUID
    private Integer metamodelVersion;  // <== ОБЯЗАТЕЛЬНО
    private String module;
    private String metamodelHash;
    private Long createdAt;
    private String session;
    private String userLogin;
    private String userName;
    private String userNode;
    private String nodeId;
    private String sourceSystem;
    private String requestId;
    private String eventCode;
    private String name;
}