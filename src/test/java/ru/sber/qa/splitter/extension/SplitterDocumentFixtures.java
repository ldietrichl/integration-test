package ru.sber.qa.splitter.extension;

import steps.flow.splitter.workedgroup.WorkedGroupSteps;

import dto.splitter.config.*;
import dto.splitter.split.SplitRequestDto;
import java.util.*;
import java.util.stream.Stream;

/** Explicit document expectations; no service/SDK selection algorithm is used as the oracle. */
final class SplitterDocumentFixtures extends WorkedGroupSteps {
    enum Family { DIFFERENT_CONDITIONS, SAME_CONDITION, MULTI_OBJECT_GROUPS, ALTERNATIVE_MATRIX, LAYERS, FILTERED_ACTION }
    record Case(Family family, int from, int to) {
        @Override public String toString() { return family + "-" + from + "-" + to; }
    }
    record Row(long expId, String group, int condition, String finalGroup, boolean alternative) {
        String key() { return expId + "/" + group + "/" + condition; }
    }
    record ObjectResult(List<Row> main, List<Row> restAll, List<Row> reportAll) {
        ObjectResult { main = List.copyOf(main); restAll = List.copyOf(restAll); reportAll = List.copyOf(reportAll); }
    }
    record Fixture(Case scenario, boolean reactions, LoadConfigRequestDto config, SplitRequestDto request,
                   long spread, Map<String, ObjectResult> objects) {
        Fixture { objects = Collections.unmodifiableMap(new LinkedHashMap<>(objects)); }
        ExperimentDto experiment(long id) {
            return config.getSplittingConfig().getExperiments().stream().filter(e -> e.getId().longValue() == id)
                    .findFirst().orElseThrow(() -> new AssertionError("Unknown fixture experiment " + id));
        }
    }

    static Stream<Case> commonCases() {
        Stream<Case> groups = Stream.of(Family.DIFFERENT_CONDITIONS, Family.SAME_CONDITION, Family.MULTI_OBJECT_GROUPS)
                .flatMap(f -> Stream.of(0, 2500, 5000, 7500).map(from -> new Case(f, from, from + 2500)));
        return Stream.concat(groups, Stream.of(0, 500, 1000, 1500, 2500, 3000, 3500, 4000, 5000, 5500, 6000, 6500)
                .map(from -> new Case(Family.ALTERNATIVE_MATRIX, from, from + 500)));
    }
    static Stream<Case> layerCases() {
        return Stream.of(0, 2500, 5000, 7500).map(from -> new Case(Family.LAYERS, from, from + 2500));
    }
    static Stream<Case> filteredCases() {
        return Stream.of(new Case(Family.FILTERED_ACTION, 0, 5000), new Case(Family.FILTERED_ACTION, 5000, 10000));
    }

