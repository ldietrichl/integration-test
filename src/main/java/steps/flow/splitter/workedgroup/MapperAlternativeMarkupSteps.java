package steps.flow.splitter.workedgroup;

import config.extensions.WorkloadScenario;
import config.services.splitter.WorkedGroupProfile;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.WorkedGroupRequests;
import dto.splitter.config.WorkedGroupRequests.AlternativeExperiment;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import steps.rest.splitter.SplitterKafkaReportClient;
import util.splittercheck.WorkedGroupAssertions;
import util.splittercheck.WorkedGroupAssertions.AlternativeRow;
import util.splittercheck.WorkedGroupAssertions.ExpectedRow;
import util.support.SplitterVersionProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Direct markup and rollback sequences; transport and workload lifecycle remain shared. */
@WorkloadScenario("EXPLAB-3056")
public abstract class MapperAlternativeMarkupSteps extends AbstractSplitterV9FlowTest {
    public enum Profile {
        STANDARD, ALT_PARAM, REVERSED_MARKUP, WORKING, ROLLBACK_SIX,
        FILTER_DISABLED, PRECALC;

        public String rules(String source) {
            try {
                var yaml = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
                var root = (com.fasterxml.jackson.databind.node.ObjectNode) yaml.readTree(source);
                var rules = root.path("rules");
                var alternative = (com.fasterxml.jackson.databind.node.ObjectNode) rules.path("alternative-markup-rule").path("proc-params");
                var filter = (com.fasterxml.jackson.databind.node.ObjectNode) rules.path("filter-rule");
                switch (this) {
                    case ALT_PARAM -> alternative.put("param-code", "altAction");
                    case REVERSED_MARKUP -> alternative.putArray("alt-markup-values").add(3).add(1);
                    case WORKING -> alternative.putArray("alt-markup-values").add(0).add(1).add(2).add(3).add(4).add(5).add(6);
                    case ROLLBACK_SIX -> alternative.putArray("alt-rollback-values").add(6);
                    case FILTER_DISABLED -> filter.put("enabled", false);
                    default -> { }
                }
                return yaml.writeValueAsString(root);
            } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid rule resource", e); }
        }
    }
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    public @interface ScenarioProfile { Profile value(); }
    private Profile profile = Profile.STANDARD;
    private static final java.util.concurrent.atomic.AtomicInteger PRECALC_VERSION =
            new java.util.concurrent.atomic.AtomicInteger((int) java.time.Instant.now().getEpochSecond());
    protected boolean reactions() { return false; }

