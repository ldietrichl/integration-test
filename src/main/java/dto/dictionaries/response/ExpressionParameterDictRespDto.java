package dto.dictionaries.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Запись справочника параметров (корневой элемент массива ответа).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpressionParameterDictRespDto {

private Long id;
private String formCode; // Код формы ???
private String paramCode; // Код параметра
private String paramName; // Название параметра
private String paramDescription; // Описание параметра
private DataType dataType; // Тип данных (enum)

private String valueSource; // Внешний источник значений (если есть)
private Boolean sourceSearch; // Признак поиска в источнике
private Integer sourceRowCount; // Кол-во строк в списке источника
private Boolean disableRepeat; // Запрет повтора значения в фильтре

private List<ValidOperatorDto> validOperators; // Допустимые операторы для параметра
}
