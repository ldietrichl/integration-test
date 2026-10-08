package dto.experiments.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExperimentV2PostRequestDto {
    private Long id; //необязательный
    private String name; //обязательный
    private String splittingPointCode; //обязательный
    private String expTemplateCode; //обязательный
    private Long startDt; //обязательный
    private Long endDt; //обязательный
    private String salt; //обязательный
    private String hypothesisDesc; //необязательный
    private String creator; //необязательный
    private Long layerId; //необязательный
    private String hashAlgorithm; //обязательный
    private Long quantum; //обязательный
    private List<Metric> metrics; //обязательный, может быть пустым

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class Metric {
        private Long id;
        private Boolean targetFlag;
        private List<ParamValues> paramValues;

        @Data
        @Builder(toBuilder = true)
        @JsonInclude
        public static class ParamValues {
            private Long id;
            private Value value;

            @Data
            @Builder(toBuilder = true)
            @JsonInclude
            public static class Value {
                private String dtInputType;
                private String value;
                private String shift;

            }
        }
    }

    private List<ObjectSelectCondition> objectSelectConditions; //обязательный, не может быть пустым

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class ObjectSelectCondition {
        private Integer number;
        private String userCondition;
        private String formCode;
        private List<List<Rule>> rules;

        @Data
        @Builder(toBuilder = true)
        @JsonInclude
        public static class Rule {
            private String dataType;
            private String parameterCode;
            private String operatorCode;
            private List<String> values;
        }
    }

    private List<ExperimentGroups> experimentGroups; //обязательный, как минимум одна группа

    @Data
    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExperimentGroups {
        private String name;
        private String symbolName;
        private List<Share> shares;
        private int size;
        private boolean baseline;
        private List<SplittingResult> splittingResults;

        @Data
        @Builder(toBuilder = true)
        @JsonInclude
        public static class Share {
            private int shareFrom;
            private int shareTo;
        }

        @Data
        @Builder(toBuilder = true)
        @JsonInclude
        public static class SplittingResult {
            private int conditionNumber;
            private String userResult;
            private List<ResultParam> resultParams;

            @Data
            @Builder(toBuilder = true)
            @JsonInclude
            public static class ResultParam {
                private String paramCode;
                private List<String> paramValue;
                private String dataType;
            }
        }
    }

    private ScheduleParam scheduleParam; //необязательный

    @Data
    @Builder(toBuilder = true)
    @JsonInclude
    public static class ScheduleParam {
        private Long repeatPeriod;
        private Long repeatStartDate;
        private Long repeatStopDate;
        private String saltTextPart;
        private Integer monthShift;
        private Long taskNumber;
    }

    private Boolean autoStart; //необязательный
    private Boolean autoStop; //необязательный
    private Boolean updateMetrics; //необязательный

    public static String toJson(ExperimentV2PostRequestDto dto) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL).writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать тело запроса", e);
        }
    }
}
