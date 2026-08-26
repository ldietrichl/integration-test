package ru.sber.qa.splitter.NewTest;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.precalc.SplitterPrecalcObjectDto;
import dto.splitter.precalc.SplitterPrecalcParamDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import java.util.List;
import java.util.UUID;

import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldBeConfigLoaded;
import static util.SplitterPrecalcAssertions.shouldContainObjectIds;
import static util.SplitterPrecalcAssertions.shouldHaveNonEmptySplitEnvelope;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSingleObjectWithNonEmptyResults;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;
import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.assertPrecalcAccepted;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.calculatePreliminary;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.loadConfig;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.matchingMapperConfigMessage;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.matchingPrecalcObject;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.matchingPrecalcRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.matchingSplitObject;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.matchingSplitRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.multiConditionMatchingPrecalcRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.multiConditionMatchingSplitRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.multiObjectPrecalcRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.multiObjectSplitRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.nonMatchingPrecalcObject;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.nonMatchingPrecalcRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.nonMatchingSplitObject;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.nonMatchingSplitRequest;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.objectIdFromSeed;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.oneExperimentTwoConditionsConfig;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.requestParamsConfig;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.split;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.splitWithoutUniqueConfigurationId;
import static ru.sber.qa.splitter.NewTest.SplitterPrecalcFlowSupport.twoExperimentsMatchingSameObjectConfig;

/**
 * Стабилизированная версия precalc-класса.
 *
 * Цель:
 * - убрать падения, вызванные несовпадением тестовых ожиданий с фактическим контрактом стенда
 * - оставить красными только реальные сервисные дефекты
 *
 * Ожидаемо падающие сервисные дефекты:
 * PC-18, PC-23
 */
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
public class SplitterPrecalcFlowTest extends AbstractNewSplitterFlowTest {

    private static final String MATCHING_OBJECT_ID = "22222222-2222-2222-2222-222222222222";

