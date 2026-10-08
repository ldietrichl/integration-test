package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import steps.flow.splitter.reactions.ReactionsProfileSteps;
import steps.flow.splitter.reactions.ReactionsPrecalcSteps;
import static util.concurrent.ScenarioConcurrency.*;

import config.environment.special.EnvironmentConfigWithReactionsPrecalc;

import com.fasterxml.jackson.databind.JsonNode;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;
import util.support.SplitterVersionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SetEnvironmentConfiguration(EnvironmentConfigWithReactionsPrecalc.class)
@AnyConfigLoadMode
@DisplayName("EXPLAB-2885. REACTIONS: контракт и изоляция запросов")
public class SplitterReactionsPrecalc2885ContractFlowTest extends ReactionsPrecalcSteps {
    @Test @Regression @DisplayName("EXPLAB-2885-T31. Отказ config не меняет применённую таблицу")
    void t31RejectedConfig() {
        scenario("Старая и невалидная конфигурация", f -> {
            seed(f); long active = version;
            ObjectNode old = config(active - 1, exp(202, 2)).put("forceConfigLoad", false);
            JsonNode rejected = ok(rawLoad(f, old));
            assertEquals("OLD_VERSION", rejected.path("result").asText());
            assertEquals(active, rejected.path("currentConfigVersion").asLong(-1));
            assertMainA(splitOne(f, "U1", "silver"), 101);
            ObjectNode invalid = config(SplitterVersionProvider.next(), exp(202, 2));
            ((ObjectNode)invalid.path("splittingConfig").path("experiments").get(0)).putNull("salt");
            var invalidResponse = rawLoad(f, invalid);
            int status = invalidResponse.toResponse().statusCode();
            assertTrue(status == 200 || status == 400, "Expected config rejection, got HTTP " + status);
            if (status == 200) {
                rejected = body(invalidResponse);
                assertEquals("CONFIG_ERROR", rejected.path("result").asText());
                assertEquals(active, rejected.path("currentConfigVersion").asLong(-1));
            }
            assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T32. REQUEST_PARAMS запрещён при предрасчёте")
    void t32RequestParamsRejected() {
        scenario("Неподдерживаемый источник параметров", f -> {
            seed(f); ObjectNode e = exp(202, 2);
            ((ObjectNode)e.path("objectSelectConditions").get(0).path("rules").get(0).get(0)).put("paramSource", "REQUEST_PARAMS");
            JsonNode rejected = ok(rawLoad(f, config(SplitterVersionProvider.next(), e)));
            assertEquals("REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED", rejected.path("result").asText());
            assertEquals(version, rejected.path("currentConfigVersion").asLong(-1));
            assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @ParameterizedTest @Regression
    @ValueSource(strings = {"requestId:null", "requestId:empty", "requestId:missing", "splittingObjects:null", "splittingObjects:missing", "uniqueConfigurationId:null", "uniqueConfigurationId:empty", "uniqueConfigurationId:missing", "objectParams:null", "objectParams:missing", "paramCode:null", "paramCode:empty", "paramCode:missing", "paramValues:null", "paramValues:missing", "dataType:null", "dataType:missing"})
    @DisplayName("EXPLAB-2885-T33. Валидация предрасчёта сохраняет прежний набор")
    void t33Validation(String mutation) {
        scenario("Невалидный запрос: " + mutation, f -> {
            seed(f); ObjectNode p = pre(102, preObject("U3", "gold"));
            String[] parts = mutation.split(":"); String field = parts[0];
            ObjectNode owner = switch (field) {
                case "requestId", "splittingObjects" -> p;
                case "uniqueConfigurationId", "objectParams" -> (ObjectNode)p.path("splittingObjects").get(0);
                default -> (ObjectNode)p.path("splittingObjects").get(0).path("objectParams").get(0);
            };
            switch (parts[1]) { case "null" -> owner.putNull(field); case "empty" -> owner.put(field, ""); default -> owner.remove(field); }
            validationRejected(rawCalculate(f, p));
            assertMainA(splitOne(f, "U1", "silver"), 101); assertNoResult(splitOne(f, "U2", "gold"), "O1");
            assertNoResult(splitOne(f, "U3", "silver"), "O1");
            // Probe preservation by repeating the old complete set: both must be copied.
            assertCounters(calculate(f, pre(103, preObject("U1", "gold"), preObject("U2", "silver"))), 2, 0, 0, 1, 2, 1, 1);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T34. Пустой objectParams отклоняется по спецификации")
    void t34EmptyParameters() {
        scenario("PDF запрещает пустые objectParams", f -> {
            seed(f); ObjectNode o = preObject("U3", "gold"); o.putArray("objectParams");
            validationRejected(rawCalculate(f, pre(102, o)));
            assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T35. У каждого ответа уникальный responseId при повторном requestId")
    void t35ResponseId() {
        scenario("Два ответа на повторный requestId имеют разные responseId", f -> {
            seed(f); ObjectNode p = pre(102, preObject("U1", "gold"));
            JsonNode first = calculate(f, p); JsonNode second = calculate(f, p.deepCopy());
            util.splittercheck.PrecalcResponseAssertions.uuid(first.path("responseId"));
            util.splittercheck.PrecalcResponseAssertions.uuid(second.path("responseId"));
            assertNotEquals(first.path("responseId"), second.path("responseId"));
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T36. Параллельные B/C не загрязняют кеш")
    void t36ConcurrentRequests() {
        scenario("32 запроса, 4 потока, отдельный DTO на каждый вызов", f -> {
            load(f, grouped()); calculate(f, pre(101, preObject("U1", "gold")));
            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < 32; i++) {
                    final int n = i;
                    futures.add(pool.submit(() -> {
                        String g = n % 2 == 0 ? "B" : "C";
                        JsonNode r = split(f, request(n % 2 == 0 ? S_B : S_C, obj("O" + n, "U1", "silver")));
                        assertMain(r, "O" + n, 101, g, 10, "101-" + g + "-10");
                    }));
                }
                await(futures);
                assertMain(splitOne(f, "U1", "silver"), "O1", 101, "B", 10, "101-B-10");
            } finally { pool.shutdownNow(); }
        });
    }
    @Test @Regression @ManualTest
    @DisplayName("EXPLAB-2885-T37. Исследование согласованности при конкурентных обновлениях")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "CONCURRENT_UPDATES")
    void t37ConcurrentUpdate() {
        // The supplied contract does not guarantee concurrent refresh/calculate or atomic publication.
        // Keep this diagnostic probe explicit; its failure alone is not a confirmed contract violation.
        ReactionsProfileSteps.profile("CONCURRENT_UPDATES");
        scenario("Параллельно split и замена конфигурации/таблицы", f -> {
            seed(f); long old = version; long next = SplitterVersionProvider.next();
            ObjectNode newConfig = config(next, exp(202, 2));
            ExecutorService pool = Executors.newFixedThreadPool(3);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<?> reader = pool.submit(() -> {
                    waitForStart(start);
                    for (int i = 0; i < 40; i++) {
                        ObjectNode req = request(S_B, obj("O1", "U1", "silver"));
                        JsonNode r = ok(rawSplit(f, req));
                        assertEquals(req.path("requestId"), r.path("requestId")); assertUnique(r);
                        long v = r.path("splittingConfigVersion").asLong(-1);
                        assertTrue(v == old || v == next, "Unknown snapshot: " + r);
                        assertMainA(r, v == old ? 101 : 202);
                    }
                });
                Future<?> writer = pool.submit(() -> {
                    waitForStart(start);
                    JsonNode r = ok(rawLoad(f, newConfig));
                    assertTrue(java.util.Set.of("LOADED", "LOADED_WITH_PRECALC").contains(r.path("result").asText()), r.toString());
                });
                Future<?> precalc = pool.submit(() -> { waitForStart(start); calculate(f, pre(102, preObject("U1", "gold"), preObject("U3", "gold"))); });
                start.countDown(); await(List.of(reader, writer, precalc)); version = next;
                assertMainA(splitOne(f, "U1", "silver"), 202); assertMainA(splitOne(f, "U3", "silver"), 202);
            } finally { pool.shutdownNow(); }
        });
    }
}
