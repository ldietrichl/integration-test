package dto.splitter.precalc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
public class SplitterPrecalcObjectDto {

    private String uniqueConfigurationId;
    private List<SplitterPrecalcParamDto> objectParams;

    public SplitterPrecalcObjectDto() {
    }

    public SplitterPrecalcObjectDto(String uniqueConfigurationId, List<SplitterPrecalcParamDto> objectParams) {
        this.uniqueConfigurationId = uniqueConfigurationId;
        this.objectParams = objectParams;
    }

    public String getUniqueConfigurationId() {
        return uniqueConfigurationId;
    }

    public void setUniqueConfigurationId(String uniqueConfigurationId) {
        this.uniqueConfigurationId = uniqueConfigurationId;
    }

    public List<SplitterPrecalcParamDto> getObjectParams() {
        return objectParams;
    }

    public void setObjectParams(List<SplitterPrecalcParamDto> objectParams) {
        this.objectParams = objectParams;
    }
}
