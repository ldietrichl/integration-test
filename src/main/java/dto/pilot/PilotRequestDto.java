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
public class PilotRequestDto {
    private Long id;
    private String name;
    private String launchStatus;
    private Long entityTypeId;
    private Long startDt;
    private Long endDt;
    private Long resourceId;
    private Long launchTypeId;
    private List<CommunicationChannel> communicationChannel;
    private Long pilotTypeId;
    private PromotedProduct promotedProduct;
    private Long metricClassId;
    private List<String> initiatorLabel;
    private String goalDescription;
    private String targetAudience;
    private String customerPath;
    private String successMetricDescription;
    private String whyItWillWork;
    private Long targetMetricTypeId;
    private Double baseMetricValue;
    private Double targetMetricValue;
    private Long pilotVarCount;
    private Double targetNPV;
    private Long replicationBase;
    private Long controlGroupPct;
    private Boolean dynamicCGRequired;
    private Long calcPilotGroupSize;
    private Long estimPilotClients;
    private Long finalClientAllocReq;
    private Double addlRespRate;
    private Double addlTransPilot;
    private Double addlNPVPilot;
    private Double addlTransRollout;
    private Double addlNPVRollout;
    private List<String> validatorLabel;
    private String businessPreApproverEmployeeId;
    private String businessApproverEmployeeId;
    private Comment comments;
    private String priorityCode;
    private Integer assignOrder;
    private Boolean fastTrackSwitcher;
    private Boolean sendToOptimizerSwitcher;
    private Boolean expPromSwitcher;
    private Boolean expDkgSwitcher;
    private List<Long> linkedConfigComIds;
    private List<Long> linkedCampaignIds;
    private List<Long> linkedExperimentPromIds;
    private List<Long> linkedExperimentDkgIds;
    private List<LinkedPilot> linkedPilotIds;
    private String pilotExtensionDescription;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CommunicationChannel {
        private Long id;
        private String name;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PromotedProduct {
        private Long id;
        private String name;
        private String productName;
        private String textProductId;
        private String textOfferId;
        private String targetAction;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Comment {
        private String name;
        private String commentText;
        private Long dateComment;
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkedPilot {
        private Long id;
        private String name;
    }

    public static String toJson(PilotRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса пилота", e);
        }
    }
}
