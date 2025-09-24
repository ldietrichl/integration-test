package dto.dictionaries.response;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum DataType {

    @JsonProperty("INTEGER") INTEGER,
    @JsonProperty("NUMBER") NUMBER,
    @JsonProperty("ENUM") ENUM,
    @JsonProperty("STRING") STRING,
    @JsonProperty("DATE_OR_ENUM_SHIFT") DATE_OR_ENUM_SHIFT,
    @JsonProperty("DATE") DATE,
    @JsonProperty("DATETIME") DATETIME,
    @JsonProperty("BOOLEAN") BOOLEAN,

    @JsonEnumDefaultValue UNKNOWN
}
