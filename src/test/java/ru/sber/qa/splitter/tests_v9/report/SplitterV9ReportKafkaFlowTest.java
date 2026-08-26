package ru.sber.qa.splitter.tests_v9.report;

import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.LoadConfigRequestDto;
import dto.splitter.config.ShareDto;
import dto.splitter.split.SplitRequestDto;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("Tests-v9. Report/Kafka проверки результата сплиттования")
public class SplitterV9ReportKafkaFlowTest extends AbstractSplitterV9FlowTest {

    @Test
    @DisplayName("SPL-V9-REP-01. Kafka-report содержит resultDt, requestId, splittingId и полный MAPPER результат")
    void mapperKafkaReportShouldContainResultDtAndFullResult(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-REP-01", V9_SALT, 1000, 1500);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем MAPPER config для проверки Kafka-report", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем MAPPER split и фиксируем REST-контракт", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertRuleResultSize(response, OBJECT_1, "MAIN", 1);
                    assertRuleResultSize(response, OBJECT_2, "MAIN", 1);
                })
                .step("Читаем Kafka-report и проверяем полный payload", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                    assertKafkaReportMeta(payload, request, version);
                    assertJsonContainsText(payload, "ALL");
                    assertJsonContainsText(payload, "MAIN");
                    assertJsonContainsText(payload, "finalExpGroup");
                    assertJsonContainsNullField(payload, "finalExpGroup");
                })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-REP-02. Kafka-report содержит несработавшие группы, которые нужны документу для анализа ALL")
    void mapperKafkaReportShouldContainNotWorkedGroups(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = mapperAlternativeConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-REP-02", V9_SALT, 5000, 5500);
        SplitRequestDto request = splitRequest(splittingId,
                object(OBJECT_1, param("id1", "1", "INTEGER")),
                object(OBJECT_2, param("id2", "2", "INTEGER")));
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем MAPPER config с интервалом, где есть несработавшие связанные группы", flow -> loadConfig(flow, EndpointMode.MAPPER, config))
                .step("Выполняем split и проверяем базовый REST-контракт", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                })
                .step("Проверяем, что report payload содержит finalExpGroup=null", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                    assertKafkaReportMeta(payload, request, version);
                    assertJsonContainsNullField(payload, "finalExpGroup");
                    assertJsonContainsText(payload, "expGroup");
                })
                .run();
    }

    @Test
    @DisplayName("SPL-V9-REP-03. Kafka-report для REACTIONS содержит resultDt и splittingPoint=REACTIONS")
    void reactionsKafkaReportShouldContainResultDtAndSplittingPoint(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = reactionsConfig(version);
        String splittingId = splittingIdForRange("SPL-V9-REP-03", V9_SALT, 0, 2500);
        SplitRequestDto request = splitRequest(splittingId, object(OBJECT_REACTIONS, param("segment", "v9", "STRING")));
        long since = System.currentTimeMillis();

        getFlowWithRest()
                .step("Загружаем REACTIONS config для проверки Kafka-report", flow -> loadConfig(flow, EndpointMode.REACTIONS, config))
                .step("Выполняем REACTIONS split", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.REACTIONS, request);
                    assertBasicResponseContract(response, request, version);
                    assertRuleExpIdsExactly(response, OBJECT_REACTIONS, "MAIN", 4L, 5L, 6L);
                    assertRuleExpsUseWorkedGroups(response, OBJECT_REACTIONS, "MAIN");
                    assertRuleExpsHaveNoExpFlags(response, OBJECT_REACTIONS, "MAIN");
                    assertNoAlternativeTrueAnywhere(response);
                })
                .step("Проверяем Kafka-report REACTIONS", flow -> {
                    String payload = findKafkaPayloadByRequestId(kafkaService, request.getRequestId(), since);
                    assertKafkaReportMeta(payload, request, version);
                    assertJsonContainsText(payload, "REACTIONS");
                    assertJsonContainsText(payload, "MAIN");
                })
                .run();
    }

    private LoadConfigRequestDto mapperAlternativeConfig(long version) {
        return configFor(EndpointMode.MAPPER, version,
                experiment(1,
                        V9_SALT,
                        List.of(
                                objectParamEqualsCondition(1, "id1", "1", "INTEGER"),
                                objectParamEqualsCondition(2, "id2", "2", "INTEGER")),
                        List.of(
                                groupWithDocResult("A", List.of(share(0, 2500)), 1, "0", "1"),
                                groupWithDocResult("B", List.of(share(2500, 5000)), 2, "1", "2"))),
                experiment(2,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id1", "1", "INTEGER")),
                        List.of(groupWithDocResult("A", List.of(share(0, 1000), share(2500, 3500), share(5000, 6000)), 1, "1", "2"))),
                experiment(3,
                        V9_SALT,
                        List.of(objectParamEqualsCondition(1, "id2", "2", "INTEGER")),
                        List.of(groupWithDocResult("A", List.of(share(0, 500), share(1000, 1500), share(2500, 3000), share(3500, 4000), share(5000, 5500), share(6000, 6500)), 1, "3", "3"))));
    }

    private LoadConfigRequestDto reactionsConfig(long version) {
        return configFor(EndpointMode.REACTIONS, version,
                reactionExperiment(1, 1, 1, 0, 2500, "1"),
                reactionExperiment(2, 2, 2, 0, 7500, "2"),
                reactionExperiment(3, 2, 2, 0, 2500, "3"),
                reactionExperiment(4, 3, 3, 0, 5000, "4"),
                reactionExperiment(5, 3, 3, 0, 7500, "5"),
                reactionExperiment(6, 3, 3, 0, 5000, "6"));
    }

    private ExperimentDto reactionExperiment(int expId,
                                             int layerId,
                                             int layerPriority,
                                             int shareFrom,
                                             int shareTo,
                                             String resultValue) {
        return layeredExperiment(expId,
                V9_SALT,
                layerId,
                layerPriority,
                List.of(objectParamEqualsCondition(1, "segment", "v9", "STRING")),
                List.of(groupWithDocResult("A", shareFrom, shareTo, 1, "1", resultValue)));
    }
}
