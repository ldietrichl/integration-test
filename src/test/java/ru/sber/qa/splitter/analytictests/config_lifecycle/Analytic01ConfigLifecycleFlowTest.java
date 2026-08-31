package ru.sber.qa.splitter.analytictests.config_lifecycle;


import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import ru.sber.qa.splitter.analytictests.common.AnalyticTag;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
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
import util.support.SplitterVersionProvider;

import java.util.List;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;
import static util.SplitterPrecalcAssertions.shouldBe200;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
/**
 * REST-only analytic splitter coverage class.
 *
 * Required ConfigMap contract: src/test/resources/splitter/configmap/mapper-current.yml.
 * The test does not apply ConfigMap automatically; the target environment must be configured with compatible rules.
 */
@AnyConfigLoadMode
public class Analytic01ConfigLifecycleFlowTest extends AbstractAnalyticSplitterFlowTest {

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Отправить запрос с конфигом, должен быть ответ; после загрузки корректный split должен работать на активной конфигурации.")
    @DisplayName("AN-CFG-01. REST config загружается и становится активным для split")
    void restConfigShouldLoadAndBecomeActiveForSplit() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = config(version, experiment(7101, "AN-CFG-01", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-CFG-01", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем валидный REST split config", flow -> loadConfigStep(flow, config))
                .step("Проверяем, что split работает на загруженной версии", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, version);
                    assertObjectHasMainAndAll(response, MATCHING_OBJECT_ID);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7101L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Получение конфигурации с версией меньше загруженной и равной загруженной. Должна быть проигнорирована.")
    @DisplayName("AN-CFG-02. Более старая версия без forceConfigLoad не меняет активный config")
    void olderVersionWithoutForceShouldNotOverrideActiveConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long oldVersion = SplitterVersionProvider.nextOlderThan(activeVersion);
        LoadConfigRequestDto activeConfig = config(activeVersion, true, experiment(7102, "AN-CFG-02-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        LoadConfigRequestDto oldConfig = config(oldVersion, false, experiment(7103, "AN-CFG-02-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-CFG-02", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем активную версию", flow -> loadConfigStep(flow, activeConfig))
                .step("Пытаемся загрузить старую версию без forceConfigLoad", flow -> shouldBe200(load(flow, oldConfig)))
                .step("Проверяем, что активная версия не изменилась", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, activeVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7102L);
                })
                .run();
    }

    @CriticalRegression
    @Test
    @AnalyticTag("Аналитика: Загрузка конфига с forceConfigLoad. Версия больше, меньше и равна текущей. Должна быть загружена.")
    @DisplayName("AN-CFG-03. forceConfigLoad=true позволяет загрузить версию ниже активной")
    void forceConfigLoadShouldOverrideVersionControl() {
        long activeVersion = SplitterVersionProvider.next();
        long forcedOldVersion = SplitterVersionProvider.nextOlderThan(activeVersion);
        LoadConfigRequestDto activeConfig = config(activeVersion, true, experiment(7104, "AN-CFG-03-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        LoadConfigRequestDto forcedConfig = config(forcedOldVersion, true, experiment(7105, "AN-CFG-03-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-CFG-03", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем активную версию", flow -> loadConfigStep(flow, activeConfig))
                .step("Загружаем старую версию с forceConfigLoad=true", flow -> loadConfigStep(flow, forcedConfig))
                .step("Проверяем, что активной стала forced-версия", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, forcedOldVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7105L);
                })
                .run();
    }

    @Test
    @AnalyticTag("Аналитика: Получение конфигурации с некорректным экспериментом. Должна быть проигнорирована.")
    @DisplayName("AN-CFG-04. Невалидный config не должен становиться активным")
    void invalidConfigShouldNotBecomeActive() {
        long activeVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = config(activeVersion, experiment(7106, "AN-CFG-04-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        LoadConfigRequestDto invalidConfig = config(invalidVersion, experiment(7107, null, List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        SplitRequestDto split = splitRequest("AN-CFG-04", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем валидный config", flow -> loadConfigStep(flow, activeConfig))
                .step("Пробуем загрузить config без salt", flow -> load(flow, invalidConfig).should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активная версия не изменилась", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, activeVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7106L);
                })
                .run();
    }

    @Test
    @AnalyticTag("Аналитика: Получение конфига с точкой сплиттования, отличной от конфигурации. Должна быть проигнорирована.")
    @DisplayName("AN-CFG-05. Config с чужой splittingPointCode не должен подменять MAPPER config")
    void foreignSplittingPointConfigShouldNotOverrideMapperConfig() {
        long activeVersion = SplitterVersionProvider.next();
        long foreignVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = config(activeVersion, experiment(7108, "AN-CFG-05-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        LoadConfigRequestDto foreignConfig = config(foreignVersion, experiment(7109, "AN-CFG-05-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        foreignConfig.setSplittingPointCode("REACTIONS");
        SplitRequestDto split = splitRequest("AN-CFG-05", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем валидный MAPPER config", flow -> loadConfigStep(flow, activeConfig))
                .step("Отправляем config с чужой splittingPointCode=REACTIONS", flow -> load(flow, foreignConfig))
                .step("Проверяем, что MAPPER split продолжает работать на исходной версии", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, activeVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7108L);
                })
                .run();
    }

    @Test
    @AnalyticTag("Аналитика: Получение конфига без точки сплиттования. Должна быть проигнорирована.")
    @DisplayName("AN-CFG-06. Config без splittingPointCode не должен становиться активным")
    void missingSplittingPointConfigShouldNotBecomeActive() {
        long activeVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = config(activeVersion, experiment(7110, "AN-CFG-06-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        LoadConfigRequestDto invalidConfig = config(invalidVersion, experiment(7111, "AN-CFG-06-B", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "1"))));
        invalidConfig.setSplittingPointCode(null);
        SplitRequestDto split = splitRequest("AN-CFG-06", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));

        getFlowWithRest()
                .step("Загружаем валидный MAPPER config", flow -> loadConfigStep(flow, activeConfig))
                .step("Отправляем config без splittingPointCode", flow -> load(flow, invalidConfig).should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активная версия не изменилась", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, activeVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7110L);
                })
                .run();
    }

    @Test
    @AnalyticTag("Аналитика: Получение конфигурации с некорректным JSON. Должна быть проигнорирована.")
    @DisplayName("AN-CFG-07. Malformed JSON в /config не должен становиться активным")
    void malformedConfigJsonShouldNotBecomeActive() {
        long activeVersion = SplitterVersionProvider.next();
        LoadConfigRequestDto activeConfig = config(activeVersion, experiment(7112, "AN-CFG-07-A", List.of(objectParamEqualsCondition(1, "id", "1", "INTEGER")), List.of(fullRangeGroup("A", 1, "0"))));
        SplitRequestDto split = splitRequest("AN-CFG-07", object(MATCHING_OBJECT_ID, param("id", "1", "INTEGER")));
        String malformedJson = "{\"messageId\":\"broken\",\"splittingConfig\":{";

        getFlowWithRest()
                .step("Загружаем валидный MAPPER config", flow -> loadConfigStep(flow, activeConfig))
                .step("Отправляем syntactically invalid JSON в /config", flow -> load(flow, malformedJson).should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активный config не сломан", flow -> {
                    var response = shouldBe200(split(flow, split));
                    assertConfigVersion(response, activeVersion);
                    assertMainExp(response, MATCHING_OBJECT_ID, 7112L);
                })
                .run();
    }

    @Disabled("Manual: требуется изолированный запуск на чистом стенде/после рестарта pod без загруженного split config")
    @ManualTest
    @Test
    @AnalyticTag("Аналитика: Работа без конфига. Отправить запрос на сплиттование без загруженного конфига, должна быть ошибка.")
    @DisplayName("AN-CFG-08. Manual: split без загруженного config должен вернуть ошибку")
    void splitWithoutLoadedConfigShouldReturnNoConfigErrorManual() {
    }

}
