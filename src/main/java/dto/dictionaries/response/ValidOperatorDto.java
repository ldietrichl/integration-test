package dto.dictionaries.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


// Допустимый оператор для параметра.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidOperatorDto {
    private Long id;
    private OperatorCode code; // Код оператора (enum)
    private String name; // Название (например, "IN")
    private String description; // Описание
    private Boolean isMultiple; // Множественный выбор
    private Boolean orderFlag; // Использование в сортировке
    private Boolean filterFlag; // Использование в фильтрации
    private List<EnumValueDto> enumValues; // Значения для ENUM
}
