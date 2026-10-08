package dto.dictionaries.response;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum OperatorCode {

    @JsonProperty("equal") EQUAL,
    @JsonProperty("not_equal") NOT_EQUAL,
    @JsonProperty("more") MORE,
    @JsonProperty("less") LESS,
    @JsonProperty("more_equal") MORE_EQUAL,
    @JsonProperty("less_equal") LESS_EQUAL,
    @JsonProperty("is_null") IS_NULL,
    @JsonProperty("is_not_null") IS_NOT_NULL,
    @JsonProperty("in") IN,
    @JsonProperty("not_in") NOT_IN,
    @JsonProperty("like") LIKE,
    @JsonProperty("not_like") NOT_LIKE,
    @JsonProperty("any") ANY,
    @JsonProperty("not_any") NOT_ANY,
    @JsonProperty("like_all") LIKE_ALL,

    @JsonEnumDefaultValue UNKNOWN
}