    static boolean expectedFiltered(Fixture fixture, ObjectResult object) {
        if (fixture.reactions() || object.main().isEmpty()) return false;
        // Fixed managed profile: filtering enabled, alternatives suppressed, actionType in [2,4].
        // Only fixture expectations are used, never flags/MAIN read from the service response.
        if (object.reportAll().stream().anyMatch(Row::alternative)) return true;
        return object.main().stream().anyMatch(row -> fixture.experiment(row.expId()).getGroups().stream()
                .filter(group -> group.getCode().equals(row.group()))
                .flatMap(group -> group.getSplittingResults().stream())
                .filter(result -> result.getConditionId() == row.condition())
                .flatMap(result -> result.getResultParams().stream())
                .anyMatch(param -> "actionType".equals(param.getParamCode()) && "INTEGER".equals(param.getDataType())
                        && param.getParamValues().stream().anyMatch(Set.of("2", "4")::contains)));
    }
    Fixture create(Case test, boolean reactions, long version) {
        return switch (test.family()) {
            case DIFFERENT_CONDITIONS, SAME_CONDITION, MULTI_OBJECT_GROUPS -> groups(test, reactions, version);
            case ALTERNATIVE_MATRIX -> alternatives(test, reactions, version);
            case LAYERS -> layers(test, reactions, version);
            case FILTERED_ACTION -> filtered(test, reactions, version);
        };
    }
    private Fixture filtered(Case test, boolean reactions, long version) {
        if (reactions) throw new IllegalArgumentException("Filtered action matrix is MAPPER-only");
        var exp = experiment(269231, SALT_2690,
                List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, 5000), 1, "2", "2"),
                        groupWithDocResult("B", shares(5000, 10000), 1, "4", "4")));
        String worked = test.from() == 0 ? "A" : "B";
        List<Row> all = List.of(new Row(269231, "A", 1, worked, false), new Row(269231, "B", 1, worked, false));
        List<Row> main = List.of(all.get(test.from() == 0 ? 0 : 1));
        var objects = new LinkedHashMap<String, ObjectResult>();
        objects.put(LEFT_OBJECT_ID, new ObjectResult(main, main, all));
        objects.put(SINGLE_OBJECT_ID, new ObjectResult(List.of(), List.of(), List.of()));
        return finish(test, false, version, List.of(exp), objects,
                object(LEFT_OBJECT_ID, param("segment", "2690", "INTEGER")),
                object(SINGLE_OBJECT_ID, param("unlinked", "1", "INTEGER")));
    }
    private Fixture finish(Case test, boolean reactions, long version, List<ExperimentDto> experiments,
                           Map<String, ObjectResult> objects, dto.splitter.split.SplittingObjectDto... requestObjects) {
        String id = splittingIdForRange("2690-DOC-" + test, SALT_2690, test.from(), test.to());
        var config = configFor(reactions ? EndpointMode.REACTIONS : EndpointMode.MAPPER, version,
                experiments.toArray(ExperimentDto[]::new));
        return new Fixture(test, reactions, config, splitRequest(id, requestObjects), spread(SALT_2690, id), objects);
    }
    private Fixture groups(Case test, boolean reactions, long version) {
        boolean multi = test.family() == Family.MULTI_OBJECT_GROUPS;
        boolean same = test.family() == Family.SAME_CONDITION;
        int worked = test.from() / 2500;
        String finalGroup = worked == 3 ? null : String.valueOf((char) ('A' + worked));
        var conditions = new ArrayList<ObjectSelectConditionDto>();
        int count = multi ? 2 : same ? 1 : 3;
        for (int i = 1; i <= count; i++) conditions.add(objectParamEqualsCondition(i,
                multi ? "side" : "segment", multi ? Integer.toString(i) : "2690", "INTEGER"));
        var groups = new ArrayList<GroupDto>();
        for (int i = 0; i < 3; i++) {
            var results = new ArrayList<SplittingResultDto>();
            for (int side = 1; side <= (multi ? 2 : 1); side++) {
                int condition = multi ? side : same ? 1 : i + 1;
                results.add(i == 2 ? resultWithParams(condition) : resultWithParams(condition,
                        param("actionType", i == 0 ? "1" : "3", "INTEGER"),
                        param("result", Integer.toString(side * 100 + i + 1), "INTEGER")));
            }
            groups.add(group(String.valueOf((char) ('A' + i)), shares(i * 2500, (i + 1) * 2500), results));
        }
        var experiment = experiment(269201, SALT_2690, conditions, groups);
        var objects = new LinkedHashMap<String, ObjectResult>();
        var requestObjects = new ArrayList<dto.splitter.split.SplittingObjectDto>();
        for (int side = 1; side <= (multi ? 2 : 1); side++) {
            String objectId = side == 1 ? LEFT_OBJECT_ID : RIGHT_OBJECT_ID;
            var all = new ArrayList<Row>();
            for (int i = 0; i < 3; i++) all.add(new Row(269201, String.valueOf((char) ('A' + i)),
                    multi ? side : same ? 1 : i + 1, finalGroup, false));
            List<Row> main = worked == 3 || (!reactions && worked == 2) ? List.of() : List.of(all.get(worked));
            objects.put(objectId, new ObjectResult(main, worked(all), all));
            requestObjects.add(object(objectId, param(multi ? "side" : "segment",
                    multi ? Integer.toString(side) : "2690", "INTEGER")));
        }
        objects.put(SINGLE_OBJECT_ID, new ObjectResult(List.of(), List.of(), List.of()));
        requestObjects.add(object(SINGLE_OBJECT_ID, param("unlinked", "1", "INTEGER")));
        return finish(test, reactions, version, List.of(experiment), objects,
                requestObjects.toArray(dto.splitter.split.SplittingObjectDto[]::new));
    }

    private Fixture alternatives(Case test, boolean reactions, long version) {
        var exp1 = experiment(269211, SALT_2690,
                List.of(objectParamEqualsCondition(1, "side", "1", "INTEGER"),
                        objectParamEqualsCondition(2, "side", "2", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, 2500), 1, "0", "1"),
                        groupWithDocResult("B", shares(2500, 5000), 2, "1", "2")));
        var exp2 = experiment(269212, SALT_2690, List.of(objectParamEqualsCondition(1, "side", "1", "INTEGER")),
                List.of(groupWithDocResult("A", List.of(share(0, 1000), share(2500, 3500), share(5000, 6000)), 1, "1", "2")));
        var exp3 = experiment(269213, SALT_2690, List.of(objectParamEqualsCondition(1, "side", "2", "INTEGER")),
                List.of(groupWithDocResult("A", List.of(share(0, 500), share(1000, 1500), share(2500, 3000),
                        share(3500, 4000), share(5000, 5500), share(6000, 6500)), 1, "3", "3")));
        int from = test.from();
        String worked1 = from < 2500 ? "A" : from < 5000 ? "B" : null;
        // Tests-v10 pp.17-31: worked sets and MAIN choices are tabulated, not inferred from action priorities.
        Set<Integer> secondWorks = Set.of(0, 500, 2500, 3000, 5000, 5500);
        Set<Integer> thirdWorks = Set.of(0, 1000, 2500, 3500, 5000, 6000);
        Row left1 = new Row(269211, "A", 1, worked1, !reactions && "B".equals(worked1));
        Row right1 = new Row(269211, "B", 2, worked1, !reactions && "A".equals(worked1));
        Row left2 = new Row(269212, "A", 1, secondWorks.contains(from) ? "A" : null, false);
        Row right3 = new Row(269213, "A", 1, thirdWorks.contains(from) ? "A" : null, false);
        List<Row> leftWorked = worked(List.of(left1, left2));
        List<Row> rightWorked = worked(List.of(right1, right3));
        List<Row> leftMain;
        List<Row> rightMain;
        if (reactions) {
            // No layers in this analogous fixture: preserve the configured min-id tie breaker, never an alternative.
            leftMain = leftWorked.isEmpty() ? List.of() : List.of(leftWorked.get(0));
            rightMain = rightWorked.isEmpty() ? List.of() : List.of(rightWorked.get(0));
        } else {
            leftMain = switch (from) {
                case 0, 500, 5000, 5500 -> List.of(left2);
                case 1000, 1500, 2500, 3000, 3500, 4000 -> List.of(left1);
                default -> List.of();
            };
            rightMain = switch (from) {
                case 0, 500, 1000, 1500, 3000, 4000 -> List.of(right1);
                case 2500, 3500, 5000, 6000 -> List.of(right3);
                default -> List.of();
            };
        }
        var objects = new LinkedHashMap<String, ObjectResult>();
        objects.put(LEFT_OBJECT_ID, new ObjectResult(leftMain, leftWorked, List.of(left1, left2)));
        objects.put(RIGHT_OBJECT_ID, new ObjectResult(rightMain, rightWorked, List.of(right1, right3)));
        objects.put(SINGLE_OBJECT_ID, new ObjectResult(List.of(), List.of(), List.of()));
        return finish(test, reactions, version, List.of(exp1, exp2, exp3), objects,
                object(LEFT_OBJECT_ID, param("side", "1", "INTEGER")),
                object(RIGHT_OBJECT_ID, param("side", "2", "INTEGER")),
                object(SINGLE_OBJECT_ID, param("unlinked", "1", "INTEGER")));
    }
    private static List<Row> worked(List<Row> rows) {
        return rows.stream().filter(row -> row.group().equals(row.finalGroup())).toList();
    }
    private Fixture layers(Case test, boolean reactions, long version) {
        if (!reactions) throw new IllegalArgumentException("Layers matrix is REACTIONS-only");
        int[] priorities = {1, 2, 2, 3, 3, 3};
        int[] ends = {2500, 7500, 2500, 5000, 7500, 5000};
        var experiments = new ArrayList<ExperimentDto>();
        for (int i = 0; i < 6; i++) experiments.add(layeredExperiment(269221 + i, SALT_2690,
                priorities[i], priorities[i], List.of(objectParamEqualsCondition(1, "segment", "2690", "INTEGER")),
                List.of(groupWithDocResult("A", shares(0, ends[i]), 1, "1", Integer.toString(i + 1)))));
        Set<Long> workedIds = switch (test.from()) {
            case 0 -> Set.of(269221L, 269222L, 269223L, 269224L, 269225L, 269226L);
            case 2500 -> Set.of(269222L, 269224L, 269225L, 269226L);
            case 5000 -> Set.of(269222L, 269225L);
            default -> Set.of();
        };
        Set<Long> mainIds = test.from() < 5000 ? Set.of(269224L, 269225L, 269226L)
                : test.from() == 5000 ? Set.of(269225L) : Set.of();
        List<Row> all = java.util.stream.LongStream.rangeClosed(269221, 269226)
                .mapToObj(id -> new Row(id, "A", 1, workedIds.contains(id) ? "A" : null, false)).toList();
        var objects = Map.of(REACTIONS_OBJECT_ID, new ObjectResult(
                all.stream().filter(row -> mainIds.contains(row.expId())).toList(), worked(all), all));
        return finish(test, true, version, experiments, objects,
                object(REACTIONS_OBJECT_ID, param("segment", "2690", "INTEGER")));
    }
}
