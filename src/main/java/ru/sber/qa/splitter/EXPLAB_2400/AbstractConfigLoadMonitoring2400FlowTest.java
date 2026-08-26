package ru.sber.qa.splitter.EXPLAB_2400;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.LoadConfigResponseDto;
import dto.splitter.precalc.SplitterPrecalcRequestDto;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.analytictests.common.AbstractAnalyticSplitterFlowTest;
import util.splittercheck.SplitterResponseReader;

import java.util.concurrent.atomic.AtomicInteger;

import static util.SplitterPrecalcAssertions.shouldBe200;
import static util.SplitterPrecalcAssertions.shouldHaveSoConfigVersion;

abstract class AbstractConfigLoadMonitoring2400FlowTest extends AbstractAnalyticSplitterFlowTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicInteger SO_CONFIG_VERSION =
            new AtomicInteger((int) (System.currentTimeMillis() / 1000L));

    private final SplitterConfigLoadMonitoring2400Flow monitoringFlow =
            new SplitterConfigLoadMonitoring2400Flow();

    protected LoadConfigResponseDto loadConfig(FlowWithRest flow, LoadConfigRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(flow.restCustomSteps().splitterSteps().loadConfig(request));
        return deserialize(response, LoadConfigResponseDto.class, "loadConfig response");
    }

    protected void preCalculate(FlowWithRest flow, SplitterPrecalcRequestDto request) {
        ValidatableResponseWrapper response = shouldBe200(
                flow.restCustomSteps().splitterSteps().calculatePreliminary(request));
        shouldHaveSoConfigVersion(response, request.getSoConfigVersion());
    }

    protected ConfigLoadMonitoringCapture2400 loadConfigAndCaptureMonitoring(FlowWithRest flow,
                                                                            KafkaService kafkaService,
                                                                            LoadConfigRequestDto request,
                                                                            String expectedResult) {
        return monitoringFlow.captureConfigLoadEvent(
                kafkaService,
                request.getMessageId(),
                expectedResult,
                () -> loadConfig(flow, request));
    }

    protected int nextSoConfigVersion() {
        return SO_CONFIG_VERSION.incrementAndGet();
    }

    protected String uniqueConfigurationId(String scenario, int index) {
        return "explab-2400-" + scenario + "-" + System.nanoTime() + "-uc-" + index;
    }

    private static <T> T deserialize(ValidatableResponseWrapper response,
                                     Class<T> targetType,
                                     String context) {
        var snapshot = SplitterResponseReader.snapshot(response);
        var json = snapshot.requireJsonBody("Ожидали JSON body: " + context);
        try {
            return OBJECT_MAPPER.treeToValue(json, targetType);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Не удалось преобразовать " + context + " в " + targetType.getSimpleName()
                    + "\n" + snapshot.prettyBody(), exception);
        }
    }
}
