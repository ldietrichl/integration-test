package request.splitter.splits;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitsParams {
    private long id;
    @Builder.Default
    private String type = "SCG555";
    @Builder.Default
    private String name = "СКГ20525";
    @Builder.Default
    private String desc = "Статичная Контрольная Группа 2025";
    @Builder.Default
    private String status = "IN_PROGRESS";
    @Builder.Default
    private String salt = "stat cg 2024";
    @Builder.Default
    private String hashAlgorithm = "MURMURHASH";
    @Builder.Default
    private int quantum = 10000;
    @Builder.Default
    private int actionType = 3;
    @Builder.Default
    private long startDt = 1704067200000L;
    @Builder.Default
    private long endDt = 1729484318L;
    private boolean autoStart;
    private boolean autoStop;
    @Builder.Default
    private long realStartDt = 1734713634687L;
    private long realEndDt;
    @Builder.Default
    private long createdDt = 1688405817844L;
    @Builder.Default
    private int createdBy = 1;
    @Builder.Default
    private long updatedDt = 1734713641334L;
    @Builder.Default
    private int updatedBy = 1004;
    @Builder.Default
    private int priority = 10;
    @Builder.Default
    private int version = 1;
    @Builder.Default
    private String groups = """
            [{"groupCode": "A","shareFrom": 0,"size": 75}]""";
}
