package dto.experiments.id.status;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import dto.enums.Boolean;
import dto.enums.ExperimentsStatusesV1;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ExperimentsPutRequestDto {
    private List<ExperimentsStatusesV1> status;
    private String comment;
    private Boolean slave;
    private Boolean ignoreWarnings;
    private Boolean startCampaigns;

    @JsonGetter("status")
    public String getStatus() {
        return status != null ?
                String.join(", ", status.stream().map(ExperimentsStatusesV1::getValue).toList()) : null;
    }

    @JsonGetter("slave")
    public String getSlave() {
        return slave.getValue();
    }

    @JsonGetter("ignoreWarnings")
    public String getIgnoreWarnings() {
        return ignoreWarnings.getValue();
    }

    @JsonGetter("startCampaigns")
    public String getStartCampaigns() {
        return startCampaigns.getValue();
    }
}
