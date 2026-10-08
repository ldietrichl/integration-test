package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import steps.flow.splitter.reactions.ReactionsMonitoringSteps;

import config.environment.special.EnvironmentConfigWithReactionsPrecalc;

import com.fasterxml.jackson.databind.JsonNode;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.services.kafka.KafkaService;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static steps.flow.splitter.reactions.ReactionsProfileSteps.profile;

@SetEnvironmentConfiguration(EnvironmentConfigWithReactionsPrecalc.class)
@AnyConfigLoadMode @ManualTest
@DisplayName("EXPLAB-2885. REACTIONS: мониторинг предрасчёта")
public class SplitterReactionsPrecalc2885MonitoringFlowTest extends ReactionsMonitoringSteps {
    @Test @Regression @DisplayName("EXPLAB-2885-T39/T40. Первая загрузка, замена и валидация: события и счётчики")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "MONITOR_FRESH")
    void t39FirstReplaceValidation(KafkaService kafka) {
        profile("MONITOR_FRESH");
        ObjectNode first = pre(101, preObject("U1", "gold"), preObject("U2", "silver"));
        ObjectNode second = pre(102, preObject("U1", "gold"), preObject("U3", "gold"));
        ObjectNode bad = pre(103).putNull("splittingObjects");
        Map<String, JsonNode> events = capture(kafka, List.of(first, second, bad), () -> scenario("Свежая таблица и два обновления", f -> {
            load(f, exp(101, 1)); // No pre-calculate reset: this scenario requires a fresh instance.
            calculate(f, first); calculate(f, second);
            error(rawCalculate(f, bad), "VALIDATION_FAILED");
        }));
        assertAll(
                () -> event(events, first, "LOADED_FIRST"),
                () -> event(events, second, "LOADED"),
                () -> event(events, bad, "VALIDATION_FAILED"),
                () -> eventCounters(events.get(second.path("requestId").asText()), 1, 1, 1, 0, 2, 1, 1));
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T39-empty. Событие загрузки до конфигурации")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "MONITOR_NO_CONFIG")
    void t39BeforeConfig(KafkaService kafka) {
        profile("MONITOR_NO_CONFIG"); ObjectNode p = pre(101, preObject("U1", "gold"));
        Map<String, JsonNode> events = capture(kafka, List.of(p), () -> scenario("Нет конфигурации сплиттования", f -> {
            error(rawSplit(f, request(S_B, obj("O1", null, "gold"))), "NO_SPLIT_CONFIG");
            calculate(f, p);
        }));
        event(events, p, "LOADED_EMPTY");
        assertEquals(1, events.get(p.path("requestId").asText()).path("objectsAdded").asInt(-1));
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T39-off. Событие отказа выключенного предрасчёта")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "MONITOR_OFF")
    void t39Disabled(KafkaService kafka) {
        profile("MONITOR_OFF"); ObjectNode p = pre(101, preObject("U1", "gold"));
        Map<String, JsonNode> events = capture(kafka, List.of(p), () -> scenario("Предрасчёт OFF", f ->
                error(rawCalculate(f, p), "PRECALC_NOT_ENABLED")));
        event(events, p, "REQUEST_REJECTED_PRECALC_NOT_ENABLED");
    }
}
