package dto.splitter.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectSelectConditionDto {
    private Integer id;
    private List<List<RuleDto>> rules;
}
