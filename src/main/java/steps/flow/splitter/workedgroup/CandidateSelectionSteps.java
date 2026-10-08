package steps.flow.splitter.workedgroup;

import config.extensions.WorkloadScenario;
import config.services.splitter.WorkedGroupProfile;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.WorkedGroupRequests;
import dto.splitter.config.WorkedGroupRequests.Fixture;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import steps.rest.splitter.SplitterKafkaReportClient;
import util.splittercheck.WorkedGroupAssertions;
import util.splittercheck.WorkedGroupAssertions.ExpectedRow;
import util.support.SplitterVersionProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Candidate/flag scenarios share transport and lifecycle, but not the older REST no-MAIN oracle. */
@WorkloadScenario("EXPLAB-2984")
public abstract class CandidateSelectionSteps extends AbstractSplitterV9FlowTest {
    public record Case(String id, Fixture fixture, int from, int to, int groups,
                       Map<String, List<ExpectedRow>> main, Map<String, List<ExpectedRow>> reportAll) {
        @Override public String toString() { return id; }
        /** The allow-without-MAIN switch suppresses ALL in KAP as well as in the public response. */
        public Map<String, List<ExpectedRow>> reportAll(boolean allowWithoutMain) {
            if (allowWithoutMain) return reportAll;
            Map<String, List<ExpectedRow>> expected = new LinkedHashMap<>();
            main.forEach((id, rows) -> expected.put(id, rows.isEmpty() ? List.of()
                    : Objects.requireNonNull(Objects.requireNonNull(reportAll, "ALL oracle for a MAIN object").get(id))));
            return expected;
        }
    }
    protected boolean reactions() { return true; }
    protected boolean allowWithoutMain() { return true; }
    @Override protected String runtimeSplittingPoint() { return reactions() ? "REACTIONS" : "MAPPER"; }

    @BeforeEach protected void prepareCandidates() {
        WorkedGroupProfile.prepare("explab2984", reactions(), allowWithoutMain(),
                System.getProperty("splitter.config.load.mode", "rest").trim().toLowerCase(Locale.ROOT), true,
                "splitter/EXPLAB_2984/configmap/");
        Allure.parameter("2984.no-main-container", "Q2: winner checked; exact empty container not asserted");
        if (!reactions()) Allure.parameter("2984.mapper-all-flag-values", "Q3: not asserted; duplicates and MAIN checked");
    }
    @AfterEach protected void candidateEvidence() { infrastructure.kubernetes.WorkloadScenarioEvidence.current().finish(); }

    protected void verify(Case testCase, KafkaService kafka) { verifySequence(List.of(testCase), kafka, false, false); }

    protected void verifyPermutation(Case testCase, KafkaService kafka) {
        verifySequence(Collections.nCopies(3, testCase), kafka, false, false);
        verifySequence(Collections.nCopies(3, testCase), kafka, true, false);
    }
    protected void verifyIndependence(Case testCase, KafkaService kafka) {
        verify(testCase, kafka);
        verifySequence(List.of(testCase), kafka, false, true);
        verifySequence(List.of(testCase), kafka, true, false);
    }

    protected void verifySequence(List<Case> cases, KafkaService kafka, boolean reverse, boolean isolatedObjects) {
        EndpointMode mode = reactions() ? EndpointMode.REACTIONS : EndpointMode.MAPPER;
        LoadConfigRequestDto current = null;
        Fixture previous = null;
        int previousGroups = -1;
        for (Case testCase : cases) {
            if (previous != testCase.fixture() || previousGroups != testCase.groups()) {
                current = WorkedGroupRequests.config(testCase.fixture(), mode.splittingPointCode(),
                        SplitterVersionProvider.next(), testCase.groups(), reverse);
                LoadConfigRequestDto config = current;
                getFlowWithRest().step("Загрузить " + mode + " / " + testCase.fixture() + " / " + config.getConfigVersion(),
                        flow -> loadConfig(flow, mode, config)).run();
                previous = testCase.fixture(); previousGroups = testCase.groups();
            }
            List<String> ids = new ArrayList<>(List.of(WorkedGroupRequests.LEFT, WorkedGroupRequests.RIGHT, WorkedGroupRequests.UNLINKED));
            if (reverse) Collections.reverse(ids);
            List<List<String>> batches = isolatedObjects ? ids.stream().map(List::of).toList() : List.of(ids);
            for (List<String> objects : batches) {
                LoadConfigRequestDto config = current;
                String splittingId = testCase.to() == testCase.from() + 1
                        ? splittingIdForExactSpread("2984", WorkedGroupRequests.SALT, testCase.from())
                        : splittingIdForRange("2984", WorkedGroupRequests.SALT, testCase.from(), testCase.to());
                int spread = spread(WorkedGroupRequests.SALT, splittingId);
                assertTrue(spread >= testCase.from() && spread < testCase.to(), "Fixture spread precondition");
                var request = WorkedGroupRequests.request(splittingId, objects);
                Allure.parameter("scenario", testCase.id());
                Allure.parameter("spreadValue", spread);
                if (testCase.reportAll(allowWithoutMain()) == null) Allure.parameter("2984.NONE-report-ALL", "Q2: exact ALL at allow=true not asserted");
                getFlowWithRest().step("Split и независимые проверки REST/КАП: " + testCase.id(), flow -> {
                    // Subscribe before sending the request; assertion failure in REST must not skip KAP.
                    try (var report = new SplitterKafkaReportClient(kafka)) {
                        var response = split(flow, mode, request);
                        assertAll("REST and KAP",
                                () -> assertAll("Public response",
                                        () -> assertBasicResponseContract(response, request, config.getConfigVersion()),
                                        () -> WorkedGroupAssertions.candidateResult(jsonBody(response, "REST result"), request,
                                                config, spread, subset(testCase.main(), objects),
                                                subset(publicAll(testCase), objects), reactions())),
                                () -> {
                                    var envelope = report.await(request.getRequestId(), mode.splittingPointCode(), config.getConfigVersion());
                                    var body = WorkedGroupAssertions.reportBody(envelope, request, mode.splittingPointCode(), config.getConfigVersion());
                                    WorkedGroupAssertions.candidateResult(body, request, config, spread,
                                            subset(testCase.main(), objects), subset(testCase.reportAll(allowWithoutMain()), objects), reactions());
                                });
                    }
                }).run();
            }
        }
    }

    private Map<String, List<ExpectedRow>> publicAll(Case testCase) {
        Map<String, List<ExpectedRow>> result = new LinkedHashMap<>();
        for (String id : testCase.main().keySet()) {
            List<ExpectedRow> all = testCase.reportAll() == null ? List.of() : testCase.reportAll().get(id).stream()
                    .filter(row -> row.finalExpGroup() != null && row.expGroup().equals(row.finalExpGroup())).toList();
            result.put(id, !allowWithoutMain() && testCase.main().get(id).isEmpty() ? List.of() : all);
        }
        return result;
    }
    private static Map<String, List<ExpectedRow>> subset(Map<String, List<ExpectedRow>> map, List<String> objects) {
        if (map == null) return null;
        Map<String, List<ExpectedRow>> result = new LinkedHashMap<>();
        objects.forEach(id -> result.put(id, Objects.requireNonNull(map.get(id), "Expected object " + id)));
        return result;
    }
}
