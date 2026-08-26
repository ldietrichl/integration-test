package dto.dictionaries.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnumValueDto {
    private Long id; // Идентификатор записи в справочнике
    private Integer order; // Порядок отображения
    private String code; // Код значения
    private String name; // Название значения
}
