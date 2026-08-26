package dto.experiments.v2.statuschange;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompleteActionRequestDto {
    private String requestId;
    private StatusChangeResult result;

    @JsonProperty("result_details")
    private String resultDetails;
}
