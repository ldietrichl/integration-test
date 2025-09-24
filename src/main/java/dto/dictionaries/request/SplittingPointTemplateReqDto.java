package dto.dictionaries.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.ALWAYS) // сериализуем даже null-поля
public class SplittingPointTemplateReqDto {

        private List<String> splittingPointCodes;
        private List<String>  templateCodes;


}
