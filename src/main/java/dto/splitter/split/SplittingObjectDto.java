package dto.splitter.split;

import dto.splitter.common.ParamDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplittingObjectDto {

    /**
     * Идентификатор объекта в таблице предрасчета.
     * Для обычного split может отсутствовать.
     */
    private String uniqueConfigurationId;

    /**
     * Бизнес-идентификатор объекта в ответе split.
     */
    private String objectId;

    private List<ParamDto> objectParams;
}
