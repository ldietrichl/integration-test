package ru.sber.qa.splitter.EXPLAB_2739;

import com.fasterxml.jackson.databind.JsonNode;
import config.environment.EnvironmentConfigurationExample;
import dto.splitter.config.ExperimentDto;
import dto.splitter.config.GroupDto;
import dto.splitter.config.LoadConfigRequestDto;
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
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import ru.sber.qa.splitter.tests_v9.common.AbstractSplitterV9FlowTest;
import util.support.SplitterVersionProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@CriticalRegression
@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
@ResourceLock("splitter-config")
@DisplayName("EXPLAB-2739. Kafka config load: empty rules")
public class SplitterConfigKafkaEmptyRules2739FlowTest extends AbstractSplitterV9FlowTest {

    private static final long EMPTY_RULES_EXP_ID = 273901L;
    private static final String SALT = "EXPLAB-2739-SALT";
    private static final String OBJECT_ID = "explab-2739-empty-rules-object";

    private final SplitterConfigKafkaLoad2739Flow kafkaFlow = new SplitterConfigKafkaLoad2739Flow();

    @Test
    @DisplayName("EXPLAB-2739-CFG-08. Kafka-загрузка принимает config с objectSelectConditions[].rules=[]")
    void kafkaLoadShouldAcceptConfigWithEmptyRulesAndActivateItForSplit(KafkaService kafkaService) {
        long version = SplitterVersionProvider.next();
        LoadConfigRequestDto config = configFor(EndpointMode.MAPPER,
                version,
                emptyRulesExperiment());
        SplitRequestDto request = splitRequest("EXPLAB-2739-CFG-08",
                objectWithUniqueId("explab-2739-empty-rules-uid",
                        OBJECT_ID,
                        param("unrelated", "value", "STRING")));
        long[] kafkaSince = new long[1];

        getFlowWithRest()
                .step("Отправляем config с пустым rules[] в Kafka topic splitting-config-created", flow -> {
                    kafkaSince[0] = System.currentTimeMillis();
                    kafkaFlow.sendConfig(kafkaService, config);
                })
                .step("Проверяем статус CONFIG_LOADED в splitting-config-requested-and-received", flow -> {
                    JsonNode status = kafkaFlow.findStatusByConfigMessageId(kafkaService,
                            config.getMessageId(),
                            kafkaSince[0]);
                    assertEquals("STATUS", SplitterConfigKafkaLoad2739Flow.normalizedText(status, "messageType"),
                            status.toPrettyString());
                    assertEquals("CONFIG_LOADED", SplitterConfigKafkaLoad2739Flow.normalizedText(status, "status"),
                            status.toPrettyString());
                    assertEquals(config.getMessageId(), SplitterConfigKafkaLoad2739Flow.text(status, "configMessageId"),
                            status.toPrettyString());
                    assertEquals(String.valueOf(config.getConfigVersion()),
                            SplitterConfigKafkaLoad2739Flow.text(status, "newConfigVersion"),
                            status.toPrettyString());
                })
                .step("Проверяем, что Kafka-loaded config стал активным для split", flow -> {
                    ValidatableResponseWrapper response = split(flow, EndpointMode.MAPPER, request);
                    assertBasicResponseContract(response, request, version);
                    assertFirstRuleExp(response, OBJECT_ID, "MAIN", EMPTY_RULES_EXP_ID, "A", "A");
                    assertRuleExpIdsExactly(response, OBJECT_ID, "ALL", EMPTY_RULES_EXP_ID);
                })
                .run();
    }

    private ExperimentDto emptyRulesExperiment() {
        return experiment((int) EMPTY_RULES_EXP_ID,
                SALT,
                List.of(condition(1, List.of())),
                List.of(fullRangeActionTypeZeroGroup()));
    }

    private GroupDto fullRangeActionTypeZeroGroup() {
        return group("A",
                List.of(share(0, 10000)),
                List.of(resultWithParams(1, param("actionType", "0", "INTEGER"))));
    }
}

