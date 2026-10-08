package dto.splitter.splits;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude
public class SplitRequestDto {
    private Long id;
    private String type;
    private String name;
    private String desc;
    private String status;
    private String salt;
    private String hashAlgorithm;
    private int quantum;
    private int actionType;
    private long startDt;
    private long endDt;
    private boolean autoStart;
    private boolean autoStop;
    private long realStartDt;
    private long realEndDt;
    private long createdDt;
    private int createdBy;
    private long updatedDt;
    private int updatedBy;
    private int priority;
    private int version;
    private String groups;
}
