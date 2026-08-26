package dto.dictionaries.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionarySplittingPointsRespDto {
    private Long id;
    private String code;
    private String name;
}
