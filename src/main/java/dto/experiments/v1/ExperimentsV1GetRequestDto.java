package dto.experiments.v1;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.enums.Boolean;
import dto.enums.ExperimentsStatusesV1;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ExperimentsV1GetRequestDto {
    private String page; // Номер страницы. Нумерация начинается с 0
    private String size; // Количество результатов на странице
    private String name;
    private String salt;
    private Boolean exact; // Точное совпадение
    private  List<ExperimentsStatusesV1> statuses;

    @JsonGetter("exact")
    public String getExact() {
        return exact != null ? exact.getValue() : null;
    }

    @JsonGetter("statuses")
    public String getStatuses() {
        return statuses != null ?
                statuses.stream().map(ExperimentsStatusesV1::getValue).collect(Collectors.joining(", ")) : null;
    }

    static public Map<String, Object> toMap(ExperimentsV1GetRequestDto builder) {
        return new ObjectMapper().convertValue(builder, new TypeReference<Map<String, Object>>() {});
    }
}
