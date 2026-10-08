package support.splitter.cases;

import dto.splitter.config.WorkedGroupRequests.Fixture;
import steps.flow.splitter.workedgroup.CandidateSelectionSteps.Case;
import util.splittercheck.WorkedGroupAssertions.ExpectedRow;
import java.util.*;
import java.util.stream.Stream;
import static dto.splitter.config.WorkedGroupRequests.*;

/** Expected winners are explicit examples, not calculated by the service's selection algorithm. */
public final class CandidateSelectionCases {
    private CandidateSelectionCases() { }
    public static ExpectedRow row(int exp, int condition, String group, String worked) {
        return new ExpectedRow(exp, condition, group, worked);
    }
    public static Map<String, List<ExpectedRow>> objects(List<ExpectedRow> left, List<ExpectedRow> right) {
        return Map.of(LEFT, left, RIGHT, right, UNLINKED, List.of());
    }
    public static Case single(String id, String worked) {
        int from = worked.equals("A") ? 0 : worked.equals("B") ? 2500 : 5000;
        var a = row(E1, 1, "A", worked);
        var b = row(E1, 2, "B", worked);
        return new Case(id, Fixture.SINGLE, from, from + 2500, 2,
                objects(worked.equals("A") ? List.of(a) : List.of(), worked.equals("B") ? List.of(b) : List.of()),
                worked.equals("NONE") ? null : objects(List.of(a), List.of(b)));
    }
    public static Case complex() {
        return new Case("2984-T04", Fixture.COMPLEX, 2500, 5000, 3,
                objects(List.of(), List.of(row(E1, 1, "A", "A"))),
                objects(List.of(row(E1, 2, "B", "A"), row(E2, 1, "A", "B"), row(E2, 1, "C", "B")),
                        List.of(row(E1, 1, "A", "A"), row(E2, 2, "B", "B"))));
    }
    public static Case priority(String id, Fixture fixture) {
        // At spread [0,2500), these deliberately hand-specified groups win distribution.
        List<String> worked = switch (fixture) {
            case PRIORITY_REJECT -> List.of("A", "B");
            case ID_REJECT -> List.of("B", "A");
            case PRIORITY_ALL -> List.of("A", "A");
            case TIE -> List.of("A", "A", "A");
            case NO_CANDIDATES -> List.of("B", "B");
            default -> throw new IllegalArgumentException(fixture.toString());
        };
        List<ExpectedRow> left = new ArrayList<>(), right = new ArrayList<>();
        for (int i = 0; i < worked.size(); i++) {
            left.add(row(E1 + i, 1, "A", worked.get(i))); right.add(row(E1 + i, 2, "B", worked.get(i)));
        }
        Map<String, List<ExpectedRow>> main = switch (fixture) {
            case PRIORITY_REJECT -> objects(List.of(left.get(0)), List.of(right.get(1)));
            case ID_REJECT -> objects(List.of(left.get(1)), List.of(right.get(0)));
            case PRIORITY_ALL -> objects(List.of(left.get(1)), List.of());
            case TIE -> objects(List.of(left.get(0)), List.of());
            case NO_CANDIDATES -> objects(List.of(), List.of(right.get(1)));
            default -> throw new IllegalArgumentException(fixture.toString());
        };
        return new Case(id, fixture, 0, 2500, 2, main, objects(left, right));
    }
    public static Case same(String id, int groups, int spread) {
        String worked = spread >= groups * 2500 ? "NONE" : String.valueOf((char)('A' + spread / 2500));
        List<ExpectedRow> all = new ArrayList<>();
        for (int i = 0; i < groups; i++) all.add(row(E1, 1, String.valueOf((char)('A' + i)), worked));
        return new Case(id, Fixture.SAME, spread, spread + 1, groups,
                objects(worked.equals("NONE") ? List.of() : List.of(row(E1, 1, worked, worked)), List.of()),
                worked.equals("NONE") ? null : objects(all, List.of()));
    }
    public static Case mapper(String worked) {
        var a = row(E1, 1, "A", worked);
        var b = row(E1, 2, "B", worked);
        var expected = objects(List.of(a), List.of(b));
        return new Case("2984-T17-" + worked, Fixture.MAPPER, worked.equals("A") ? 0 : 5000,
                worked.equals("A") ? 2500 : 7500, 2, expected, expected);
    }
    public static Case reactionsComparedToMapper() {
        Case mapper = mapper("A");
        return new Case("2984-T19", mapper.fixture(), mapper.from(), mapper.to(), mapper.groups(),
                objects(List.of(row(E1, 1, "A", "A")), List.of()), mapper.reportAll());
    }
    public static Case moved() {
        return new Case("2984-T21-MOVED", Fixture.MOVED, 2500, 5000, 2,
                objects(List.of(row(E1, 1, "B", "B")), List.of()),
                objects(List.of(row(E1, 1, "B", "B")), List.of(row(E1, 2, "A", "B"))));
    }
    public static Stream<Case> reactions() {
        return Stream.of(single("2984-T01", "B"), single("2984-T02", "A"), single("2984-T03", "NONE"), complex(),
                priority("2984-T05", Fixture.PRIORITY_REJECT), priority("2984-T06", Fixture.ID_REJECT),
                priority("2984-T07", Fixture.PRIORITY_ALL), priority("2984-T08", Fixture.TIE),
                priority("2984-T09", Fixture.NO_CANDIDATES), same("2984-T10-A", 3, 1000),
                same("2984-T10-B", 3, 3000), same("2984-T10-C", 3, 6000), reactionsComparedToMapper());
    }
    public static Stream<Case> boundaries() {
        return Stream.of(0,2499,2500,4999,5000,7499,7500,9999).map(s -> same("2984-T11-" + s, 3, s));
    }
    public static Stream<Case> flagMultiplicity() {
        return Stream.of(1,2,3).map(n -> same("2984-T14-" + n, n, 1000));
    }
    public static Stream<Case> permutations() {
        return Stream.of(complex(), priority("2984-T05", Fixture.PRIORITY_REJECT), same("2984-T10-B",3,3000));
    }
    public static Stream<Case> mapperCases() { return Stream.of(mapper("A"), mapper("B"), same("2984-T16",3,3000)); }
    public static Stream<Case> deniedCases() { return Stream.of(single("2984-T20-T01", "B"), single("2984-T20-T03", "NONE"), complex()); }
    public static List<Case> changingGroups() {
        return List.of(same("2984-T15-A1",3,1000), same("2984-T15-B",3,3000),
                same("2984-T15-NONE",3,8000), same("2984-T15-A2",3,1000));
    }
}
