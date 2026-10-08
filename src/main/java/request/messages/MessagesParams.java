package request.messages;

import dto.messages.MessageParamDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagesParams {
    // ---- payload ----
    @Builder.Default
    private String id = "73e7ee3d-002e-429c-b57f-89c122b6beea";
    @Builder.Default
    private Integer metamodelVersion = 2;
    @Builder.Default
    private String module = "CIO796369_ExpLab";
    @Builder.Default
    private String metamodelHash = "fe557e985ecab7d524b9e60796061fd4";
    @Builder.Default
    private Long createdAt = 1742868468L;
    @Builder.Default
    private String session = "b368a921-6027-4bf0-a3c6-b34886d05594";
    @Builder.Default
    private String userLogin = "081540872";
    @Builder.Default
    private String userName = "Иван Иванович Иванов";
    @Builder.Default
    private String userNode = "10.62.234.1:443";
    @Builder.Default
    private String nodeId = "tvlds-ter025578.delta.sbrf.ru 29.65.212.215";
    @Builder.Default
    private String sourceSystem = "CI02047903";
    @Builder.Default
    private String requestId = "cfd3f1ad40f008fea72e06901ff722bb";
    @Builder.Default
    private String eventCode = "expCreated";
    @Builder.Default
    private String name = "Создание эксперимента";

    // ---- params ----
    @Builder.Default
    private List<MessageParamDto> params = List.of(
            MessageParamDto.builder()
                    .name("id")
                    .value("12345")
                    .build()
    );
}
