package dto.experiments.v2.registry;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.enums.OperatorCodeExpression;
import dto.enums.SortsDirections;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ExperimentsV2RegistryPostRequestDto {
    private Integer page; // обязательный параметр
    private Integer size; // обязательный параметр
    private String search; // не обязательный параметр
    private List<List<Filters>> filters; // не обязательный параметр
    private List<Sorts> sorts; // не обязательный параметр

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public static class Filters {
        private String paramCode; // обязательный параметр
        private OperatorCodeExpression operatorCode; // обязательный параметр
        private List<String> paramValues; // обязательный параметр

        @JsonGetter("operatorCode")
        public String getOperatorCode() {
            return operatorCode.getValue();
        }
    }

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public static class Sorts {
        private String paramCode; // обязательный параметр
        private SortsDirections direction; // обязательный параметр
    }

    public static String toJson(ExperimentsV2RegistryPostRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.ALWAYS).writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса", e);
        }
    }
}
