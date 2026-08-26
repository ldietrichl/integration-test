package dto.splitter.precalc;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SplitterPrecalcRequestDto {

    private String requestId;
    private Integer soConfigVersion;
    private List<SplitterPrecalcObjectDto> splittingObjects;

    public SplitterPrecalcRequestDto() {
    }

    public SplitterPrecalcRequestDto(String requestId, Integer soConfigVersion, List<SplitterPrecalcObjectDto> splittingObjects) {
        this.requestId = requestId;
        this.soConfigVersion = soConfigVersion;
        this.splittingObjects = splittingObjects;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Integer getSoConfigVersion() {
        return soConfigVersion;
    }

    public void setSoConfigVersion(Integer soConfigVersion) {
        this.soConfigVersion = soConfigVersion;
    }

    public List<SplitterPrecalcObjectDto> getSplittingObjects() {
        return splittingObjects;
    }

    public void setSplittingObjects(List<SplitterPrecalcObjectDto> splittingObjects) {
        this.splittingObjects = splittingObjects;
    }
}