    public record Case(String id, List<AlternativeExperiment> experiments, int spread, List<Integer> objects,
                       Map<String, List<ExpectedRow>> main, Map<String, List<ExpectedRow>> all,
                       Set<AlternativeRow> marked) {
        public Case {
            experiments = List.copyOf(experiments); objects = List.copyOf(objects);
            main = immutableRows(main); all = immutableRows(all); marked = Set.copyOf(marked);
            if (spread < 0 || spread >= 10000) throw new IllegalArgumentException("Invalid spread");
            var ids = objects.stream().map(WorkedGroupRequests::alternativeObjectId).toList();
            if (new HashSet<>(ids).size() != ids.size() || !main.keySet().equals(new HashSet<>(ids))
                    || !all.keySet().equals(main.keySet())) throw new IllegalArgumentException("Incomplete object oracle");
            for (AlternativeRow row : marked) if (!all.getOrDefault(row.objectId(), List.of()).contains(row.row()))
                throw new IllegalArgumentException("Marked row is absent from ALL: " + row);
        }
        private static Map<String, List<ExpectedRow>> immutableRows(Map<String, List<ExpectedRow>> rows) {
            Map<String, List<ExpectedRow>> copy = new LinkedHashMap<>();
            rows.forEach((id, values) -> copy.put(id, List.copyOf(values)));
            return Collections.unmodifiableMap(copy);
        }
        public Map<String, List<ExpectedRow>> publicAll() {
            Map<String, List<ExpectedRow>> result = new LinkedHashMap<>();
            all.forEach((id, rows) -> result.put(id, rows.stream()
                    .filter(row -> row.expGroup().equals(row.finalExpGroup())).toList()));
            return result;
        }
        public Set<AlternativeRow> publicMarked() {
            return marked.stream().filter(row -> row.row().expGroup().equals(row.row().finalExpGroup()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        @Override public String toString() { return id; }
    }

    @Override protected String runtimeSplittingPoint() { return reactions() ? "REACTIONS" : "MAPPER"; }

    @BeforeEach protected void prepareMarkup(TestInfo info) {
        profile = info.getTestMethod().map(m -> m.getAnnotation(ScenarioProfile.class))
                .map(ScenarioProfile::value).orElse(Profile.STANDARD);
        WorkedGroupProfile.prepare("explab3056", reactions(), !reactions(),
                System.getProperty("splitter.config.load.mode", "rest").trim().toLowerCase(Locale.ROOT),
                profile != Profile.PRECALC, reactions() ? "splitter/EXPLAB_2984/configmap/" : "splitter/EXPLAB_3056/configmap/",
                reactions() ? java.util.function.UnaryOperator.identity() : profile::rules,
                profile == Profile.PRECALC ? Map.of("preliminary-calculation-enabled", "true") : Map.of());
        Allure.parameter("3056.profile", profile);
        Allure.parameter("3056.flag-contract", "AB Splitter v14 p.95/284: ALL isAlternative string true/false; MAIN has no alternative flags");
    }

    @AfterEach protected void markupEvidence() {
        infrastructure.kubernetes.WorkloadScenarioEvidence.current().finish();
    }

    protected void verify(Case scenario, KafkaService kafka) { verifySequence(List.of(scenario), kafka, false); }

    protected void verifyPermutation(Case scenario, KafkaService kafka) {
        verifySequence(Collections.nCopies(3, scenario), kafka, false);
        verifySequence(Collections.nCopies(3, scenario), kafka, true);
    }

    protected void verifySequence(List<Case> scenarios, KafkaService kafka, boolean reversed) {
        if (profile == Profile.PRECALC) assertEquals(1, scenarios.size(),
                "EXPLAB-3056 checks alternative markup on isolated precalculated links, not cache refresh");
        List<AlternativeExperiment> previous = null;
        LoadConfigRequestDto loaded = null;
        for (Case scenario : scenarios) {
            if (!scenario.experiments().equals(previous)) {
                loaded = WorkedGroupRequests.alternativeConfig(SplitterVersionProvider.next(), scenario.experiments(), reversed);
                loaded.setSplittingPointCode(runtimeSplittingPoint());
                LoadConfigRequestDto config = loaded;
                getFlowWithRest().step("Загрузить " + runtimeSplittingPoint() + ": " + scenario.id() + ", version=" + config.getConfigVersion(),
                        flow -> {
                            if (profile != Profile.PRECALC) {
                                loadConfig(flow, reactions() ? EndpointMode.REACTIONS : EndpointMode.MAPPER, config);
                            } else {
                                var response = util.SplitterPrecalcAssertions.shouldBe200(EndpointMode.MAPPER.load(flow.restCustomSteps(), config));
                                util.SplitterPrecalcAssertions.shouldBeConfigLoaded(response);
                                var body = jsonBody(response, "Precalculation config load");
                                util.splittercheck.PrecalcResponseAssertions.numberEquals(body, "currentConfigVersion", config.getConfigVersion());
                            }
                        }).run();
                previous = scenario.experiments();
            }
            LoadConfigRequestDto config = loaded;
            String splittingId = splittingIdForExactSpread("3056", WorkedGroupRequests.ALTERNATIVE_SALT, scenario.spread());
            int actualSpread = spread(WorkedGroupRequests.ALTERNATIVE_SALT, splittingId);
            assertEquals(scenario.spread(), actualSpread, "Deterministic distribution precondition");
            List<Integer> objects = new ArrayList<>(scenario.objects());
            if (reversed) Collections.reverse(objects);
            var request = profile == Profile.PRECALC
                    ? WorkedGroupRequests.alternativePrecalculatedRequest(splittingId, objects)
                    : WorkedGroupRequests.alternativeRequest(splittingId, objects);
            if (profile == Profile.PRECALC) {
                var pre = dto.splitter.precalc.MapperPrecalcRequests.fromSplit(PRECALC_VERSION.incrementAndGet(), request);
                getFlowWithRest().step("Предрасчёт связей: " + scenario.id(), flow -> {
                    var response = util.SplitterPrecalcAssertions.shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(pre));
                    var body = jsonBody(response, "Precalculate result");
                    util.splittercheck.PrecalcResponseAssertions.precalculated(pre, body);
                    util.splittercheck.PrecalcResponseAssertions.numberEquals(body.path("counter"), "totalObjects", scenario.objects().size());
                    util.splittercheck.PrecalcResponseAssertions.numberEquals(body.path("counter"), "copiedObjects", 0);
                    util.splittercheck.PrecalcResponseAssertions.numberEquals(body.path("counter"), "objectsAdded", scenario.objects().size());
                }).run();
            }
            if (profile == Profile.PRECALC) {
                // Runtime values cannot reproduce the cached links: fallback to live calculation must fail.
                request.getSplittingObjects().forEach(o -> o.setObjectParams(List.of(
                        new dto.splitter.common.ParamDto("id", List.of("-3056"), "INTEGER"))));
            }
            Allure.parameter("scenario", scenario.id());
            Allure.parameter("spreadValue", actualSpread);
            Allure.addAttachment("3056 expected rows and flags", "text/plain",
                    "MAIN=" + scenario.main() + "\nKAP ALL=" + scenario.all() + "\nMarked=" + scenario.marked());
            getFlowWithRest().step("Проверить MAIN, связи и альтернативы REST/КАП: " + scenario.id(), flow -> {
                try (var report = new SplitterKafkaReportClient(kafka)) {
                    var response = split(flow, reactions() ? EndpointMode.REACTIONS : EndpointMode.MAPPER, request);
                    assertAll("REST and KAP",
                            () -> assertAll("REST",
                                    () -> assertBasicResponseContract(response, request, config.getConfigVersion()),
                                    () -> {
                                        var body = jsonBody(response, "REST result");
                                        assertAll("Public rows and flags",
                                                () -> WorkedGroupAssertions.candidateResult(body, request, config, actualSpread,
                                                        scenario.main(), scenario.publicAll(), reactions()),
                                                () -> checkMarkup(body, scenario, true));
                                    }),
                            () -> {
                                var envelope = report.await(request.getRequestId(), runtimeSplittingPoint(), config.getConfigVersion());
                                var body = WorkedGroupAssertions.reportBody(envelope, request, runtimeSplittingPoint(), config.getConfigVersion());
                                assertAll("Full rows and flags",
                                        () -> WorkedGroupAssertions.candidateResult(body, request, config, actualSpread,
                                                scenario.main(), scenario.all(), reactions()),
                                        () -> checkMarkup(body, scenario, false));
                            });
                }
            }).run();
        }
    }

    private void checkMarkup(com.fasterxml.jackson.databind.JsonNode body, Case scenario, boolean publicResult) {
        if (reactions()) for (var object : body.path("splittingResults")) {
            if (scenario.main().get(object.path("objectId").asText()).isEmpty()) {
                WorkedGroupAssertions.rules(object, Set.of());
                WorkedGroupAssertions.noMainFlags(object.path("objectFlags"));
            }
        }
        if (!reactions()) WorkedGroupAssertions.alternativeFlags(body,
                publicResult ? scenario.publicAll() : scenario.all(), publicResult ? scenario.publicMarked() : scenario.marked());
        if (profile == Profile.WORKING || profile == Profile.FILTER_DISABLED)
            WorkedGroupAssertions.alternativeFiltering(body, scenario.main(), scenario.marked(),
                    WorkedGroupRequests.alternativeConfig(1, scenario.experiments(), false),
                    profile != Profile.FILTER_DISABLED, profile == Profile.WORKING);
    }
}
