package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import steps.flow.splitter.reactions.ReactionsProfileSteps;

import config.environment.special.EnvironmentConfigWithReactionsPrecalc;

import com.fasterxml.jackson.databind.JsonNode;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.ManualTest;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicitly selected profiles are applied through the common managed-workload lifecycle. */
@SetEnvironmentConfiguration(EnvironmentConfigWithReactionsPrecalc.class)
@AnyConfigLoadMode @ManualTest
@DisplayName("EXPLAB-2885. REACTIONS: специальные профили стенда")
public class SplitterReactionsPrecalc2885ProfileFlowTest extends ReactionsProfileSteps {
    @Test @Regression @DisplayName("EXPLAB-2885-T01/T32-OFF. Предрасчёт выключен, REQUEST_PARAMS работает")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "OFF")
    void t01Off() {
        profile("OFF");
        scenario("Предрасчёт OFF, обычный split", f -> {
            load(f, exp(101, 1));
            error(rawCalculate(f, pre(101, preObject("U1", "gold"))), "PRECALC_NOT_ENABLED");
            assertMainA(splitOne(f, "U1", "gold"), 101); assertNoResult(splitOne(f, "U1", "silver"), "O1");
            ObjectNode e = exp(101, 1);
            ((ObjectNode)e.path("objectSelectConditions").get(0).path("rules").get(0).get(0)).put("paramSource", "REQUEST_PARAMS");
            load(f, e); ObjectNode r = request(S_B, obj("O1", "U1", "silver"));
            r.withArray("requestParams").add(param("segment", "gold")); assertMainA(split(f, r), 101);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T15. Предрасчёт до первой конфигурации")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "FRESH")
    void t15BeforeFirstConfig() {
        profile("FRESH");
        scenario("Свежий инстанс с отключённой автозагрузкой конфигурации", f -> {
            error(rawSplit(f, request(S_B, obj("O1", "U1", "gold"))), "NO_SPLIT_CONFIG");
            JsonNode p = calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver")));
            assertEquals(2, p.path("counter").path("totalObjects").asInt(-1));
            assertEquals(2, p.path("counter").path("objectsAdded").asInt(-1));
            load(f, exp(101, 1)); assertMainA(splitOne(f, "U1", "silver"), 101); assertNoResult(splitOne(f, "U2", "gold"), "O1");
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T02-no-table. Fallback до первого предрасчёта")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "NO_TABLE")
    void t02NoTable() {
        profile("NO_TABLE");
        scenario("Чистый процесс: загрузить только конфигурацию", f -> {
            load(f, exp(101, 1));
            assertMainA(splitOne(f, "U1", "gold"), 101);
            assertNoResult(splitOne(f, "U1", "silver"), "O1");
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T12/T13. Матрица настроек MAIN/ALL/пустых объектов")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "MATRIX")
    void t13ProfileMatrix() {
        profile("MATRIX");
        boolean allow = requiredFlag("EXPLAB_2885_ALLOW_WITHOUT_MAIN");
        boolean empty = requiredFlag("EXPLAB_2885_EMPTY_OBJECTS");
        boolean all = requiredFlag("EXPLAB_2885_ALL");
        scenario("Параметры применены через общий механизм ConfigMap", f -> {
            load(f, exp(101, 1)); calculate(f, pre(100));
            JsonNode live = splitOne(f, "U2", "silver");
            calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver")));
            JsonNode cached = splitOne(f, "U2", "gold"); assertEquivalent(live, cached); assertNoResult(cached, "O1");
            JsonNode o = object(cached, "O1");
            if (!allow && !empty) assertNull(o, cached.toString());
            else {
                assertNotNull(o, cached.toString());
                if (!allow) assertTrue(o.path("objectResults").isEmpty());
                else { assertNotNull(rule(o, "MAIN")); assertTrue(rule(o, "MAIN").path("resultExps").isEmpty()); }
            }
            JsonNode positive = splitOne(f, "U1", "silver"); assertMainA(positive, 101);
            if (all) assertNotNull(rule(object(positive, "O1"), "ALL"));
            else assertNull(rule(object(positive, "O1"), "ALL"));
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T38. После перезапуска пода кеш отсутствует и создаётся заново")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "RESTART")
    void t38Restart() {
        profile("RESTART");
        scenario("Создать кеш, перезапустить под через Fabric8 и проверить потерю кеша", f -> {
            load(f, exp(101, 1));
            calculate(f, pre(100));
            calculate(f, pre(101, preObject("U1", "gold")));
            assertMainA(splitOne(f, "U1", "silver"), 101);
            restartManagedStand();
            load(f, exp(101, 1));
            assertNoResult(splitOne(f, "U1", "silver"), "O1");
            assertMainA(splitOne(f, "U1", "gold"), 101);
            calculate(f, pre(102, preObject("U1", "gold")));
            assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T34-exploratory. Политика дубликатов требует согласования")
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "EXPLAB_2885_PROFILE", matches = "DUPLICATES")
    void t34DuplicateKeysEvidence() {
        profile("DUPLICATES");
        scenario("Собрать фактический ответ на конфликтующие ключи", f -> {
            seed(f);
            JsonNode response = body(rawCalculate(f,
                    pre(102, preObject("DUP", "gold"), preObject("DUP", "silver"))));
            assertTrue(response.isObject(), "Expected a structured response, acceptance policy remains unresolved");
            Allure.addAttachment("Duplicate keys: observed response", "application/json", response.toPrettyString(), ".json");
            JsonNode observed = body(rawSplit(f, request(S_B, obj("O1", "DUP", "silver"))));
            Allure.addAttachment("Duplicate keys: observed split", "application/json", observed.toPrettyString(), ".json");
            // Undefined acceptance criteria must never be reported as a passing functional test.
            assumeTrue(false, "Q3 unresolved: evidence captured; agree reject/first/last policy before acceptance");
        });
    }
}
