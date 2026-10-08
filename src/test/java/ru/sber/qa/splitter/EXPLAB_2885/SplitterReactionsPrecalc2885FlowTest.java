package ru.sber.qa.splitter.EXPLAB_2885;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;
import steps.flow.splitter.reactions.ReactionsPrecalcSteps;

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
import ru.sber.qa.splitter.support.AnyConfigLoadMode;

import static org.junit.jupiter.api.Assertions.*;

@SetEnvironmentConfiguration(EnvironmentConfigWithReactionsPrecalc.class)
@AnyConfigLoadMode
@DisplayName("EXPLAB-2885. REACTIONS: предрасчёт и актуализация связей")
public class SplitterReactionsPrecalc2885FlowTest extends ReactionsPrecalcSteps {
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T02. Расчёт при пустой таблице")
    void t02EmptyTable() {
        scenario("Пустая таблица: обычный расчёт всех объектов", f -> {
            load(f, exp(101, 1)); calculate(f, pre(100));
            JsonNode r = split(f, request(S_B, obj("O1", "U1", "gold"), obj("O2", "U2", "silver"), obj("O3", "U404", "gold"), obj("O4", null, "gold")));
            for (String id : new String[]{"O1", "O3", "O4"}) assertMain(r, id, 101, "A", 10, "101-A-10");
            assertNoResult(r, "O2");
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T03. Успешный предрасчёт и split")
    void t03HappyPath() { scenario("C1 → P1 → split", f -> { seed(f); assertMainA(splitOne(f, "U1", "gold"), 101); }); }

    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T04. objectId берётся из текущего запроса")
    void t04ObjectIdentity() {
        scenario("Новый objectId, только запрошенный объект", f -> {
            seed(f); JsonNode r = split(f, request(S_B, obj("O99", "U1", "silver")));
            assertEquals(1, r.path("splittingResults").size()); assertMain(r, "O99", 101, "A", 10, "101-A-10");
        });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true}) @CriticalRegression
    @DisplayName("EXPLAB-2885-T05. Известный ключ использует предрасчёт при других/нерелевантных параметрах")
    void t05CachedParameters(boolean unrelated) {
        scenario("Доказательство использования кеша", f -> {
            seed(f); ObjectNode o = obj("O1", "U1", "silver");
            // SplittingObject.objectParams is @NotEmpty. Omit segment using a valid unrelated parameter.
            if (unrelated) o.putArray("objectParams").add(param("unrelated", "ignored"));
            assertMainA(split(f, request(S_B, o)), 101);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T06. Известный объект без связей не пересчитывается")
    void t06CachedMiss() { scenario("U2 без связей, runtime-параметры совпадают", f -> { seed(f); assertNoResult(splitOne(f, "U2", "gold"), "O1"); }); }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @CriticalRegression
    @DisplayName("EXPLAB-2885-T07. Неизвестный или отсутствующий ключ рассчитывается динамически")
    void t07Fallback(boolean absent) {
        scenario("Fallback не добавляет ключ в кеш", f -> {
            seed(f); String id = absent ? null : "U404";
            assertMainA(splitOne(f, id, "gold"), 101); assertNoResult(splitOne(f, id, "silver"), "O1");
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T08. Смешанный запрос: кеш, нет связей, fallback")
    void t08Mixed() {
        scenario("Четыре ветки одного запроса", f -> {
            seed(f); JsonNode r = split(f, request(S_B, obj("O1", "U1", "silver"), obj("O2", "U2", "gold"), obj("O3", "U404", "gold"), obj("O4", null, "gold")));
            for (String id : new String[]{"O1", "O3", "O4"}) assertMain(r, id, 101, "A", 10, "101-A-10");
            assertNoResult(r, "O2");
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T09. Два objectId с одним uniqueConfigurationId")
    void t09SharedKey() {
        scenario("Не схлопывать объекты по ключу кеша", f -> {
            seed(f); JsonNode r = split(f, request(S_B, obj("O1", "U1", "silver"), obj("O2", "U1", "silver")));
            assertEquals(2, r.path("splittingResults").size());
            for (String id : new String[]{"O1", "O2"}) assertMain(r, id, 101, "A", 10, "101-A-10");
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T10. Группа вычисляется на каждый splittingId: B/C/B")
    void t10PerRequestGroup() {
        scenario("Кеш хранит связи, а не сработавшую группу", f -> {
            load(f, grouped()); calculate(f, pre(101, preObject("U1", "gold")));
            for (String s : new String[]{S_B, S_C, S_B}) {
                String g = s.equals(S_B) ? "B" : "C";
                JsonNode r = split(f, request(s, obj("O1", "U1", "silver")));
                assertMain(r, "O1", 101, g, 10, "101-" + g + "-10");
                assertEquals(s.equals(S_B) ? 1928 : 6313, rule(object(r, "O1"), "MAIN").path("resultExps").get(0).path("spreadValue").asInt(-1));
            }
        });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true}) @Regression
    @DisplayName("EXPLAB-2885-T11. MAIN по приоритету слоя и id одинаков для live/cached")
    void t11Priority(boolean tie) {
        scenario("Проверка MAIN и эквивалентности", f -> {
            load(f, exp(101, 1), exp(202, tie ? 1 : 2)); calculate(f, pre(100));
            JsonNode live = splitOne(f, "U1", "gold");
            calculate(f, pre(101, preObject("U1", "gold")));
            JsonNode cached = splitOne(f, "U1", "silver");
            assertMainA(cached, tie ? 101 : 202); assertEquivalent(live, cached);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T12. MAIN/ALL и параметры сработавшей группы")
    void t12All() {
        scenario("Полный результат live/cached", f -> {
            load(f, grouped()); calculate(f, pre(100)); JsonNode live = splitOne(f, "U1", "gold");
            calculate(f, pre(101, preObject("U1", "gold"))); JsonNode cached = splitOne(f, "U1", "silver");
            assertMain(cached, "O1", 101, "B", 10, "101-B-10"); assertEquivalent(live, cached);
            JsonNode all = rule(object(cached, "O1"), "ALL"); assertNotNull(all, "Base profile requires ALL=true");
            assertEquals(1, all.path("resultExps").size());
            assertEquals("B", all.path("resultExps").get(0).path("expGroup").asText());
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T13. Пустой объект ровно один раз, live/cached эквивалентны")
    void t13EmptyResult() {
        scenario("Базовый профиль: пустой объект присутствует", f -> {
            load(f, exp(101, 1)); calculate(f, pre(100)); JsonNode live = splitOne(f, "U2", "silver");
            calculate(f, pre(101, preObject("U2", "silver"))); JsonNode cached = splitOne(f, "U2", "gold");
            assertEquivalent(live, cached); assertNotNull(object(cached, "O1"));
            assertTrue(object(cached, "O1").path("objectResults").isEmpty(), cached.toString());
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T14. Связь есть, но splittingId вне диапазона группы")
    void t14NoWorkedGroup() {
        scenario("Нет ложного MAIN", f -> {
            ObjectNode e = exp(101, 1); e.putArray("groups").add(group("A", 0, 1000, 10, "101-A-10"));
            load(f, e); calculate(f, pre(100)); JsonNode live = splitOne(f, "U1", "gold");
            calculate(f, pre(101, preObject("U1", "gold"))); JsonNode cached = splitOne(f, "U1", "silver");
            assertNoResult(cached, "O1"); assertEquivalent(live, cached);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T16. Полная замена: перенос, удаление, добавление")
    void t16Replace() {
        scenario("P1 → P2", f -> {
            seed(f); assertCounters(calculate(f, pre(102, preObject("U1", "gold"), preObject("U3", "gold"))), 1, 1, 1, 0, 2, 1, 1);
            assertMainA(splitOne(f, "U1", "silver"), 101); assertMainA(splitOne(f, "U3", "silver"), 101);
            assertMainA(splitOne(f, "U2", "gold"), 101); // removed negative cache entry must fallback
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T17. Повторный полный набор не дублирует связи")
    void t17Repeat() {
        scenario("Повтор P1", f -> {
            seed(f); assertCounters(calculate(f, pre(102, preObject("U1", "gold"), preObject("U2", "silver"))), 2, 0, 0, 1, 2, 1, 1);
            assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T18. Пустой полный набор очищает кеш")
    void t18ClearSet() {
        scenario("P1 → []", f -> {
            seed(f); assertCounters(calculate(f, pre(102)), 0, 0, 2, 0, 0, 0, 1);
            assertMainA(splitOne(f, "U2", "gold"), 101); assertNoResult(splitOne(f, "U1", "silver"), "O1");
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T19. Непересекающийся набор заменяет прежний")
    void t19Disjoint() {
        scenario("P1 → U3", f -> {
            seed(f); assertCounters(calculate(f, pre(102, preObject("U3", "gold"))), 0, 1, 2, 0, 1, 1, 1);
            assertNoResult(splitOne(f, "U1", "silver"), "O1"); assertMainA(splitOne(f, "U2", "gold"), 101); assertMainA(splitOne(f, "U3", "silver"), 101);
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T20. Существующий ключ переносится, новый рассчитывается")
    void t20ImmutableKey() {
        scenario("Изменение параметров под прежним и новым ключом", f -> {
            seed(f); calculate(f, pre(102, preObject("U1", "silver"))); assertMainA(splitOne(f, "U1", "silver"), 101);
            calculate(f, pre(103, preObject("U1v2", "silver"))); assertNoResult(splitOne(f, "U1v2", "gold"), "O1");
        });
    }
    @ParameterizedTest @ValueSource(booleans = {false, true}) @CriticalRegression
    @DisplayName("EXPLAB-2885-T21. Необязательная soConfigVersion отсутствует или null")
    void t21OptionalVersion(boolean explicitNull) {
        scenario("Split до и после предрасчёта без версии: сохранённые связи и динамический расчёт", f -> {
            load(f, exp(101, 1)); calculate(f, pre(100));
            assertMainA(splitOne(f, "U1", "gold"), 101);
            assertMainA(splitOne(f, "DYNAMIC", "gold"), 101);
            ObjectNode p = pre(null, preObject("U1", "gold"), preObject("U2", "silver")); if (explicitNull) p.putNull("soConfigVersion");
            JsonNode calculated = calculate(f, p);
            assertAll("Отдельно проверяем доступность split, fallback и использование предрасчёта; причина заранее не задана",
                    () -> assertMainA(splitOne(f, "U1", "gold"), 101),
                    () -> assertMainA(splitOne(f, "DYNAMIC", "gold"), 101),
                    () -> assertMainA(splitOne(f, null, "gold"), 101),
                    () -> assertCounters(calculated, 0, 2, 0, 1, 2, 1, 1),
                    () -> assertMainA(splitOne(f, "U1", "silver"), 101),
                    () -> assertNoResult(splitOne(f, "U2", "gold"), "O1"));
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T22. Возраст soConfigVersion не запрещает замену набора")
    void t22VersionOrder() {
        scenario("Одинаковая, меньшая, большая версия объектов", f -> {
            seed(f); int i = 0;
            for (int v : new int[]{101, 99, 102}) {
                String id = "NEXT" + i++; calculate(f, pre(v, preObject(id, "gold")));
                assertMainA(splitOne(f, id, "silver"), 101); assertNoResult(splitOne(f, "U1", "silver"), "O1");
            }
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T23. Предрасчёт при experiments=[] сохраняет объекты")
    void t23EmptyConfig() {
        scenario("Пустая config → P1 → C1", f -> {
            load(f); calculate(f, pre(100));
            assertCounters(calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver"))), 0, 2, 0, 2, 2, 0, 0);
            load(f, exp(101, 1)); assertMainA(splitOne(f, "U1", "silver"), 101); assertNoResult(splitOne(f, "U2", "gold"), "O1");
        });
    }
    @Test @Regression @DisplayName("EXPLAB-2885-T24. conditionId, параметры и уникальные счётчики связей")
    void t24Conditions() {
        scenario("Два условия одной группы, минимальный conditionId", f -> {
            ObjectNode e = exp(101, 1); e.withArray("objectSelectConditions").add(condition(20, "gold"));
            ((ObjectNode)e.path("groups").get(0)).withArray("splittingResults").addObject().put("conditionId", 20)
                    .putArray("resultParams").add(param("marker", "WRONG-20"));
            load(f, e); calculate(f, pre(100)); JsonNode live = splitOne(f, "U1", "gold");
            assertCounters(calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver"), preObject("U3", "gold"))), 0, 3, 0, 1, 3, 1, 1);
            JsonNode cached = splitOne(f, "U1", "silver"); assertMainA(cached, 101); assertEquivalent(live, cached);
        });
    }
    @ParameterizedTest(name = "equalLayerPriority={0}") @ValueSource(booleans = {true, false}) @CriticalRegression
    @DisplayName("EXPLAB-2885-T25. Новые связи и MAIN по настроенному правилу приоритета")
    void t25Add(boolean equalLayerPriority) {
        scenario("C1 → P1 → C2: max-layer-priority=true, max-id=false", f -> {
            seed(f); assertMainA(splitOne(f, "U1", "silver"), 101);
            load(f, exp(101, 1), exp(202, equalLayerPriority ? 1 : 2));
            JsonNode live = splitOne(f, "DYNAMIC", "gold");
            JsonNode linked = splitOne(f, "U1", "silver");
            int expectedMain = equalLayerPriority ? 101 : 202;
            assertAll("Наличие новых связей проверяется независимо от выбора MAIN",
                    () -> assertAllIds(linked, 101, 202),
                    () -> assertMainA(live, expectedMain),
                    () -> assertMainA(linked, expectedMain),
                    () -> assertEquivalent(live, linked));
        });
    }

    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T26. Удаление эксперимента обновляет связи")
    void t26Remove() {
        scenario("C2 → P1 → только E101", f -> {
            load(f, exp(101, 1), exp(202, 2)); calculate(f, pre(101, preObject("U1", "gold")));
            assertMainA(splitOne(f, "U1", "silver"), 202); load(f, exp(101, 1));
            JsonNode r = splitOne(f, "U1", "silver"); assertMainA(r, 101); assertAllIds(r, 101);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T27. Одновременное удаление и добавление эксперимента")
    void t27ReplaceExperiment() {
        scenario("E101 → идентичный E202: изменён только id эксперимента", f -> {
            seed(f); assertMainA(splitOne(f, "U1", "silver"), 101);
            load(f, exp(101, 1).put("id", 202));
            JsonNode live = splitOne(f, "DYNAMIC", "gold");
            JsonNode cached = splitOne(f, "U1", "silver");
            assertAll("Новый эксперимент подходит динамически и связан с сохранённым объектом",
                    () -> assertMain(live, "O1", 202, "A", 10, "101-A-10", 101),
                    () -> assertMain(cached, "O1", 202, "A", 10, "101-A-10", 101),
                    () -> assertAllIds(cached, 202),
                    () -> assertEquivalent(live, cached));
        });
    }

    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T28. Серия обновлений без повторного предрасчёта")
    void t28Sequence() {
        scenario("C1 → P1 → C2 → C3 → C4", f -> {
            seed(f); load(f, exp(101, 1), exp(202, 1)); JsonNode linked = splitOne(f, "U1", "silver");
            assertAll("Равные приоритеты: MAIN=101, но связь с 202 тоже должна появиться",
                    () -> assertAllIds(linked, 101, 202), () -> assertMainA(linked, 101),
                    () -> assertEquivalent(splitOne(f, "DYNAMIC", "gold"), linked));
            load(f, exp(202, 2)); assertMainA(splitOne(f, "U1", "silver"), 202);
            load(f, exp(101, 1)); assertMainA(splitOne(f, "U1", "silver"), 101);
        });
    }
    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T29. Остановка всех экспериментов и возврат")
    void t29StopAll() {
        scenario("C1 → [] → C1: связи пересчитываются без повторного предрасчёта", f -> {
            seed(f); assertMainA(splitOne(f, "U1", "silver"), 101);
            load(f); assertNoResult(splitOne(f, "U1", "silver"), "O1");
            load(f, exp(101, 1));
            JsonNode live = splitOne(f, "DYNAMIC", "gold");
            JsonNode cached = splitOne(f, "U1", "silver");
            assertAll("После возврата конфигурации работают динамический расчёт и сохранённые связи",
                    () -> assertMainA(live, 101), () -> assertMainA(cached, 101),
                    () -> assertEquivalent(live, cached));
        });
    }

    @Test @Regression @DisplayName("EXPLAB-2885-T30. Новая configVersion с неизменным набором")
    void t30SameExperiments() { scenario("Неизменные эксперименты", f -> { seed(f); JsonNode before = splitOne(f, "U1", "silver"); load(f, exp(101, 1)); assertEquivalent(before, splitOne(f, "U1", "silver")); }); }

    @Test @CriticalRegression @DisplayName("EXPLAB-2885-T40. Счётчики первой непустой загрузки и замены")
    void t40Counters() {
        scenario("Счётчики SDK отражают набор объектов", f -> {
            load(f, exp(101, 1)); calculate(f, pre(100));
            assertCounters(calculate(f, pre(101, preObject("U1", "gold"), preObject("U2", "silver"))), 0, 2, 0, 1, 2, 1, 1);
            assertCounters(calculate(f, pre(102, preObject("U1", "gold"), preObject("U3", "gold"))), 1, 1, 1, 0, 2, 1, 1);
        });
    }
}
