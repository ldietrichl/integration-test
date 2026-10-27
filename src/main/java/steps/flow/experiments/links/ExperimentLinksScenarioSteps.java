package steps.flow.experiments.links;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.services.core.ExperimentLinksSettings;
import fixtures.experiments.ExperimentLinksFixture;
import io.qameta.allure.Allure;
import steps.db.experiments.links.ExperimentLinksDbSteps;
import steps.kafka.experiments.ExperimentLinksKafkaSteps;
import steps.rest.experiments.links.ExperimentLinksRestSteps;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static util.splittercheck.experimentlinks.ExperimentLinksAssertions.*;

/** Два API и настоящие события; инфраструктура принадлежит Environment. */
public final class ExperimentLinksScenarioSteps implements AutoCloseable, flow.RestCustomFlow {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final int number;
    private final ExperimentLinksSettings settings = new ExperimentLinksSettings();
    private final ExperimentLinksProfile profile = new ExperimentLinksProfile(settings);
    private ExperimentLinksRestSteps rest;
    private ExperimentLinksDbSteps db;
    private ExperimentLinksFixture fixture;
    private ExperimentLinksKafkaSteps kafka;
    private long campaignId;
    private List<JsonNode> before = List.of();
    public ExperimentLinksScenarioSteps(int number) {
        if (!Set.of(1,9,18,60,61,22,23,26,27,28,29,30).contains(number))
            throw new IllegalArgumentException("Сценарий не входит в ядро EXPLAB-3109: " + number);
        this.number = number;
    }
    private boolean kafkaCase() { return Set.of(22,23,26,27,28,29,30).contains(number); }
    public void prepare() throws Exception {
        settings.requireIft(); settings.requireIsolated(); settings.route("direct");
        rest = restCustomSteps().experimentLinksSteps();
        db = new ExperimentLinksDbSteps(settings); db.requirePrimary();
        if (!db.queue().isEmpty())
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: перед подготовкой требуется пустая очередь и согласованная изоляция");
        // Эти проверки не создают объектов и не требуют Campaign/Kafka/Kubernetes bindings.
        if (number == 18 || number == 60) return;
        if (kafkaCase()) { settings.route("campaign"); settings.topic(); settings.kafkaProfile(); }
        List<Long> candidates = settings.campaignIds();
        boolean automatic = candidates.isEmpty();
        if (automatic) candidates = db.campaignCandidates();
        if (candidates.isEmpty())
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: в ограниченной выборке нет реального campaignId; укажите campaign.ids");
        campaignId = kafkaCase() ? ExperimentLinksFixture.selectWorkingCampaign(rest, candidates) : candidates.get(0);
        attach("Источник реальной кампании", Map.of("campaignId", campaignId,
                "source", automatic ? "latest-existing-database-links" : "configured-real-campaign-ids",
                "liveWorkingQuery", kafkaCase()));
        profile.prepare(kafkaCase());
        // Complete readiness/restarts and validate Kafka before creating any owned DB/HTTP fixtures.
        if (kafkaCase()) kafka = new ExperimentLinksKafkaSteps(settings);
        db.holdQueue();
        fixture = new ExperimentLinksFixture(settings, rest, db);
        fixture.create(campaignId);
        if (number == 61 || number == 23) fixture.create(campaignId);
        db.refreshBarrier(); db.clearFixtureTasks(); db.clearLinks();
        if (number == 27) db.seedEmptyLink(config(0), campaignId);
        before = db.links(); attach("Свои связи БД до задачи", before);
    }
    private long exp(int index) { return fixture.experiments.get(index); }
    private long config(int index) { return fixture.config(exp(index)); }
    private ObjectNode request(int... indices) {
        ObjectNode request = JSON.createObjectNode();
        var ids = request.putArray("expIds");
        for (int index : indices) ids.add(exp(index));
        request.putArray("cjIds").add(campaignId);
        return request;
    }
    private long enqueue(ObjectNode request) throws Exception {
        return Allure.step("Поставить ограниченную задачу и проверить параметры до обработки", () -> {
            db.refreshBarrier(); db.requireOwnedPending();
            Set<Long> existing = new HashSet<>(); db.pending().forEach(t -> existing.add(t.path("id").asLong()));
            long dbBefore = db.clockMillis();
            accepted(rest.update(request, "direct"));
            long dbAfter = db.clockMillis();
            var created = db.pending().stream().filter(t -> !existing.contains(t.path("id").asLong())).toList();
            assertEquals(1, created.size(), "POST должен создать ровно одну собственную задачу: " + created);
            JsonNode task = created.get(0);
            long id = task.path("id").asLong();
            if (kafka != null) kafka.watchTask(id);
            attach("Созданная задача БД", task);
            queue(task, request);
            // Independent database clock bounds; tolerate at most 30 seconds of application/DB skew.
            queueTime(task, dbBefore, dbAfter, Duration.ofSeconds(30));
            attach("Границы времени постановки", Map.of("dbBefore", dbBefore, "dbAfter", dbAfter,
                    "allowedClockSkewSeconds", 30));
            return id;
        });
    }
    private List<JsonNode> readTasks() throws Exception {
        return Allure.step("Получить текущую очередь через HTTP и сверить с БД", () -> {
            if (db.hasBarrier()) { db.refreshBarrier(); db.requireOwnedPending(); }
            List<JsonNode> expected = db.queue();
            attach("Снимок БД для GET очереди", expected);
            return tasks(rest.tasks("direct"), expected);
        });
    }
    public void test01() throws Exception { run(); }
    public void test09() throws Exception { run(); }
    public void test18() throws Exception { run(); }
    public void test60() throws Exception { run(); }
    public void test61() throws Exception { run(); }
    public void test22() throws Exception { run(); }
    public void test23() throws Exception { run(); }
    public void test26() throws Exception { run(); }
    public void test27() throws Exception { run(); }
    public void test28() throws Exception { run(); }
    public void test29() throws Exception { run(); }
    public void test30() throws Exception { run(); }
    private void run() throws Exception {
        if (number == 18) {
            Allure.step("POST без тела отклоняется до создания задачи", () -> {
                List<JsonNode> previous = db.queue();
                attach("Очередь до POST без тела", previous);
                missingBodyRejected(rest.updateWithoutBody("direct"), previous, db.queue());
            });
            return;
        }
        if (number == 60) {
            Allure.step("Пустая очередь доступна через GET", () -> {
                assertTrue(db.queue().isEmpty(), "Ожидаемая пустая очередь изменилась до GET");
                assertTrue(readTasks().isEmpty(), "GET должен вернуть [] для пустой очереди");
            });
            return;
        }
        if (number == 1 || number == 9 || number == 61) {
            ObjectNode first = request(0);
            long a = enqueue(first);
            if (number == 9) Allure.step("Последовательный повтор ожидающей задачи не создаёт дубль", () -> {
                db.refreshBarrier();
                List<JsonNode> previous = db.pending();
                attach("Свои задачи до повторного POST", previous);
                accepted(rest.update(first, "direct"));
                unchangedQueue(previous, db.pending());
            });
            Long b = number == 61 ? enqueue(request(1)) : null;
            if (b != null) assertNotEquals(a, b.longValue(), "Разные эксперименты должны создавать разные задачи");
            List<JsonNode> visible = readTasks();
            assertTrue(visible.stream().anyMatch(t -> t.path("id").asLong() == a), "GET потерял первую собственную задачу");
            if (b != null) assertTrue(visible.stream().anyMatch(t -> t.path("id").asLong() == b.longValue()), "GET потерял вторую собственную задачу");
            return;
        }
        if (number == 28) runUnchangedRepeat();
        else if (number == 29) runDeletion();
        else if (number == 30) runDistinctTasks();
        else runKafka();
    }
    private void runKafka() throws Exception {
        ObjectNode request = number == 23 ? request(0,1) : request(0);
        Set<Long> selected = number == 23 ? Set.of(config(0),config(1)) : Set.of(config(0));
        long task = enqueue(request);
        db.releaseQueue();
        waitForResult(task, selected);
        verifyKafkaResult(task, request, selected, before, number != 22, true);
        completedTasksAbsent(Set.of(task));
    }
    private record Result(List<JsonNode> after, List<JsonNode> expected) { }
    private Result verifyKafkaResult(long task, ObjectNode request, Set<Long> selected,
                                     List<JsonNode> previous, boolean journal, boolean nonempty) throws Exception {
        final List<JsonNode> actual = ownLinks(selected);
        final List<JsonNode> expected = new ArrayList<>();
        Allure.step("Проверить итог и фактическую доставку события в Kafka ExpLab", () -> {
            List<JsonNode> events = kafka.task(task);
            JsonNode total = scopedSummary(task, request, selected, events, false);
            routing(task);
            attach("Свои связи БД после задачи taskId=" + task, actual);
            if (journal) {
                List<JsonNode> completed = chunks(events).stream().distinct().filter(e ->
                        !time(e.path("startDt").asText()).isBefore(time(total.path("startDt").asText()))
                        && !time(e.path("endDt").asText()).isAfter(time(total.path("endDt").asText()))).toList();
                fixture.captureFromJournal(selected, completed);
                fixture.verifyStable(selected);
                Map<Long, List<ExperimentLinksFixture.Page>> source = new LinkedHashMap<>();
                selected.forEach(config -> source.put(config, fixture.pages.get(config)));
                actualJournal(task, total, selected, events, source);
                expected.addAll(fixture.expected(selected));
                if (nonempty && expected.isEmpty())
                    throw new IllegalStateException("BLOCKED_ENVIRONMENT: реальные фильтры собственной конфигурации не дали данных для проверки снимков/страниц");
                if (number == 26) createdSnapshots(completed, previous, actual, expected);
                else if (number == 27) {
                    if (expected.stream().noneMatch(n -> List.of("productCrmIds","channelIds","templateIds","commIds")
                            .stream().anyMatch(field -> !n.path(field).isEmpty())))
                        throw new IllegalStateException("BLOCKED_ENVIRONMENT: для updatedLinks нужен хотя бы один непустой реальный набор");
                    updatedSnapshots(completed, previous, actual, expected);
                } else completeFlushDelta(completed, previous, actual, expected);
            }
        });
        return new Result(actual, List.copyOf(expected));
    }
    private List<JsonNode> ownLinks(Set<Long> selected) {
        return db.links().stream().filter(row -> selected.contains(row.path("configId").longValue())).toList();
    }
    private void completedTasksAbsent(Set<Long> completed) throws Exception {
        // Only after both concurrent owned tasks finish: avoid comparing two moving queue snapshots.
        List<JsonNode> queue = db.queue();
        tasks(rest.tasks("direct"), queue);
        assertTrue(queue.stream().noneMatch(row -> completed.contains(row.path("id").longValue())),
                "Завершённая собственная задача осталась в текущей очереди");
    }
    private Result warmup() throws Exception {
        Set<Long> selected = Set.of(config(0));
        ObjectNode request = request(0);
        long task = enqueue(request);
        db.releaseQueue(); waitForResult(task, selected);
        Result result = verifyKafkaResult(task, request, selected, before, true, true);
        completedTasksAbsent(Set.of(task));
        db.holdForCleanup(); db.refreshBarrier(); db.requireOwnedPending();
        assertTrue(db.pending().isEmpty(), "Перед следующим этапом не должно оставаться задач");
        return result;
    }
    private void runUnchangedRepeat() throws Exception {
        Result first = warmup();
        Set<Long> selected = Set.of(config(0));
        fixture.captureSource(selected); fixture.verifyStable(selected);
        links(first.after(), fixture.expected(selected));
        List<JsonNode> previous = ownLinks(selected);
        ObjectNode request = request(0);
        // The completed task has left the queue; this POST must create a new task.
        Set<Long> earlier = kafka.observations().stream().map(r -> r.at("/payload/taskId").longValue())
                .collect(java.util.stream.Collectors.toSet());
        long task = enqueue(request);
        assertFalse(earlier.contains(task), "Повтор после завершения должен получить новый taskId");
        db.releaseQueue(); waitForResult(task, selected);
        Result result = verifyKafkaResult(task, request, selected, previous, true, true);
        assertEquals(linkIds(previous), linkIds(result.after()), "Неизменённые связи должны сохранять ID");
        links(previous, result.after());
        completedTasksAbsent(Set.of(task));
    }
    private void runDeletion() throws Exception {
        Result baseline = warmup();
        Set<Long> realIds = ExperimentLinksFixture.workingCommunicationIds(rest, campaignId);
        requireData(!realIds.isEmpty(), "Для удаления нужен непустой реальный WORKING id-list");
        fixture.create(campaignId, realIds);
        db.refreshBarrier(); db.clearFixtureTasks();
        Set<Long> selected = Set.of(config(1));
        List<JsonNode> empty = fixture.captureSource(selected);
        requireData(empty.isEmpty(), "Исключение реальных communication IDs не дало пустого результата Campaign");
        List<ExperimentLinksFixture.Page> pages = fixture.pages.get(config(1));
        requireData(pages.size() == 1
                && ExperimentLinksFixture.scope(pages.get(0).response(), config(1)).path("isLastPage").booleanValue()
                && ExperimentLinksFixture.scope(pages.get(0).response(), config(1)).path("cjs").isEmpty(),
                "Для удаления нужен HTTP 200 с собственным configScope, isLastPage=true и cjs=[]");
        fixture.verifyStable(selected);
        requireData(baseline.expected().size() == 1, "Для исходного снимка удаления нужна одна реальная связь выбранной кампании");
        db.seedLink(config(1), campaignId, baseline.expected().get(0));
        List<JsonNode> previous = ownLinks(selected);
        assertEquals(1, previous.size(), "Перед удалением должна существовать собственная связь");
        attach("Исходный снимок собственной удаляемой связи", previous);
        ObjectNode request = request(1);
        long task = enqueue(request);
        db.releaseQueue(); waitForResult(task, selected);
        Result result = verifyKafkaResult(task, request, selected, previous, true, false);
        assertTrue(result.after().isEmpty(), "Связь должна отсутствовать в БД после пустого результата источника");
        // completeFlushDelta above requires the full deletedLinks snapshot, including the old persisted ID.
        completedTasksAbsent(Set.of(task));
    }
    private void runDistinctTasks() throws Exception {
        warmup();
        List<Long> realIds = new ArrayList<>(ExperimentLinksFixture.workingCommunicationIds(rest, campaignId));
        requireData(realIds.size() >= 2, "Для двух различных результатов нужны как минимум две реальные WORKING-коммуникации");
        Set<Long> excludeA = new TreeSet<>(realIds), excludeB = new TreeSet<>(realIds);
        excludeA.remove(realIds.get(0)); excludeB.remove(realIds.get(1));
        fixture.create(campaignId, excludeA); fixture.create(campaignId, excludeB);
        db.refreshBarrier(); db.clearFixtureTasks();
        Set<Long> a = Set.of(config(1)), b = Set.of(config(2));
        List<JsonNode> left = fixture.captureSource(a), right = fixture.captureSource(b);
        requireData(left.size() == 1 && right.size() == 1
                        && !left.get(0).path("commIds").isEmpty() && !right.get(0).path("commIds").isEmpty()
                        && !content(left).values().iterator().next().equals(content(right).values().iterator().next()),
                "Два валидных фильтра не дали разных непустых независимых Campaign-наборов; нужна другая реальная кампания");
        fixture.verifyStable(Set.of(config(1), config(2)));
        List<JsonNode> beforeA = ownLinks(a), beforeB = ownLinks(b);
        assertTrue(beforeA.isEmpty() && beforeB.isEmpty(), "Оба новых конфига должны начинать без связей");
        ObjectNode requestA = request(1), requestB = request(2);
        assertNotEquals(exp(1), exp(2), "Нужны два разных собственных эксперимента");
        long taskA = enqueue(requestA), taskB = enqueue(requestB);
        assertNotEquals(taskA, taskB, "Разные expIds должны создавать две отдельные задачи");
        db.releaseQueue(); waitForResult(taskA, a); waitForResult(taskB, b);
        verifyKafkaResult(taskA, requestA, a, beforeA, true, true);
        verifyKafkaResult(taskB, requestB, b, beforeB, true, true);
        completedTasksAbsent(Set.of(taskA, taskB));
    }
    private static Map<String, Long> linkIds(List<JsonNode> rows) {
        Map<String, Long> result = new TreeMap<>();
        for (JsonNode row : rows) {
            assertTrue(row.path("id").isIntegralNumber() && row.path("id").canConvertToLong() && row.path("id").longValue() > 0,
                    "Сохранённая связь должна иметь положительный Long ID");
            assertNull(result.put(key(row), row.path("id").longValue()), "Повторная связь");
        }
        return result;
    }
    private static void requireData(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("BLOCKED_ENVIRONMENT: " + message);
    }
    private void waitForResult(long task, Set<Long> selected) {
        Allure.step("Дождаться итога только собственной задачи и необходимых событий", () -> {
            await().atMost(settings.timeout()).pollInterval(Duration.ofMillis(250)).until(() -> {
                kafka.poll(); db.requireOwnedPending();
                List<JsonNode> events = kafka.task(task);
                if (events.stream().anyMatch(e -> e.path("executionError").asBoolean())) return true;
                return db.pending().stream().noneMatch(t -> t.path("id").asLong() == task)
                        && events.stream().anyMatch(e -> e.has("executionError"))
                        && (number == 22 || chunks(events).stream().distinct().count() >= selected.size());
            });
            long until = System.nanoTime() + settings.quietWindow().toNanos();
            do { kafka.poll(); } while (System.nanoTime() < until);
        });
    }
    private void routing(long task) {
        var records = kafka.observations().stream().filter(r -> r.at("/payload/taskId").asLong() == task).toList();
        assertFalse(records.isEmpty(), "Нет прочитанного события собственной задачи");
        for (JsonNode record : records) {
            assertEquals(settings.topic(), record.path("topic").asText(), "Событие прочитано не из настроенного топика ExpLab");
            assertTrue(record.path("partition").asInt(-1) >= 0 && record.path("offset").asLong(-1) >= 0,
                    "Не сохранены реальные Kafka partition/offset");
        }
        attach("Доставка журнала ExpLab", records);
    }
    private static void attach(String name, Object data) {
        try { Allure.addAttachment(name, "application/json", JSON.writeValueAsString(data), ".json"); }
        catch (Exception e) { throw new IllegalStateException("Не удалось сохранить доказательства: " + name, e); }
    }
    @Override public void close() throws Exception {
        List<Throwable> errors = new ArrayList<>();
        try { profile.evidence(); } catch (Throwable e) { errors.add(e); }
        if (db != null && db.hasBarrier()) try {
            db.holdForCleanup(); db.refreshBarrier();
            await().atMost(settings.timeout()).pollInterval(Duration.ofMillis(250)).until(() -> !db.processingOwned());
            attach("Очередь перед очисткой своих объектов", db.pending());
            db.clearFixtureTasks();
            if (fixture != null) fixture.close();
            db.refreshBarrier(); db.clearFixtureTasks(); db.releaseQueue(); db.verifyCleanup();
        } catch (Throwable e) { errors.add(new IllegalStateException("Очистка или её постусловия не подтверждены; проверьте свои объекты и состояние барьера по диагностике", e)); }
        if (kafka != null) try { kafka.close(); } catch (Throwable e) { errors.add(e); }
        if (!errors.isEmpty()) {
            var error = new IllegalStateException("Не завершены диагностика или очистка EXPLAB-3109");
            errors.forEach(error::addSuppressed); throw error;
        }
    }
}
