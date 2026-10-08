package dto.dataoperator.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SoDictByParamsRequestDto {
    private String splittingPointCode;
    private List<InputParam> inParams;
    private List<String> outParams;

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputParam {
        private String paramCode;
        private List<String> paramValues;
    }

    public static String toJson(SoDictByParamsRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(dto);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сериализовать запрос справочника по параметрам", exception);
        }
    }
}
