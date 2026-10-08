package dto.splitter.precalc;

import java.util.List;

public class SplitterPrecalcParamDto {

    private String paramCode;
    private List<String> paramValues;
    private String dataType;

    public SplitterPrecalcParamDto() {
    }

    public SplitterPrecalcParamDto(String paramCode, List<String> paramValues, String dataType) {
        this.paramCode = paramCode;
        this.paramValues = paramValues;
        this.dataType = dataType;
    }

    public String getParamCode() {
        return paramCode;
    }

    public void setParamCode(String paramCode) {
        this.paramCode = paramCode;
    }

    public List<String> getParamValues() {
        return paramValues;
    }

    public void setParamValues(List<String> paramValues) {
        this.paramValues = paramValues;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
}
