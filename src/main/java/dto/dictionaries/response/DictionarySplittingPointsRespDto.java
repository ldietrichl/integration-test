package dto.dictionaries.response;

import lombok.*;


@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DictionarySplittingPointsRespDto {
    private Long id;
    private String code;
    private String name;
}
