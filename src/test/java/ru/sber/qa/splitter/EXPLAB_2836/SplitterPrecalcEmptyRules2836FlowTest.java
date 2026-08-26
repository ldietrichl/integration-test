package ru.sber.qa.splitter.EXPLAB_2836;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ObjectSelectConditionDto;
import dto.splitter.config.RuleDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2836. Pre-calculate: empty rules")
public class SplitterPrecalcEmptyRules2836FlowTest extends AbstractSplitterV9FlowTest {

    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
    private static final long EMPTY_RULES_EXP_ID = 283601L;
    private static final long IN_EMPTY_VALUES_EXP_ID = 283602L;
    private static final long IN_ONE_VALUE_EXP_ID = 283603L;
    private static final String SALT = "EXPLAB-2836-SALT";
    private static final String OBJECT_1 = "explab-2836-object-1";
    private static final String OBJECT_2 = "explab-2836-object-2";

    @Test
    @DisplayName("EXPLAB-2836-PC-05. pre-calculate с rules=[] связывает все объекты")
    void preCalculateShouldLinkEveryObjectWhenObjectSelectConditionRulesIsEmpty() {
        long version = SplitterVersionProvider.next();
        String uid1 = uniqueId("empty-rules", 1);
        String uid2 = uniqueId("empty-rules", 2);
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment());
        SplitterPrecalcRequestDto precalcRequest = precalcRequest(
                precalcObject(uid1, "A"),
                precalcObject(uid2, "B"));
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2836-PC-05",
                objectWithUniqueId(uid1, OBJECT_1, param("runtimeOnly", "NO_MATCH", "STRING")),
                objectWithUniqueId(uid2, OBJECT_2, param("runtimeOnly", "NO_MATCH", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с objectSelectConditions[].rules=[]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем pre-calculate для двух объектов с разным набором параметров", flow ->
                        calculatePreliminary(flow, precalcRequest))
                .step("Проверяем split по uniqueConfigurationId: оба объекта связаны с empty-rules experiment", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertFirstRuleExp(response, OBJECT_1, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertFirstRuleExp(response, OBJECT_2, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_1, "ALL", EMPTY_RULES_EXP_ID);
                    assertRuleExpIdsExactly(response, OBJECT_2, "ALL", EMPTY_RULES_EXP_ID);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-06. pre-calculate не считает rules=[[param in []]] пустым rules[]")
    void preCalculateShouldNotTreatInEmptyValuesRuleAsEmptyRules() {
        long version = SplitterVersionProvider.next();
        String uidNotMatched = uniqueId("in-empty", 1);
        String uidMatched = uniqueId("in-one", 2);
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                inEmptyValuesExperiment(),
                inOneValueExperiment());
        SplitterPrecalcRequestDto precalcRequest = precalcRequest(
                precalcObject(uidNotMatched, "2"),
                precalcObject(uidMatched, "1"));
        SplitRequestDto splitRequest = splitRequest("EXPLAB-2836-PC-06",
                objectWithUniqueId(uidNotMatched, OBJECT_1, param("runtimeOnly", "NO_MATCH", "STRING")),
                objectWithUniqueId(uidMatched, OBJECT_2, param("runtimeOnly", "NO_MATCH", "STRING")));

        getFlowWithRest()
                .step("Загружаем config с expId=283602 values=[] и expId=283603 values=[1]", flow ->
                        loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем pre-calculate: первый объект не подходит, второй подходит под values=[1]", flow ->
                        calculatePreliminary(flow, precalcRequest))
                .step("Проверяем split по таблице предрасчета", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertObjectEmptyOrAbsent(response, OBJECT_1);
                    assertFirstRuleExp(response, OBJECT_2, "MAIN", IN_ONE_VALUE_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_2, "MAIN", IN_ONE_VALUE_EXP_ID);
                    assertRuleExpIdsExactly(response, OBJECT_2, "ALL", IN_ONE_VALUE_EXP_ID);
                })
                .run();
    }

    private ValidatableResponseWrapper calculatePreliminary(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(request));
        shouldHaveSoConfigVersion(response, request.getSoConfigVersion());
        return response;
    }

    private SplitterPrecalcRequestDto precalcRequest(SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_VERSION.incrementAndGet())
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    private SplitterPrecalcObjectDto precalcObject(String uniqueConfigurationId, String configCommId) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(new SplitterPrecalcParamDto("configCommId", List.of(configCommId), "INTEGER")))
                .build();
    }

    private String uniqueId(String scenario, int index) {
        return "explab-2836-" + scenario + "-" + System.nanoTime() + "-uc-" + index;
    }

    private ExperimentDto emptyRulesExperiment() {
        return experiment((int) EMPTY_RULES_EXP_ID,
                SALT,
                List.of(condition(1, List.of())),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private ExperimentDto inEmptyValuesExperiment() {
        return inValuesExperiment((int) IN_EMPTY_VALUES_EXP_ID, List.of());
    }

    private ExperimentDto inOneValueExperiment() {
        return inValuesExperiment((int) IN_ONE_VALUE_EXP_ID, List.of("1"));
    }

    private ExperimentDto inValuesExperiment(int id, List<String> values) {
        RuleDto rule = RuleDto.builder()
                .dataType("INTEGER")
                .paramCode("configCommId")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("in")
                .values(values)
                .build();
        ObjectSelectConditionDto condition = condition(1, List.of(List.of(rule)));
        return experiment(id,
                SALT,
                List.of(condition),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private GroupDto fullRangeActionTypeZeroGroup() {
        return group("A",
                List.of(share(0, 10000)),
                List.of(resultWithParams(1, param("actionType", "0", "INTEGER"))));
    }
}
