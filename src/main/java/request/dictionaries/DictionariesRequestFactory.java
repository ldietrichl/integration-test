package request.dictionaries;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import dto.dictionaries.request.OperatorsReqDto;
import dto.dictionaries.request.SplittingPointTemplateReqDto;


public class DictionariesRequestFactory {

    private final ObjectMapper mapper;

    public DictionariesRequestFactory() {
        // сериализуем null-поля
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    /** Построить DTO по параметрам. */
    public ExpressionParameterDictReqDto buildDto(DictionariesParams p) {

        // ---- корневой объект ----
        var dtoBuilder = ExpressionParameterDictReqDto.builder()
                                .formСode(p.getFormСode());
        return dtoBuilder.build();
    }

    public OperatorsReqDto buildOperatorsDto(DictionariesParams p) {
        return OperatorsReqDto.builder()
                .operatorCodes(p.getOperatorCodes())
                .build();
    }


    public SplittingPointTemplateReqDto buildSplittingPointTemplateDto(DictionariesParams p) {
        return SplittingPointTemplateReqDto.builder()
                .splittingPointCodes(p.getSplittingPointCodes())
                .templateCodes(p.getTemplateCodes())
                .build();
    }

    /** Сериализовать в JSON-строку (включая null-поля). */
    public String toJson(OperatorsReqDto dto) {
        try {
            return mapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса", e);
        }
    }
}