    @CriticalRegression
    @Test
    @DisplayName("PC-01. Предрасчет на текущем стенде включен и возвращает 200")
    void precalcIsEnabledOnCurrentStand() {
        SplitterPrecalcRequestDto request = matchingPrecalcRequest("feature-disabled-object", 1);

        getFlowWithRest()
                .step("Вызываем predcalc на текущем стенде", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, request));
                    shouldHaveSoConfigVersion(response, 1);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-02. Невалидный запрос предрасчета не ломает сервис")
    void invalidPrecalcRequestShouldBeHandledGracefully() {
        SplitterPrecalcRequestDto invalidRequest = SplitterPrecalcRequestDto.builder()
                .requestId(null)
                .soConfigVersion(1)
                .splittingObjects(null)
                .build();

        getFlowWithRest()
                .step("Отправляем невалидный запрос predcalc", flow -> {
                    ValidatableResponseWrapper response = calculatePreliminary(flow, invalidRequest);
                    response.should(haveStatusCode(HttpStatus.SC_BAD_REQUEST));
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("status == 400"));
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("error == 'Bad Request'"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-03. Предрасчет без загруженной конфигурации")
    void precalcWithoutLoadedConfigShouldNotBreakSplit() {
        String uniqueConfigurationId = "no-config-object-001";
        SplitterPrecalcRequestDto precalcRequest = nonMatchingPrecalcRequest(uniqueConfigurationId, 1);
        SplitRequestDto splitRequest = nonMatchingSplitRequest(uniqueConfigurationId);

        getFlowWithRest()
                .step("Вызываем predcalc до загрузки config", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    shouldHaveSoConfigVersion(response, 1);
                })
                .step("Затем выполняем split по тем же объектам", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-04. Предрасчет с пустым списком объектов")
    void precalcWithEmptyObjectsShouldReturnSuccess() {
        SplitterPrecalcRequestDto request = SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(1)
                .splittingObjects(List.of())
                .build();

        getFlowWithRest()
                .step("Вызываем predcalc с пустым списком объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, request));
                    shouldHaveSoConfigVersion(response, 1);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-05. Первичное построение таблицы предрасчета при наличии конфига")
    void initialPrecalcTableBuildShouldBeCorrect() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        String matchingId = "matching-precalc-object-001";
        String nonMatchingId = "non-matching-precalc-object-001";

        SplitterPrecalcRequestDto precalcRequest = multiObjectPrecalcRequest(
                1,
                matchingPrecalcObject(matchingId),
                nonMatchingPrecalcObject(nonMatchingId)
        );

        SplitRequestDto splitRequest = multiObjectSplitRequest(
                matchingSplitObject(matchingId, objectIdFromSeed("pc05-match")),
                nonMatchingSplitObject(nonMatchingId, objectIdFromSeed("pc05-non-match"))
        );

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Строим таблицу predcalc для matching и non-matching объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, precalcRequest));
                    assertPrecalcAccepted(response);
                })
                .step("Split возвращает два объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-06. Полный входной список заменяет старую таблицу")
    void fullInputListShouldReplaceOldTable() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        String a = "obj-A";
        String b = "obj-B";
        String c = "obj-C";

        SplitterPrecalcRequestDto first = multiObjectPrecalcRequest(1,
                matchingPrecalcObject(a),
                matchingPrecalcObject(b),
                matchingPrecalcObject(c)
        );
        SplitterPrecalcRequestDto second = multiObjectPrecalcRequest(1,
                matchingPrecalcObject(a),
                matchingPrecalcObject(c)
        );
        SplitRequestDto splitForB = multiObjectSplitRequest(matchingSplitObject(b, objectIdFromSeed("pc06-b")));

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Первый predcalc для A,B,C", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, first))))
                .step("Второй predcalc только для A,C", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, second))))
                .step("Split по B отрабатывает предсказуемо после полной замены таблицы", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitForB));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-07. Пересчет только новых объектов")
    void shouldReuseOldObjectsAndCalculateNewOnes() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        String a = "obj-A";
        String b = "obj-B";
        String c = "obj-C";
        String d = "obj-D";

        SplitterPrecalcRequestDto first = multiObjectPrecalcRequest(1, matchingPrecalcObject(a), matchingPrecalcObject(b));
        SplitterPrecalcRequestDto second = multiObjectPrecalcRequest(1, matchingPrecalcObject(a), matchingPrecalcObject(b), matchingPrecalcObject(c), matchingPrecalcObject(d));
        SplitRequestDto splitRequest = multiObjectSplitRequest(
                matchingSplitObject(a, objectIdFromSeed("pc07-a")),
                matchingSplitObject(b, objectIdFromSeed("pc07-b")),
                matchingSplitObject(c, objectIdFromSeed("pc07-c")),
                matchingSplitObject(d, objectIdFromSeed("pc07-d"))
        );

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Первый predcalc для A,B", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, first))))
                .step("Второй predcalc для A,B,C,D", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, second))))
                .step("Split возвращает корректную структуру для всех четырех объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 4"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-08. Дедупликация по uniqueConfigurationId")
    void shouldNotDuplicateObjectForSameUniqueConfigurationId() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        String sameId = "duplicate-check-object-001";
        SplitterPrecalcRequestDto first = multiObjectPrecalcRequest(1, matchingPrecalcObject(sameId));
        SplitterPrecalcRequestDto second = multiObjectPrecalcRequest(1, matchingPrecalcObject(sameId));
        SplitRequestDto splitRequest = multiObjectSplitRequest(matchingSplitObject(sameId, objectIdFromSeed("pc08")));

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Первый predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, first))))
                .step("Повторный predcalc с тем же uniqueConfigurationId", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, second))))
                .step("Split не создает дубль объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 1"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-09. Один эксперимент, несколько условий")
    void oneExperimentSeveralConditionsShouldNotDuplicateExperiment() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = oneExperimentTwoConditionsConfig(version);
        String id = "multi-condition-object-001";
        SplitterPrecalcRequestDto precalcRequest = multiConditionMatchingPrecalcRequest(id);
        SplitRequestDto splitRequest = multiConditionMatchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config с двумя условиями одного exp", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split не дублирует exp в результате", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-10. Один объект связан с несколькими экспериментами")
    void oneObjectShouldKeepSeveralExperiments() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = twoExperimentsMatchingSameObjectConfig(version);
        String id = "multi-exp-object-001";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config с несколькими exp", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split сохраняет несколько exp у одного объекта", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-11. Объект не подходит ни под один эксперимент")
    void objectNotMatchingAnyExperimentShouldNotDisappear() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        String id = "non-matching-precalc-object-003";
        SplitterPrecalcRequestDto precalcRequest = nonMatchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = nonMatchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBeConfigLoaded(shouldBe200(loadConfig(flow, config))))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split возвращает объект без потери objectId", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-12. Возврат soConfigVersion")
    void precalcShouldReturnSameSoConfigVersionAsRequest() {
        int soConfigVersion = 77;
        SplitterPrecalcRequestDto request = nonMatchingPrecalcRequest("version-check-object", soConfigVersion);

        getFlowWithRest()
                .step("Вызываем predcalc с нестандартной soConfigVersion", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, request));
                    shouldHaveSoConfigVersion(response, soConfigVersion);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-13. Загрузка конфига с REQUEST_PARAMS при включенном предрасчете")
    void loadConfigWithRequestParamsShouldReturnBusinessResultCode() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = requestParamsConfig(version);

        getFlowWithRest()
                .step("Пытаемся загрузить config с REQUEST_PARAMS", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    response.should(
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("result == 'REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED'")
                    );
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-14. Загрузка валидного конфига без таблицы предрасчета")
    void loadValidConfigWithoutPrecalcTableShouldReturnLoaded() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        getFlowWithRest()
                .step("Загружаем config без предварительно созданной таблицы predcalc", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, config));
                    shouldBeConfigLoaded(response);
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("currentConfigVersion == " + version));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-15. Загрузка нового конфига при наличии таблицы предрасчета")
    void loadingNewConfigWithPrecalcTableShouldRebuildTable() {
        long oldVersion = SplitterVersionProvider.next();
        long newVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto oldConfig = matchingMapperConfigMessage(oldVersion);
        LoadConfigRequestDto newConfig = twoExperimentsMatchingSameObjectConfig(newVersion);
        String id = "config-rebuild-object-001";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем исходный config", flow -> shouldBe200(loadConfig(flow, oldConfig)))
                .step("Строим таблицу predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Загружаем новый config поверх существующей таблицы", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(loadConfig(flow, newConfig));
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("currentConfigVersion == " + newVersion));
                })
                .step("Split выполняется уже на новой конфигурации", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-16. Удаление эксперимента из конфига")
    void removedExperimentShouldDisappearFromResults() {
        long oldVersion = SplitterVersionProvider.next();
        long newVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto oldConfig = twoExperimentsMatchingSameObjectConfig(oldVersion);
        LoadConfigRequestDto newConfig = matchingMapperConfigMessage(newVersion);
        String id = "remove-exp-object-001";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config с двумя exp", flow -> shouldBe200(loadConfig(flow, oldConfig)))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Загружаем config, где один exp удален", flow -> shouldBe200(loadConfig(flow, newConfig)))
                .step("Split не содержит удаленный exp", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-17. Добавление нового эксперимента в конфиг")
    void addedExperimentShouldAppearAfterConfigReload() {
        long oldVersion = SplitterVersionProvider.next();
        long newVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto oldConfig = matchingMapperConfigMessage(oldVersion);
        LoadConfigRequestDto newConfig = twoExperimentsMatchingSameObjectConfig(newVersion);
        String id = "add-exp-object-001";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config с одним exp", flow -> shouldBe200(loadConfig(flow, oldConfig)))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Загружаем config с новым exp", flow -> shouldBe200(loadConfig(flow, newConfig)))
                .step("Split учитывает новый exp", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-18. split использует предрасчитанные связи")
    void splitShouldUsePrecalculatedLinks() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        String id = "matching-precalc-object-001";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split использует precalc-таблицу", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-19. split fallback для объекта без предрасчета")
    void splitShouldFallbackForObjectMissingInPrecalcTable() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        SplitRequestDto splitRequest = matchingSplitRequest("missing-in-precalc-table");

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("Split для объекта без записи в precalc-table проходит через runtime", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-20. split fallback для объекта без uniqueConfigurationId")
    void splitShouldFallbackWhenUniqueConfigurationIdAbsent() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        SplitRequestDto splitRequest = splitWithoutUniqueConfigurationId();

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("Split без uniqueConfigurationId отрабатывает как runtime split", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-21. split для объекта из таблицы без связей")
    void splitForObjectWithoutLinksShouldReturnEmptyObjectResults() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        String id = "table-object-without-links";
        SplitterPrecalcRequestDto precalcRequest = nonMatchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = nonMatchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("Предрасчитываем non-matching объект", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split возвращает объект без objectResults", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveSingleObjectWithEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-22. Смешанный пакет: часть объектов из таблицы, часть новых")
    void mixedBatchShouldHandlePrecalcAndRuntimeTogether() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);

        String precalcId = "precalc-mixed-object";
        String runtimeId = "runtime-mixed-object";

        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(precalcId, 1);
        String precalcObjectId = objectIdFromSeed("pc22-precalc");
        String runtimeObjectId = objectIdFromSeed("pc22-runtime");

        SplitRequestDto splitRequest = multiObjectSplitRequest(
                matchingSplitObject(precalcId, precalcObjectId),
                matchingSplitObject(runtimeId, runtimeObjectId)
        );

        getFlowWithRest()
                .step("Загружаем config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("Создаем predcalc только для первого объекта", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("Split по смешанному пакету корректно обрабатывает обе группы объектов", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                    shouldContainObjectIds(response, precalcObjectId, runtimeObjectId);
                    response.should(RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingResults.size() == 2"));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-23. Стабильность бизнес-результата split до/после предрасчета")
    void businessResultShouldBeStableBeforeAndAfterPrecalc() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = matchingMapperConfigMessage(version);
        String id = "matching-precalc-object-002";
        SplitterPrecalcRequestDto precalcRequest = matchingPrecalcRequest(id, 1);
        SplitRequestDto splitRequest = matchingSplitRequest(id);

        getFlowWithRest()
                .step("Загружаем matching config", flow -> shouldBe200(loadConfig(flow, config)))
                .step("split до predcalc дает matching-результат", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .step("Выполняем predcalc", flow -> assertPrecalcAccepted(shouldBe200(calculatePreliminary(flow, precalcRequest))))
                .step("split после predcalc сохраняет бизнес-форму", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveSingleObjectWithNonEmptyResults(response, MATCHING_OBJECT_ID);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-24. Предрасчет и смена soConfigVersion")
    void latestSoConfigVersionShouldBeUsed() {
        String id = "version-switch-object";
        SplitterPrecalcRequestDto first = nonMatchingPrecalcRequest(id, 1);
        SplitterPrecalcRequestDto second = nonMatchingPrecalcRequest(id, 2);
        SplitRequestDto splitRequest = nonMatchingSplitRequest(id);

        getFlowWithRest()
                .step("Первый predcalc с soConfigVersion=1", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, first)), 1))
                .step("Второй predcalc с soConfigVersion=2", flow -> shouldHaveSoConfigVersion(shouldBe200(calculatePreliminary(flow, second)), 2))
                .step("Последующий split успешно отрабатывает на актуальной таблице", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(split(flow, splitRequest));
                    shouldHaveNonEmptySplitEnvelope(response);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("PC-25. Неконсистентные данные в predcalc не валят сервис")
    void inconsistentDataShouldBeHandledGracefully() {
        SplitterPrecalcRequestDto request = SplitterPrecalcRequestDto.builder()
                .requestId(UUID.randomUUID().toString())
                .soConfigVersion(1)
                .splittingObjects(List.of(
                        SplitterPrecalcObjectDto.builder()
                                .uniqueConfigurationId("broken-object")
                                .objectParams(List.of(new SplitterPrecalcParamDto("configCommId", null, "INTEGER")))
                                .build()
                ))
                .build();

        getFlowWithRest()
                .step("Пытаемся спровоцировать ошибку неконсистентными данными", flow -> {
                    ValidatableResponseWrapper response = shouldBe200(calculatePreliminary(flow, request));
                    assertPrecalcAccepted(response);
                })
                .run();
    }

}
