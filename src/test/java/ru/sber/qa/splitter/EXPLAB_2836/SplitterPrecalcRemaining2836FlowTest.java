package ru.sber.qa.splitter.EXPLAB_2836;

import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.splitter.support.SplitterTestProfileOnly;
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
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2836. Pre-calculate: remaining functional plan")
@AnyConfigLoadMode
public class SplitterPrecalcRemaining2836FlowTest extends AbstractSplitterV9FlowTest {

    private static final AtomicInteger SO_VERSION = new AtomicInteger((int) (System.currentTimeMillis() / 1000L));
    private static final String SALT = "EXPLAB-2836-REMAINING-SALT";
    private static final String OBJECT = "explab-2836-object";
    private static final String OBJECT_2 = "explab-2836-object-2";
    private static final String OBJECT_3 = "explab-2836-object-3";

    @Test
    @DisplayName("EXPLAB-2836-PC-01. Валидный pre-calculate возвращает responseId и soConfigVersion")
    void validPreCalculateShouldReturnResponseContract() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc01");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28360101, "PC01", "0"));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(uid, "PC01"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-01", uid, OBJECT);

        getFlowWithRest()
                .step("Загружаем config и выполняем pre-calculate", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    shouldHaveSoConfigVersion(shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(precalc)),
                            precalc.getSoConfigVersion());
                })
                .step("Проверяем, что split использует предрасчитанную связь", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28360101L, "A", "A");
                })
                .run();
    }

    @Disabled("Требуется отдельный стендовый профиль с preliminary-calc-enabled=false")
    @Test
    @DisplayName("EXPLAB-2836-PC-02. Manual/env: pre-calculate выключен")
    void disabledPrecalcShouldReturnPrecalcNotEnabled() {
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-03. Невалидный pre-calculate request возвращает BAD_REQUEST")
    void invalidPrecalculateRequestShouldReturnBadRequest() {
        getFlowWithRest()
                .step("Отправляем request без requestId и objectParams", flow ->
                        flow.restCustomSteps().splitterSteps().calculatePreliminary("{\"soConfigVersion\":1,\"splittingObjects\":[{\"uniqueConfigurationId\":\"broken\"}]}")
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .run();
    }

    @Disabled("Требуется изолированный чистый стенд без активного split config")
    @Test
    @DisplayName("EXPLAB-2836-PC-04. Manual/env: pre-calculate без config создает пустые links")
    void precalcWithoutLoadedConfigShouldStoreObjectsWithEmptyLinks() {
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-07. Повторный pre-calculate полного списка сохраняет стабильный split")
    void repeatedFullListShouldKeepStablePrecalcLinks() {
        long version = SplitterVersionProvider.next();
        String a = uid("pc07-a");
        String b = uid("pc07-b");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28360701, "PC07", "0"));
        SplitterPrecalcRequestDto first = precalcRequest(precalcObject(a, "PC07"), precalcObject(b, "PC07"));
        SplitterPrecalcRequestDto repeated = precalcRequest(precalcObject(a, "PC07"), precalcObject(b, "PC07"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-07", a, OBJECT, b, OBJECT_2);

        getFlowWithRest()
                .step("Загружаем config и выполняем первый полный pre-calculate", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, first);
                })
                .step("Повторяем pre-calculate с тем же полным списком", flow -> calculate(flow, repeated))
                .step("Проверяем, что обе связи стабильны", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28360701L, "A", "A");
                    assertFirstRuleExp(response, OBJECT_2, "MAIN", 28360701L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-08. Полный список [A,C] заменяет [A,B]")
    void changedFullListShouldAddAndDeleteObjects() {
        long version = SplitterVersionProvider.next();
        String a = uid("pc08-a");
        String b = uid("pc08-b");
        String c = uid("pc08-c");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28360801, "PC08", "0"));
        SplitterPrecalcRequestDto first = precalcRequest(precalcObject(a, "PC08"), precalcObject(b, "PC08"));
        SplitterPrecalcRequestDto changed = precalcRequest(precalcObject(a, "PC08"), precalcObject(c, "PC08"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-08", a, OBJECT, b, OBJECT_2, c, OBJECT_3);

        getFlowWithRest()
                .step("Создаем таблицу [A,B]", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, first);
                })
                .step("Заменяем таблицу полным списком [A,C]", flow -> calculate(flow, changed))
                .step("Проверяем, что A и C связаны, а B больше не используется", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28360801L, "A", "A");
                    assertObjectEmptyOrAbsent(response, OBJECT_2);
                    assertFirstRuleExp(response, OBJECT_3, "MAIN", 28360801L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-09. Объекты без links не теряются и дают empty split result")
    void notLinkedObjectsShouldBeStoredAndReturnedAsEmpty() {
        long version = SplitterVersionProvider.next();
        String linked = uid("pc09-linked");
        String notLinked = uid("pc09-empty");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28360901, "LINKED", "0"));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(linked, "LINKED"), precalcObject(notLinked, "NO_MATCH"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-09", linked, OBJECT, notLinked, OBJECT_2);

        getFlowWithRest()
                .step("Выполняем pre-calculate с linked и not-linked объектами", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, precalc);
                })
                .step("Проверяем split: linked имеет MAIN, not-linked пустой", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28360901L, "A", "A");
                    assertObjectEmptyOrAbsent(response, OBJECT_2);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-10. Объект может получить links по нескольким experiments")
    void objectMatchedBySeveralExperimentsShouldStoreAllLinks() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc10");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361001, "MULTI", "0"),
                markerExperiment(28361002, "MULTI", "3"));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(uid, "MULTI"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-10", uid, OBJECT);

        getFlowWithRest()
                .step("Выполняем pre-calculate для объекта, подходящего двум experiments", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, precalc);
                })
                .step("Проверяем ALL по двум experiments и MAIN по priority", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertRuleExpIdsExactly(response, OBJECT, "ALL", 28361001L, 28361002L);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361002L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-11. Несколько conditions сохраняют conditionId в predcalc result")
    void multipleConditionsShouldKeepConditionIdInPrecalcLinks() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc11");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                experiment(28361101,
                        SALT,
                        List.of(markerCondition(1, "C1"), markerCondition(2, "C2")),
                        List.of(group("A",
                                Arrays.asList(share(0, 10000)),
                                List.of(
                                        resultWithParams(1, param("actionType", "0", "INTEGER")),
                                        resultWithParams(2, param("actionType", "0", "INTEGER")))))));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(uid, "C1"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-11", uid, OBJECT);

        getFlowWithRest()
                .step("Выполняем pre-calculate", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, precalc);
                })
                .step("Проверяем conditionId выбранной связи", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361101L, "A", "A");
                    assertFirstRuleExpConditionId(response, OBJECT, "MAIN", 1);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-12. groupResultParams из predcalc доступны в split result")
    void groupResultParamsShouldBeAvailableAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc12");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361201, "PARAMS", "3"));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(uid, "PARAMS"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-12", uid, OBJECT);

        getFlowWithRest()
                .step("Выполняем pre-calculate", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, precalc);
                })
                .step("Проверяем result params из группы", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExpActionType(response, OBJECT, "MAIN", "3");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-13. Тот же uniqueConfigurationId с измененными params сохраняет старые links")
    void sameUniqueIdWithChangedParamsShouldKeepCopiedLinks() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc13");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361301, "INITIAL", "0"),
                markerExperiment(28361302, "CHANGED", "3"));
        SplitterPrecalcRequestDto first = precalcRequest(precalcObject(uid, "INITIAL"));
        SplitterPrecalcRequestDto changed = precalcRequest(precalcObject(uid, "CHANGED"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-13", uid, OBJECT);

        getFlowWithRest()
                .step("Предрасчитываем uid с INITIAL", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, first);
                })
                .step("Повторяем pre-calculate с тем же uid и CHANGED params", flow -> calculate(flow, changed))
                .step("Проверяем, что link остался от первого расчета", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361301L, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT, "ALL", 28361301L);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-14. Новый uniqueConfigurationId пересчитывает тот же бизнес-объект заново")
    void newUniqueIdForSameBusinessObjectShouldRecalculateLinks() {
        long version = SplitterVersionProvider.next();
        String oldUid = uid("pc14-old");
        String newUid = uid("pc14-new");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361401, "OLD", "0"),
                markerExperiment(28361402, "NEW", "3"));
        SplitterPrecalcRequestDto first = precalcRequest(precalcObject(oldUid, "OLD"));
        SplitterPrecalcRequestDto second = precalcRequest(precalcObject(oldUid, "OLD"), precalcObject(newUid, "NEW"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-14", newUid, OBJECT);

        getFlowWithRest()
                .step("Предрасчитываем старый uid", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, first);
                })
                .step("Добавляем новый uid того же бизнес-объекта", flow -> calculate(flow, second))
                .step("Проверяем, что новый uid рассчитан по новым params", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361402L, "A", "A");
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-15. splittingObjects=[] очищает таблицу predcalc")
    void emptySplittingObjectsShouldClearPrecalcTable() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc15");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361501, "PC15", "0"));
        SplitterPrecalcRequestDto filled = precalcRequest(precalcObject(uid, "PC15"));
        SplitterPrecalcRequestDto empty = SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_VERSION.incrementAndGet())
                .splittingObjects(List.of())
                .build();
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-15", uid, OBJECT);

        getFlowWithRest()
                .step("Создаем непустую predcalc table", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, filled);
                })
                .step("Очищаем таблицу пустым списком", flow -> calculate(flow, empty))
                .step("Проверяем, что старый uid больше не дает predcalc result", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertObjectEmptyOrAbsent(response, OBJECT);
                })
                .run();
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-16. Дубли uniqueConfigurationId не приводят к падению")
    void duplicatedUniqueConfigurationIdsShouldBeHandledDeterministically() {
        long version = SplitterVersionProvider.next();
        String uid = uid("pc16");
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361601, "FIRST", "0"),
                markerExperiment(28361602, "SECOND", "3"));
        SplitterPrecalcRequestDto duplicated = precalcRequest(precalcObject(uid, "FIRST"), precalcObject(uid, "SECOND"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-16", uid, OBJECT);

        getFlowWithRest()
                .step("Выполняем pre-calculate с дублями uniqueConfigurationId", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculateAllowingEmptyBody(flow, duplicated);
                })
                .step("Проверяем, что split возвращает один детерминированный object result", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, version);
                    assertTrue(hasObject(response, OBJECT), body(response));
                    assertSplittingResultsHaveUniqueObjectIds(response);
                })
                .run();
    }

    @Disabled("Требуется Kafka monitoring topic omon_explab_splitter_log и стабильные counters для PRE_CALC_REQUEST")
    @Test
    @DisplayName("EXPLAB-2836-PC-17. Manual/env: successful monitoring fields PRE_CALC_REQUEST")
    void successfulPrecalcMonitoringShouldExposeRequiredFields() {
    }

    @Test
    @DisplayName("EXPLAB-2836-PC-18. Большой batch объектов рассчитывается без ошибок")
    void largeBatchShouldBeCalculatedSuccessfully() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                markerExperiment(28361801, "BATCH", "0"));
        List<SplitterPrecalcObjectDto> objects = new ArrayList<>();
        List<String> uids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String uid = uid("pc18-" + i);
            uids.add(uid);
            objects.add(precalcObject(uid, "BATCH"));
        }
        SplitterPrecalcRequestDto precalc = precalcRequest(objects.toArray(new SplitterPrecalcObjectDto[0]));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-18", uids.get(0), OBJECT, uids.get(19), OBJECT_2);

        getFlowWithRest()
                .step("Выполняем pre-calculate для batch из 20 объектов", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, config);
                    calculate(flow, precalc);
                })
                .step("Проверяем несколько объектов из batch", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361801L, "A", "A");
                    assertFirstRuleExp(response, OBJECT_2, "MAIN", 28361801L, "A", "A");
                })
                .run();
    }

    @Test
    @SplitterTestProfileOnly("precalc-reload-contract")
    @DisplayName("EXPLAB-2836-PC-19. Reload config перевязывает существующую predcalc-таблицу")
    void configReloadShouldRebindExistingPrecalcTable() {
        long[] versions = SplitterVersionProvider.nextVersions(2);
        String uid = uid("pc19");
        LoadConfigRequestDto seed = configFor(EndpointMode.MAPPER,
                versions[0],
                markerExperiment(28361901, "RELOAD", "0"));
        LoadConfigRequestDto reload = configFor(EndpointMode.MAPPER,
                versions[1],
                markerExperiment(28361902, "RELOAD", "3"));
        SplitterPrecalcRequestDto precalc = precalcRequest(precalcObject(uid, "RELOAD"));
        SplitRequestDto splitRequest = splitByUid("EXPLAB-2836-PC-19", uid, OBJECT);

        getFlowWithRest()
                .step("Создаем predcalc table на seed config", flow -> {
                    loadConfig(flow, EndpointMode.MAPPER, seed);
                    calculate(flow, precalc);
                })
                .step("Загружаем новую версию config", flow -> loadConfig(flow, EndpointMode.MAPPER, reload))
                .step("Проверяем, что split видит новые links после reload", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, splitRequest);
                    assertBasicResponseContract(response, splitRequest, versions[1]);
                    assertFirstRuleExp(response, OBJECT, "MAIN", 28361902L, "A", "A");
                    assertFirstRuleExpActionType(response, OBJECT, "MAIN", "3");
                })
                .run();
    }

    private ValidatableResponseWrapper calculate(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(request));
        shouldHaveSoConfigVersion(response, request.getSoConfigVersion());
        return response;
    }

    private ValidatableResponseWrapper calculateAllowingEmptyBody(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        return shouldBe200(flow.restCustomSteps().splitterSteps().calculatePreliminary(request));
    }

    private ExperimentDto markerExperiment(int id, String marker, String actionType) {
        return experiment(id,
                SALT + "-" + id,
                List.of(markerCondition(1, marker)),
                List.of(group("A",
                        Arrays.asList(share(0, 10000)),
                        List.of(resultWithParams(1,
                                param("actionType", actionType, "INTEGER"),
                                param("result", String.valueOf(id), "INTEGER"))))));
    }

    private ObjectSelectConditionDto markerCondition(int id, String marker) {
        return condition(id, List.of(List.of(RuleDto.builder()
                .dataType("STRING")
                .paramCode("marker")
                .paramSource("SPLITTING_OBJECTS")
                .operatorCode("equal")
                .values(List.of(marker))
                .build())));
    }

    private SplitterPrecalcRequestDto precalcRequest(SplitterPrecalcObjectDto... objects) {
        return SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(SO_VERSION.incrementAndGet())
                .splittingObjects(Arrays.asList(objects))
                .build();
    }

    private SplitterPrecalcObjectDto precalcObject(String uniqueConfigurationId, String marker) {
        return SplitterPrecalcObjectDto.builder()
                .uniqueConfigurationId(uniqueConfigurationId)
                .objectParams(List.of(new SplitterPrecalcParamDto("marker", List.of(marker), "STRING")))
                .build();
    }

    private SplitRequestDto splitByUid(String splittingId, String uid, String objectId) {
        return splitRequest(splittingId, objectWithUniqueId(uid, objectId, param("runtimeOnly", "NO_MATCH", "STRING")));
    }

    private SplitRequestDto splitByUid(String splittingId,
                                       String uid1,
                                       String objectId1,
                                       String uid2,
                                       String objectId2) {
        return splitRequest(splittingId,
                objectWithUniqueId(uid1, objectId1, param("runtimeOnly", "NO_MATCH", "STRING")),
                objectWithUniqueId(uid2, objectId2, param("runtimeOnly", "NO_MATCH", "STRING")));
    }

    private SplitRequestDto splitByUid(String splittingId,
                                       String uid1,
                                       String objectId1,
                                       String uid2,
                                       String objectId2,
                                       String uid3,
                                       String objectId3) {
        return splitRequest(splittingId,
                objectWithUniqueId(uid1, objectId1, param("runtimeOnly", "NO_MATCH", "STRING")),
                objectWithUniqueId(uid2, objectId2, param("runtimeOnly", "NO_MATCH", "STRING")),
                objectWithUniqueId(uid3, objectId3, param("runtimeOnly", "NO_MATCH", "STRING")));
    }

    private String uid(String scenario) {
        return "explab-2836-" + scenario + "-" + System.nanoTime();
    }
}
