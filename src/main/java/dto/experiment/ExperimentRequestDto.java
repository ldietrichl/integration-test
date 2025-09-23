package dto.experiment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.*;


import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.ALWAYS)
public class ExperimentRequestDto {
    // --- корневые поля ---
    private Long id;                 // null при создании
    private String name;
    private Long startDt;
    private Long endDt;

    // в «успешном» примере первые objectId/formatId шли как null,
    // а в конце — с реальными значениями (1 и 7). Поля тут одни; ты задаёшь финальные.
    private Long object;           // например, 7
    private Integer objectId;      // например, 1 objectId;           // например, 7
    private Integer formatId;        // например, 1

    private String salt;

    // служебные "кандидаты на удаление" — в примере null
    private Long createdBy;
    private Long createdDt;
    private Long updatedBy;
    private Long updatedDt;
    private Long statusChangedBy;

    private String hypothesisDesc;   // "Du27_01/3" (пример)
    private String creator;          // "АСтеповой" (пример)
    private Object sendings;         // по контракту — структура; сейчас null
    private Object budget;           // сейчас null

    private String hashAlgorithm;    // "MURMURHASH"
    private Integer quantum;         // 10000
    private String compareType;      // "FULL"

    private List<Object> metrics;    // []
    private List<ExperimentGroup> experimentGroups;

    private Boolean hasNotAgreedCJ;  // false
    private Boolean hasReadyToStartCJ; // false
    private Integer version;         // 4

    private ScheduleParam scheduleParam; // null
    private Boolean startCampaigns;  // false
    private Boolean autoStart;       // false
    private Boolean autoStop;        // false
    private Long startedBy;          // null
    private Long stoppedBy;          // null
    private Long realStartDt;        // null
    private Long realEndDt;          // null

    private List<Object> relations;  // []
    private Object compareConfig;    // null
    private Boolean updateMetrics;   // true
    private String purpose;          // "COMMON"
    private Boolean withCampaign;    // false

    // ===== вложенные классы =====

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class ExperimentGroup {
        private Long id;                 // может быть null
        private String symbolName;       // "A"/"B"
        private String name;             // "Целевая группа"/"Контрольная группа"
        private Integer actionTypeId;    // 2
        private GroupConfig groupConfig; // у B = null
        private Integer size;            // 2000
        private Boolean baseline;        // A=false, B=true

        // в успешном примере это поле было null; оставим nullable
        private List<Long> configSplits; // null

        private Integer shareFrom;       // A:0, B:5000
        private Integer shareTo;         // A:2000, B:7000

        // в образце — []

    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class GroupConfig {
        private Long id;                 // 2055
        private String configSource;     // "SELECT"
        private Boolean dynamicConfig;
        private ConfigQuery configQuery; // содержит include/exclude
        private List<Long> staticConfig;// true
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class ConfigQuery {
        private Boolean allCjComms;          // false
        private List<Sort> sorts;            // []
        private List<Filter> filters;        // [{...}]

        // ИМЕНА ИМЕННО ТАКИЕ — согласно ошибке бэка
        private List<Long> commIdsInclude;   // []
        private List<Long> commIdsExclude;   // []
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class Sort {
        private String parameterCode;    // не используется сейчас
        private String sortDirection;    // ASC/DESC
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class Filter {
        private String parameterCode;    // "cjId"
        private String operatorCode;     // "equal" / "in" и т.д.
        private List<String> values;     // ["103081"]
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(Include.ALWAYS)
    public static class ScheduleParam {
        private Integer repeatPeriod;
        private Long repeatStartDate;
        private Long repeatStopDate;
        private String saltTextPart;
        private Integer monthShift;
        private Integer taskNumber;
    }
}
