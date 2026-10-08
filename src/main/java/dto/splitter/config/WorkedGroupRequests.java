package dto.splitter.config;

import dto.splitter.common.ParamDto;
import dto.splitter.split.SplitRequestDto;
import dto.splitter.split.SplittingObjectDto;
import java.util.*;

/** Deterministic group/link fixtures for candidate selection, independent of transport and assertions. */
public final class WorkedGroupRequests {
    private WorkedGroupRequests() { }
    public static final String SALT = "24096d2M1e";
    public static final String LEFT = "29840000-0000-0000-0000-000000000001";
    public static final String RIGHT = "29840000-0000-0000-0000-000000000002";
    public static final String UNLINKED = "29840000-0000-0000-0000-000000000099";
    public static final int E1 = 298401, E2 = 298402, E3 = 298403;
    public enum Fixture { SINGLE, COMPLEX, SAME, PRIORITY_REJECT, ID_REJECT, PRIORITY_ALL, TIE, NO_CANDIDATES, MAPPER, MOVED }

    public static LoadConfigRequestDto config(Fixture fixture, String point, long version, int groupCount, boolean reversed) {
        List<ExperimentDto> experiments = new ArrayList<>(switch (fixture) {
            case SINGLE -> List.of(exp(E1, null, List.of(group("A", 0, 2500, 1, "1", "1"),
                    group("B", 2500, 5000, 2, "3", "2"))));
            case MOVED -> List.of(exp(E1, null, List.of(group("A", 0, 2500, 2, "1", "1"),
                    group("B", 2500, 5000, 1, "3", "2"))));
            case MAPPER -> List.of(exp(E1, null, List.of(group("A", 0, 5000, 1, "1", "101"),
                    group("B", 5000, 10000, 2, "3", "202"))));
            case COMPLEX -> List.of(complexFirst(),
                    exp(E2, null, List.of(group("A", 0, 2500, 1, "1", "1"),
                            group("B", 2500, 5000, 2, "3", "2"), group("C", 5000, 7500, 1, "1", "3"))));
            case SAME -> {
                if (groupCount < 1 || groupCount > 3) throw new IllegalArgumentException("1..3 groups required");
                List<GroupDto> groups = new ArrayList<>();
                for (int i = 0; i < groupCount; i++) groups.add(group(String.valueOf((char)('A' + i)),
                        i * 2500, (i + 1) * 2500, 1, i == 1 ? "3" : "1", Integer.toString(i + 1)));
                var experiment = exp(E1, null, groups);
                // The shared-link example has only condition 1; object 2 must not match an orphan condition.
                experiment.setObjectSelectConditions(List.of(condition(1, "1")));
                yield List.of(experiment);
            }
            case PRIORITY_REJECT -> List.of(pair(E1, 1, true), pair(E2, 3, false));
            case ID_REJECT -> List.of(pair(E1, 3, false), pair(E2, 3, true));
            case PRIORITY_ALL -> List.of(pair(E1, 1, true), pair(E2, 3, true));
            case TIE -> List.of(pair(E1, 3, true), pair(E2, 3, true), pair(E3, 3, true));
            case NO_CANDIDATES -> List.of(pair(E1, 1, false), pair(E2, 3, false));
        });
        // S1 REACTIONS examples contain only result; MAPPER requires actionType as well.
        if (point.equals("REACTIONS")) experiments.forEach(e -> e.getGroups().forEach(g ->
                g.getSplittingResults().forEach(r -> r.setResultParams(r.getResultParams().stream()
                        .filter(p -> !p.getParamCode().equals("actionType")).toList()))));
        if (reversed) {
            Collections.reverse(experiments);
            experiments.forEach(e -> {
                e.setGroups(reverse(e.getGroups()));
                e.setObjectSelectConditions(reverse(e.getObjectSelectConditions()));
                e.getGroups().forEach(g -> g.setSplittingResults(reverse(g.getSplittingResults())));
            });
        }
        return new LoadConfigRequestDto(UUID.randomUUID().toString(), UUID.randomUUID().toString(), version,
                true, point, new SplittingConfigDto(experiments));
    }

    private static <T> List<T> reverse(List<T> values) {
        List<T> copy = new ArrayList<>(values); Collections.reverse(copy); return copy;
    }
    private static ExperimentDto complexFirst() {
        var experiment = exp(E1, null, List.of(group("A", 2500, 5000, 1, "1", "21"),
                group("B", 7500, 10000, 2, "3", "22")));
        experiment.setObjectSelectConditions(List.of(condition(1, "2"), condition(2, "1")));
        return experiment;
    }
    private static ExperimentDto pair(int id, int priority, boolean aWorked) {
        return exp(id, priority, List.of(group("A", aWorked ? 0 : 5000, aWorked ? 5000 : 10000,
                        1, "1", Integer.toString(id * 10 + 1)),
                group("B", aWorked ? 5000 : 0, aWorked ? 10000 : 5000,
                        2, "3", Integer.toString(id * 10 + 2))));
    }
    private static ExperimentDto exp(int id, Integer priority, List<GroupDto> groups) {
        return ExperimentDto.builder().id(id).salt(SALT).layerId(priority == null ? null : id)
                .layerPriority(priority).objectSelectConditions(List.of(condition(1, "1"), condition(2, "2")))
                .groups(groups).build();
    }
    private static ObjectSelectConditionDto condition(int id, String value) {
        return ObjectSelectConditionDto.builder().id(id).rules(List.of(List.of(RuleDto.builder()
                .dataType("INTEGER").paramCode("id").paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal").values(List.of(value)).build()))).build();
    }
    private static GroupDto group(String code, int from, int to, int condition, String action, String value) {
        return new GroupDto(code, List.of(new ShareDto(from, to)), List.of(new SplittingResultDto(condition,
                List.of(new ParamDto("actionType", List.of(action), "INTEGER"),
                        new ParamDto("result", List.of(value), "INTEGER")))));
    }
    public static SplitRequestDto request(String splittingId, List<String> objects) {
        return new SplitRequestDto(UUID.randomUUID().toString(), splittingId, null,
                objects.stream().map(id -> new SplittingObjectDto("2984-config-" + id, id,
                        List.of(new ParamDto("id", List.of(id.equals(LEFT) ? "1" : id.equals(RIGHT) ? "2" : "99"),
                                "INTEGER")))).toList());
    }
}
