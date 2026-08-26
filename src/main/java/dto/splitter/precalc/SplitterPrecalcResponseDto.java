package dto.splitter.precalc;

public class SplitterPrecalcResponseDto {

    private String responseId;
    private Integer soConfigVersion;

    public SplitterPrecalcResponseDto() {
    }

    public SplitterPrecalcResponseDto(String responseId, Integer soConfigVersion) {
        this.responseId = responseId;
        this.soConfigVersion = soConfigVersion;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }

    public Integer getSoConfigVersion() {
        return soConfigVersion;
    }

    public void setSoConfigVersion(Integer soConfigVersion) {
        this.soConfigVersion = soConfigVersion;
    }
}
