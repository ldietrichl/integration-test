package request.dictionaries;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictionariesParams {

    @Builder.Default
    private String formCode = "MAPPER_OBJECT_SELECT";

    @Builder.Default
    private List<String> operatorCodes = List.of("in");

    @Builder.Default
    private List<String>  splittingPointCodes = List.of("MAPPER_OBJECT_SELECT");

    @Builder.Default
    private List<String>  templateCodes = List.of("PILOT");

}
