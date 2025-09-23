package request;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExperimentParams {

    // Основные переменные из теста
    private String name;
    @Builder.Default private  Long id = null;
    private String salt;
    private long startDt;
    private long endDt;
    @Builder.Default private Long object = null;
    private List<String> cjIds;     // значения фильтра (например: ["103081"])

    @Builder.Default private Long createdBy = null;
    @Builder.Default private Long createdDt = null;
    @Builder.Default private Long updatedBy = 1L;
    @Builder.Default  private Long updatedDt = null;
    @Builder.Default private Long statusChangedBy = 1L;
    @Builder.Default private Object sendings = null;
    @Builder.Default  private Object budget = null;

    // Идентификаторы групп (можно оставить null)
    @Builder.Default private Long groupAId = 3570L;
    @Builder.Default private Long groupBId = 3571L;

    // Настройки групп
    @Builder.Default private int actionTypeId = 2;
    @Builder.Default private int sizeA = 2000;
    @Builder.Default private int sizeB = 2000;
    @Builder.Default private int shareFromA = 0;
    @Builder.Default private int shareToA   = 2000;
    @Builder.Default private int shareFromB = 5000;
    @Builder.Default private int shareToB   = 7000;
    @Builder.Default private boolean baselineB = true; // B — контрольная

    // Конфиг группы A
    @Builder.Default private Long groupConfigId = 2055L;
    @Builder.Default private String configSource = "SELECT";
    @Builder.Default private boolean dynamicConfig = true;

    // Поля корня (то, что реально важно в контракте)
    @Builder.Default private String hashAlgorithm = "MURMURHASH";
    @Builder.Default private int quantum = 10_000;
    @Builder.Default private String compareType = "FULL";
    @Builder.Default private boolean updateMetrics = true;
    @Builder.Default private String purpose = "COMMON";
    @Builder.Default private boolean withCampaign = false;

    // Служебные: objectId/formatId (в успешном примере 7 и 1)
    @Builder.Default private Integer formatId = 1;
    @Builder.Default private Integer objectId = 7;

    // Необязательные поля — по умолчанию null (как в образце)
    private String hypothesisDesc;  // "Du27_01/3" — если нужно
    private String creator;         // "АСтеповой" — если нужно

    // Schedule (опционально)
    private Integer repeatPeriod;
    private Long repeatStartDate;
    private Long repeatStopDate;
    private String saltTextPart;
    private Integer monthShift;
    private Integer taskNumber;
}
