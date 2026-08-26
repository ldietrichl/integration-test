package ru.sber.qa.splitter.NewTest;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.SplitterAssertions;
import util.support.SplitterVersionProvider;
import ru.sber.qa.allure.CriticalRegression;

import static request.splitter.SplitterConfigTestDataFactory.emptyExperimentsConfig;
import static request.splitter.SplitterConfigTestDataFactory.invalidNoConditionsConfig;
import static request.splitter.SplitterConfigTestDataFactory.invalidNoSaltOrLayerConfig;
import static request.splitter.SplitterConfigTestDataFactory.invalidRangesConfig;
import static request.splitter.SplitterConfigTestDataFactory.validLayerExperimentConfig;
import static request.splitter.SplitterConfigTestDataFactory.validSingleExperimentConfig;
import static request.splitter.SplitterTestDataFactory.splitForMatchedObject;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("splitter-config")
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public class SplitterConfigFlowTest_extended extends AbstractNewSplitterFlowTest {

    private final SplitterAssertions assertions = new SplitterAssertions();
    private static final String MATCHED_OBJECT_ID = "22222222-2222-2222-2222-222222222222";

    @CriticalRegression
    @Test
    @DisplayName("CFG-01. Валидный конфиг становится активным")
    void validConfigShouldBecomeActive() {
        long version = SplitterVersionProvider.next();

        LoadConfigRequestDto request = validSingleExperimentConfig(version, false);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем валидный конфиг",
                        flow -> loadConfig(flow, request)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что конфиг стал активным через split",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(version)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-02. Более старая версия не должна становиться активной")
    void oldVersionShouldBeIgnored() {
        long newVersion = SplitterVersionProvider.next();
        long oldVersion = SplitterVersionProvider.nextOlderThan(newVersion);

        LoadConfigRequestDto newRequest = validSingleExperimentConfig(newVersion, false);
        LoadConfigRequestDto oldRequest = validSingleExperimentConfig(oldVersion, false);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем новую валидную версию",
                        flow -> loadConfig(flow, newRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Считываем фактически активную версию после новой загрузки",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(newVersion)))
                .step("Пытаемся загрузить более старую версию",
                        flow -> loadConfig(flow, oldRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что активная версия не изменилась",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(newVersion)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-03. forceConfigLoad=true позволяет активировать старую версию")
    void forceLoadShouldActivateOlderVersion() {
        long newVersion = SplitterVersionProvider.next();
        long forcedOldVersion = SplitterVersionProvider.nextOlderThan(newVersion);

        LoadConfigRequestDto newRequest = validSingleExperimentConfig(newVersion, false);
        LoadConfigRequestDto forcedOldRequest = validSingleExperimentConfig(forcedOldVersion, true);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем новую валидную версию",
                        flow -> loadConfig(flow, newRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Загружаем старую версию с forceConfigLoad=true",
                        flow -> loadConfig(flow, forcedOldRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что активной стала старая версия",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(forcedOldVersion)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-04. Конфиг без salt/layer отклоняется")
    void configWithoutSaltOrLayerShouldBeRejected() {
        long baseVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();

        LoadConfigRequestDto baseRequest = validSingleExperimentConfig(baseVersion, false);
        LoadConfigRequestDto invalidRequest = invalidNoSaltOrLayerConfig(invalidVersion);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем базовый валидный конфиг",
                        flow -> loadConfig(flow, baseRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Считываем активную версию до невалидной загрузки",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .step("Отправляем невалидный конфиг без salt/layer",
                        flow -> loadConfig(flow, invalidRequest)
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активная версия не изменилась",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-05. Конфиг без objectSelectConditions отклоняется")
    void configWithoutConditionsShouldBeRejected() {
        long baseVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();

        LoadConfigRequestDto baseRequest = validSingleExperimentConfig(baseVersion, false);
        LoadConfigRequestDto invalidRequest = invalidNoConditionsConfig(invalidVersion);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем базовый валидный конфиг",
                        flow -> loadConfig(flow, baseRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Считываем активную версию до невалидной загрузки",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .step("Отправляем невалидный конфиг без objectSelectConditions",
                        flow -> loadConfig(flow, invalidRequest)
                                .should(haveStatusCode(HttpStatus.SC_BAD_REQUEST)))
                .step("Проверяем, что активная версия не изменилась",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-06. Конфиг с плохими диапазонами не должен менять активную версию")
    void configWithInvalidRangesShouldNotBecomeActive() {
        long baseVersion = SplitterVersionProvider.next();
        long invalidVersion = SplitterVersionProvider.next();

        LoadConfigRequestDto baseRequest = validSingleExperimentConfig(baseVersion, false);
        LoadConfigRequestDto invalidRequest = invalidRangesConfig(invalidVersion);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем опорный валидный конфиг",
                        flow -> loadConfig(flow, baseRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Считываем активную версию до невалидной загрузки",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .step("Пробуем загрузить конфиг с плохими диапазонами",
                        flow -> loadConfig(flow, invalidRequest)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что активная версия осталась прежней",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(baseVersion)))
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("CFG-07. Пустой список experiments загружается и не дает результатов по объекту")
    void emptyExperimentsConfigShouldLoadAndReturnNoResults() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto request = emptyExperimentsConfig(version);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем конфиг с пустым списком experiments",
                        flow -> loadConfig(flow, request)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что активна новая версия и по объекту нет результатов",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(version),
                                        assertions.shouldHaveObjectResultsSize(MATCHED_OBJECT_ID, 0)))
                .run();
    }

    @Test
    @Disabled("Exploratory only: на текущем стенде layer-конфиг без salt отклоняется 400, сценарий исключен из contract-набора до уточнения правила")
    @DisplayName("CFG-08. Layer-конфиг без salt загружается и участвует в split")
    void layerBasedConfigShouldLoadWithoutSalt() {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto request = validLayerExperimentConfig(version);
        SplitRequestDto split = splitForMatchedObject();

        getFlowWithRest()
                .step("Загружаем layer-конфиг",
                        flow -> loadConfig(flow, request)
                                .should(haveStatusCode(HttpStatus.SC_OK)))
                .step("Проверяем, что layer-конфиг стал активным и вернул результат",
                        flow -> split(flow, split)
                                .should(haveStatusCode(HttpStatus.SC_OK),
                                        assertions.shouldHaveConfigVersion(version),
                                        assertions.shouldContainObject(MATCHED_OBJECT_ID),
                                        assertions.shouldContainMain(MATCHED_OBJECT_ID),
                                        assertions.shouldContainAll(MATCHED_OBJECT_ID)))
                .run();
    }

    private ValidatableResponseWrapper loadConfig(FlowWithRest flow, LoadConfigRequestDto request) {
        return flow.restCustomSteps().splitterSteps().loadConfig(request);
    }

    private ValidatableResponseWrapper split(FlowWithRest flow, SplitRequestDto request) {
        return flow.restCustomSteps().splitterSteps().split(request);
    }


}
