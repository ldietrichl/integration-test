package util.splittercheck.experimentlinks;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fixtures.experiments.ExperimentLinksFixture.Page;
import steps.rest.experiments.links.ExperimentLinksRestSteps.Reply;
import steps.db.experiments.links.ExperimentLinksDbSteps;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Проверки независимого источника, снимков БД и настоящих событий Kafka. */
public final class ExperimentLinksAssertions {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIELDS = List.of("productCrmIds", "channelIds", "templateIds", "commIds");
    private ExperimentLinksAssertions() { }
    public static void accepted(Reply response) {
        assertEquals(200, response.status(), "Ручной endpoint должен принять задачу; ответ=" + response.text());
        assertTrue(response.text().isBlank(), "У принятой ручной задачи ожидалось пустое тело; фактически=" + response.text());
    }
    /** The GET returns the current table, including a separately owned queue barrier. */
    public static List<JsonNode> tasks(Reply response, List<JsonNode> database) {
        assertEquals(200, response.status(), "GET текущих задач должен вернуть HTTP 200");
        assertTrue(response.json().isArray(), "GET текущих задач должен вернуть JSON-массив");
        Map<Long, JsonNode> rows = queueById(database);
        List<JsonNode> result = new ArrayList<>();
        long previous = 0;
        for (JsonNode task : response.json()) {
            assertTrue(task.isObject(), "Элемент списка задач должен быть объектом");
            long id = positiveLong(task.path("id"), "id задачи");
            assertTrue(id > previous, "Задачи должны следовать строго по id ASC"); previous = id;
            JsonNode row = rows.get(id);
            assertNotNull(row, "GET вернул отсутствующую в снимке БД задачу id=" + id);
            assertEquals(positiveLong(row.path("request_dt"), "request_dt БД"),
                    positiveLong(task.path("requestDt"), "requestDt epoch millis"), "Отличается requestDt задачи " + id);
            for (String[] field : new String[][]{{"cjIds","cj_ids"},{"expIds","exp_ids"}})
                nullableLongArray(row.path(field[1]), task.path(field[0]), field[0]);
            for (String[] field : new String[][]{{"readFlag","read_flag"},{"isInterrupted","is_interrupted"},
                    {"isProcessing","is_processing"},{"isMarkedToStop","is_marked_to_stop"}})
                nullableScalar(row.path(field[1]), task.path(field[0]), field[0], "boolean");
            for (String[] field : new String[][]{{"attemptsCount","attempts_count"},{"recreatesCount","recreates_count"}})
                nullableScalar(row.path(field[1]), task.path(field[0]), field[0], "integer");
            nullableState(row.path("current_state"), task.path("currentState"));
            nullableInstant(row.path("start_processing_dt"), task.path("startProcessingDt"), "startProcessingDt");
            nullableInstant(row.path("marked_to_stop_dt"), task.path("markedToStopDt"), "markedToStopDt");
            result.add(task.deepCopy());
        }
        assertEquals(rows.size(), result.size(), "GET должен вернуть все текущие задачи из снимка БД");
        return List.copyOf(result);
    }
    public static void unchangedQueue(List<JsonNode> before, List<JsonNode> after) {
        assertEquals(queueById(before), queueById(after), "Запрос не должен изменять текущую очередь");
    }
    public static void missingBodyRejected(Reply response, List<JsonNode> before, List<JsonNode> after) {
        assertEquals(400, response.status(), "POST без обязательного тела должен вернуть HTTP 400");
        unchangedQueue(before, after);
    }
    private static Map<Long, JsonNode> queueById(List<JsonNode> rows) {
        Map<Long, JsonNode> result = new TreeMap<>();
        for (JsonNode row : rows) {
            assertTrue(row.isObject(), "Снимок задачи БД должен быть объектом");
            long id = positiveLong(row.path("id"), "id задачи БД");
            assertNull(result.put(id, row), "В снимке БД повторяется id задачи " + id);
        }
        return result;
    }
    public static long positiveLong(JsonNode value, String field) {
        assertTrue(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0,
                "Ожидалось положительное Long: " + field);
        return value.longValue();
    }
    private static boolean absent(JsonNode value) { return value.isMissingNode() || value.isNull(); }
    private static void nullableLongArray(JsonNode expected, JsonNode actual, String field) {
        if (absent(expected)) { assertTrue(absent(actual), "Ожидался null: " + field); return; }
        assertTrue(expected.isArray() && actual.isArray(), "Ожидался массив Long: " + field);
        List<Long> left = new ArrayList<>(), right = new ArrayList<>();
        for (JsonNode value : expected) { assertTrue(value.isIntegralNumber() && value.canConvertToLong(), field); left.add(value.longValue()); }
        for (JsonNode value : actual) { assertTrue(value.isIntegralNumber() && value.canConvertToLong(), field); right.add(value.longValue()); }
        Collections.sort(left); Collections.sort(right);
        assertEquals(left, right, "Массив GET отличается от БД: " + field);
    }
    private static void nullableScalar(JsonNode expected, JsonNode actual, String field, String type) {
        if (absent(expected)) { assertTrue(absent(actual), "Ожидался null: " + field); return; }
        if (type.equals("boolean")) assertTrue(actual.isBoolean(), "Ожидался boolean: " + field);
        else if (type.equals("integer")) assertTrue(actual.isIntegralNumber() && actual.canConvertToInt(), "Ожидался Integer: " + field);
        else assertTrue(actual.isTextual(), "Ожидалась строка: " + field);
        assertEquals(expected, actual, "Поле GET отличается от БД: " + field);
    }
    private static void nullableState(JsonNode expected, JsonNode actual) {
        if (absent(expected)) { assertTrue(absent(actual), "Ожидался null currentState"); return; }
        assertTrue(expected.isObject() && actual.isObject(), "currentState должен быть объектом");
        for (String field : List.of("currentConfigChunkStartIndex", "configChunkSize"))
            nullableScalar(expected.path(field), actual.path(field), "currentState." + field, "integer");
        nullableScalar(expected.path("failureReason"), actual.path("failureReason"), "currentState.failureReason", "string");
    }
    private static void nullableInstant(JsonNode expected, JsonNode actual, String field) {
        if (absent(expected)) { assertTrue(absent(actual), "Ожидался null: " + field); return; }
        assertTrue(expected.isTextual(), "Ожидался timestamp БД без часового пояса: " + field);
        assertTrue(actual.isIntegralNumber() && actual.canConvertToLong(), "Ожидались целые epoch milliseconds: " + field);
        // Service Hibernate stores UTC wall time in TIMESTAMP; its Jackson Instant serializer emits milliseconds.
        long milliseconds = assertDoesNotThrow(() -> LocalDateTime.parse(expected.asText())
                .toInstant(ZoneOffset.UTC).toEpochMilli(), "Некорректный UTC timestamp БД: " + field);
        assertEquals(milliseconds, actual.longValue(), "Отличается момент времени с точностью миллисекунд: " + field);
    }
    public static ObjectNode link(long config, JsonNode campaign) {
        assertTrue(config > 0, "Ожидался положительный configId");
        ObjectNode link = JSON.createObjectNode().put("configId", config).put("cjId", positiveLong(campaign.path("id"), "id кампании"));
        for (String field : FIELDS) link.set(field, campaign.path(field.equals("commIds") ? "configCommIds" : field).deepCopy());
        return link;
    }
    public static String key(JsonNode link) {
        assertTrue(link.isObject(), "Ожидался объект связи");
        return positiveLong(link.path("configId"), "configId связи") + ":" + positiveLong(link.path("cjId"), "cjId связи");
    }
    public static Map<String, Map<String, Set<String>>> content(Collection<JsonNode> links) {
        Map<String, Map<String, Set<String>>> result = new TreeMap<>();
        for (JsonNode link : links) {
            Map<String, Set<String>> fields = new TreeMap<>();
            for (String field : FIELDS) {
                assertTrue(link.path(field).isArray(), "В снимке нет массива " + field + ": " + link);
                Set<String> values = new TreeSet<>();
                for (JsonNode value : link.path(field))
                    assertTrue(values.add(setElement(value, field.equals("productCrmIds"), field).toString()), "Повторный элемент множества " + field);
                fields.put(field, values);
            }
            assertNull(result.put(key(link), fields), "Повторная связь config/campaign: " + key(link));
        }
        return result;
    }
    public static void links(Collection<JsonNode> expected, Collection<JsonNode> actual) {
        assertEquals(content(expected), content(actual), "Связи БД должны совпадать с независимыми данными campaign-service");
    }
    public static void queue(JsonNode task, JsonNode request) {
        positiveLong(task.path("id"), "id задачи");
        positiveLong(task.path("request_dt"), "request_dt задачи");
        for (String field : List.of("expIds", "cjIds")) {
            JsonNode actual = task.path(field.equals("expIds") ? "exp_ids" : "cj_ids");
            JsonNode wanted = request.path(field);
            if (wanted.isMissingNode() || wanted.isNull()) assertTrue(actual.isNull(), "Ожидался SQL NULL для " + field + "; фактически=" + actual);
            else assertEquals(ExperimentLinksDbSteps.longs(wanted), ExperimentLinksDbSteps.longs(actual), "Параметр задачи отличается от запроса: " + field);
        }
        for (String flag : List.of("is_processing", "is_interrupted", "is_marked_to_stop")) {
            assertTrue(task.path(flag).isBoolean(), "Отсутствует флаг " + flag);
            assertFalse(task.path(flag).asBoolean(), "Новая задача не должна иметь флаг " + flag);
        }
        for (String counter : List.of("attempts_count", "recreates_count")) {
            assertTrue(task.path(counter).isIntegralNumber() && task.path(counter).canConvertToInt(), "Отсутствует Integer счётчик " + counter);
            assertEquals(0, task.path(counter).asInt(), "Начальный счётчик должен быть 0: " + counter);
        }
    }
    /** Bound request_dt by independently measured database clock samples and an explicit clock allowance. */
    public static void queueTime(JsonNode task, long dbBefore, long dbAfter, Duration allowedClockSkew) {
        assertTrue(dbBefore > 0 && dbAfter >= dbBefore, "Некорректный интервал времени БД");
        assertNotNull(allowedClockSkew, "Не задан допуск рассогласования часов");
        assertFalse(allowedClockSkew.isNegative(), "Допуск часов не должен быть отрицательным");
        long allowance = assertDoesNotThrow(allowedClockSkew::toMillis, "Переполнение допуска часов");
        long first = assertDoesNotThrow(() -> Math.subtractExact(dbBefore, allowance), "Переполнение нижней границы времени");
        long last = assertDoesNotThrow(() -> Math.addExact(dbAfter, allowance), "Переполнение верхней границы времени");
        long requested = positiveLong(task.path("request_dt"), "request_dt epoch millis");
        assertTrue(requested >= first && requested <= last, "request_dt вне независимого интервала времени БД с допуском");
    }
    /** Identical payload deliveries are evidence of transport, not separate processing attempts. */
    public static JsonNode scopedSummary(long task, JsonNode request, Set<Long> configs, List<JsonNode> events, boolean error) {
        assertTrue(!ExperimentLinksDbSteps.longs(request.path("expIds")).isEmpty()
                || !ExperimentLinksDbSteps.longs(request.path("cjIds")).isEmpty(), "Нужна ограниченная область задачи");
        for (JsonNode event : events) {
            assertEquals(task, positiveLong(event.path("taskId"), "taskId события"), "Чужая задача в журнале");
            assertTrue(event.has("executionError") || event.has("configIds"), "Неизвестный тип события задачи");
            if (event.has("configIds")) chunkConfig(event, configs);
        }
        List<JsonNode> totals = events.stream().distinct().filter(e -> e.has("executionError")).toList();
        assertFalse(totals.isEmpty(), "Не получено итоговое событие taskId=" + task);
        for (JsonNode total : totals) {
            assertEquals(task, positiveLong(total.path("taskId"), "taskId итога"), "Итог относится к другой задаче");
            assertTrue(total.path("executionError").isBoolean(), "Нет boolean executionError");
            assertEquals(error, total.path("executionError").booleanValue(), "Признак ошибки не соответствует исходу обработки");
            assertTrue(total.path("allConfigsIds").isArray(), "Нет массива allConfigsIds");
            assertEquals(configs, ExperimentLinksDbSteps.longs(total.path("allConfigsIds")), "Неверная область конфигов итога");
            assertEquals(configs.size(), total.path("allConfigsIds").size(), "Повторный configId в итоге");
            for (String field : List.of("expIds", "cjIds"))
                nullableLongArray(request.path(field), total.path(field), field);
            times(total);
        }
        return totals.stream().max(Comparator.comparing(e -> time(e.path("endDt").asText()))).orElseThrow();
    }
    /** Compare the recorded pages with independently replayed Campaign requests and responses. */
    public static void actualJournal(long task, JsonNode total, Set<Long> configs, List<JsonNode> events,
                                     Map<Long, List<Page>> source) {
        assertEquals(configs, source.keySet(), "Независимые страницы должны покрывать область конфигов задачи");
        var start = time(total.path("startDt").asText());
        var end = time(total.path("endDt").asText());
        List<JsonNode> selected = new ArrayList<>();
        for (JsonNode event : events.stream().distinct().toList()) {
            assertEquals(task, positiveLong(event.path("taskId"), "taskId события"), "Чужая задача в журнале");
            assertTrue(event.has("executionError") || event.has("configIds"), "Неизвестный тип события задачи");
            if (!event.has("configIds")) continue;
            chunkConfig(event, configs);
            times(event);
            if (!time(event.path("startDt").asText()).isBefore(start) && !time(event.path("endDt").asText()).isAfter(end))
                selected.add(event);
        }
        assertEquals(configs.size(), selected.size(), "Для завершённой попытки при чанке 1 нужны все журналы конфигов");
        Set<Long> seen = new TreeSet<>();
        for (JsonNode chunk : selected) {
            assertEquals(total.path("hostname"), chunk.path("hostname"), "Итог и чанк одной попытки имеют разных обработчиков");
            assertTrue(chunk.path("configIds").isArray(), "Нет массива configIds");
            assertEquals(1, chunk.path("configIds").size(), "Чанк должен содержать одну конфигурацию");
            long config = positiveLong(chunk.path("configIds").get(0), "configId чанка");
            assertTrue(configs.contains(config) && seen.add(config), "Чужой или повторный конфиг чанка " + config);
            JsonNode pages = chunk.path("requestResponsePages");
            assertTrue(pages.isArray() && !pages.isEmpty(), "Не получены реальные страницы чанка " + config);
            List<Page> originals = source.get(config);
            assertEquals(originals.size(), pages.size(), "Журнал не покрывает независимо прочитанные страницы " + config);
            for (int i = 0; i < pages.size(); i++) {
                Page original = originals.get(i);
                assertEquals(200, original.status(), "Независимый Campaign-запрос завершился ошибкой");
                assertEquals(normalRequest(original.request()), normalRequest(pages.get(i).path("request")),
                        "Искажён существенный параметр Campaign-запроса: config=" + config + ", page=" + i);
                campaignResponses(original.response(), pages.get(i).path("response"));
            }
        }
        assertEquals(configs, seen, "Не все конфиги задачи получили журнал");
    }
    public static void createdSnapshots(List<JsonNode> chunks, List<JsonNode> before, List<JsonNode> after,
                                        Collection<JsonNode> expected) {
        var previous = content(before); var current = content(after);
        Set<String> createdKeys = new TreeSet<>(current.keySet()); createdKeys.removeAll(previous.keySet());
        assertFalse(createdKeys.isEmpty(), "Проверка требует реально созданную собственную связь");
        completeFlushDelta(chunks, before, after, expected);
    }
    public static void updatedSnapshots(List<JsonNode> chunks, List<JsonNode> before, List<JsonNode> after,
                                        Collection<JsonNode> expected) {
        var previous = content(before); var current = content(after);
        Set<String> updatedKeys = new TreeSet<>(); previous.forEach((key, value) -> {
            if (current.containsKey(key) && !value.equals(current.get(key))) updatedKeys.add(key);
        });
        assertFalse(updatedKeys.isEmpty(), "Проверка требует хотя бы один изменённый реальный набор связи");
        completeFlushDelta(chunks, before, after, expected);
    }
    /** Compare all reported actions with the complete independently observed database delta, including no-op. */
    public static void completeFlushDelta(List<JsonNode> chunks, List<JsonNode> before, List<JsonNode> after,
                                         Collection<JsonNode> expected) {
        links(expected, after);
        var previous = content(before); var current = content(after);
        Map<String, JsonNode> oldRows = linkRows(before), newRows = linkRows(after);
        Set<String> createdKeys = new TreeSet<>(current.keySet()); createdKeys.removeAll(previous.keySet());
        Set<String> deletedKeys = new TreeSet<>(previous.keySet()); deletedKeys.removeAll(current.keySet());
        Set<String> updatedKeys = new TreeSet<>();
        previous.forEach((key, value) -> {
            if (current.containsKey(key)) {
                assertEquals(positiveLong(oldRows.get(key).path("id"), "id before"),
                        positiveLong(newRows.get(key).path("id"), "id after"), "Изменён id существующей связи " + key);
                if (!value.equals(current.get(key))) updatedKeys.add(key);
            }
        });
        List<JsonNode> created = new ArrayList<>(), old = new ArrayList<>(), updated = new ArrayList<>(), deleted = new ArrayList<>();
        for (JsonNode chunk : chunks.stream().distinct().toList()) {
            long config = snapshotConfig(chunk);
            for (JsonNode snapshot : optionalArray(chunk.path("totalLinksFlushed"), "createdLinks"))
                created.add(withConfig(snapshot, config));
            for (JsonNode pair : optionalArray(chunk.path("totalLinksFlushed"), "updatedLinks")) {
                JsonNode left = withConfig(pair.path("oldLink"), config), right = withConfig(pair.path("updatedLink"), config);
                assertEquals(key(left), key(right), "Обновление меняет принадлежность связи");
                assertEquals(positiveLong(left.path("id"), "id oldLink"), positiveLong(right.path("id"), "id updatedLink"),
                        "Обновление меняет id существующей связи");
                old.add(left); updated.add(right);
            }
            for (JsonNode snapshot : optionalArray(chunk.path("totalLinksFlushed"), "deletedLinks"))
                deleted.add(withConfig(snapshot, config));
        }
        assertEquals(select(current, createdKeys), content(created), "createdLinks должен точно описывать созданные связи");
        assertEquals(select(previous, updatedKeys), content(old), "oldLink должен сохранять независимый исходный снимок БД");
        assertEquals(select(current, updatedKeys), content(updated), "updatedLink должен совпадать с независимыми данными и БД");
        assertEquals(select(previous, deletedKeys), content(deleted), "deletedLinks должен точно описывать удалённые связи");
        for (JsonNode snapshot : created) if (!absent(snapshot.path("id")))
            assertEquals(positiveLong(newRows.get(key(snapshot)).path("id"), "id созданной связи БД"),
                    positiveLong(snapshot.path("id"), "назначенный id createdLinks"), "Неверный назначенный id созданной связи");
        snapshotIds(before, old); snapshotIds(after, updated);
        snapshotIds(before, deleted);
    }
    private static Map<String, JsonNode> linkRows(Collection<JsonNode> links) {
        Map<String, JsonNode> rows = new TreeMap<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode link : links) {
            assertTrue(ids.add(positiveLong(link.path("id"), "id связи БД")), "Повторный id связи БД");
            assertNull(rows.put(key(link), link), "Повторная связь БД");
        }
        return rows;
    }
    private static long chunkConfig(JsonNode chunk, Set<Long> configs) {
        assertTrue(chunk.path("configIds").isArray(), "Нет массива configIds");
        assertEquals(1, chunk.path("configIds").size(), "Чанк должен содержать одну конфигурацию");
        long config = positiveLong(chunk.path("configIds").get(0), "configId чанка");
        assertTrue(configs.contains(config), "Чужой configId в событии собственной задачи " + config);
        return config;
    }
    private static long snapshotConfig(JsonNode chunk) {
        assertTrue(chunk.path("configIds").isArray(), "Нет массива configIds снимка");
        assertEquals(1, chunk.path("configIds").size(), "Сопоставление снимков требует чанк 1");
        assertTrue(chunk.path("totalLinksFlushed").isObject(), "Нет объекта totalLinksFlushed");
        return positiveLong(chunk.path("configIds").get(0), "configId снимка");
    }
    private static List<JsonNode> optionalArray(JsonNode object, String field) {
        if (absent(object.path(field))) return List.of();
        assertTrue(object.path(field).isArray(), "Ожидался массив " + field);
        List<JsonNode> values = new ArrayList<>(); object.path(field).forEach(values::add); return values;
    }
    public static List<JsonNode> chunks(List<JsonNode> events) { return events.stream().filter(e -> e.has("configIds")).toList(); }
    public static ObjectNode normalRequest(JsonNode request) {
        assertTrue(request.isObject(), "В журнале нет объекта исходящего запроса: " + request);
        positiveLong(request.path("requestId"), "requestId исходящего запроса");
        ObjectNode copy = request.deepCopy(); copy.remove("requestId");
        // JSON_NULL и отсутствие необязательного поля равнозначны; остальные параметры сравниваются точно.
        if (copy.path("cjIds").isNull()) copy.remove("cjIds");
        // Persisted groupCommunications is a HashSet; the Campaign mapper cannot preserve input ID-list order.
        // Preserve multiplicity and null/absent distinctions; only these two membership lists are unordered.
        for (JsonNode config : copy.path("configs")) {
            JsonNode query = config.path("basicQuery");
            for (String field : List.of("commIdsInclude", "commIdsExclude")) {
                JsonNode ids = query.path(field);
                if (absent(ids)) continue;
                assertTrue(ids.isArray(), "Ожидался массив Long: basicQuery." + field);
                List<Long> values = new ArrayList<>();
                ids.forEach(value -> values.add(positiveLong(value, "basicQuery." + field)));
                Collections.sort(values);
                var normalized = ((ObjectNode) query).putArray(field); values.forEach(normalized::add);
            }
        }
        return (ObjectNode) integralNumbers(copy);
    }
    /** Preserve response structure and List ordering; only CampaignConfigDto's four Sets are unordered. */
    public static void campaignResponses(JsonNode expected, JsonNode actual) {
        assertEquals(campaignResponse(expected), campaignResponse(actual),
                "Campaign-ответ отличается от независимых данных");
    }
    private static JsonNode campaignResponse(JsonNode response) {
        assertTrue(response.isObject() && response.path("configScope").isArray(), "Нет массива configScope в Campaign-ответе");
        ObjectNode copy = response.deepCopy();
        for (JsonNode scope : copy.path("configScope")) {
            assertTrue(scope.isObject() && scope.path("cjs").isArray(), "Нет массива cjs в configScope");
            positiveLong(scope.path("configId"), "configId ответа Campaign");
            for (JsonNode campaign : scope.path("cjs")) {
                assertTrue(campaign.isObject(), "Ожидался объект CampaignConfigDto");
                positiveLong(campaign.path("id"), "id ответа Campaign");
                for (String field : List.of("productCrmIds", "channelIds", "templateIds", "configCommIds")) {
                    assertTrue(campaign.path(field).isArray(), "Нет массива CampaignConfigDto." + field);
                    SortedMap<String, JsonNode> values = new TreeMap<>();
                    for (JsonNode value : campaign.path(field)) {
                        JsonNode normalized = setElement(value, field.equals("productCrmIds"), field);
                        assertNull(values.put(normalized.toString(), normalized), "Повторный элемент Campaign-множества " + field);
                    }
                    var array = ((ObjectNode) campaign).putArray(field); values.values().forEach(array::add);
                }
            }
        }
        return integralNumbers(copy);
    }
    private static JsonNode setElement(JsonNode value, boolean text, String field) {
        if (text) {
            assertTrue(value.isTextual(), "Ожидался String в множестве " + field); return value.deepCopy();
        }
        assertTrue(value.isIntegralNumber() && value.canConvertToLong(), "Ожидался Long в множестве " + field);
        return JSON.getNodeFactory().numberNode(value.bigIntegerValue());
    }
    /** JSON has one integral number representation; Jackson IntNode/LongNode are not a wire distinction. */
    private static JsonNode integralNumbers(JsonNode node) {
        if (node.isIntegralNumber()) return JSON.getNodeFactory().numberNode(node.bigIntegerValue());
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(), integralNumbers(field.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = JSON.createArrayNode(); node.forEach(value -> result.add(integralNumbers(value))); return result;
        }
        return node.deepCopy();
    }
    private static void snapshotIds(List<JsonNode> database, List<JsonNode> snapshots) {
        Map<String, JsonNode> rows = new HashMap<>(); database.forEach(row -> rows.put(key(row), row));
        for (JsonNode snapshot : snapshots) {
            JsonNode row = rows.get(key(snapshot));
            assertNotNull(row, "Снимок ссылается на отсутствующую связь " + key(snapshot));
            assertEquals(positiveLong(row.path("id"), "id связи БД"), positiveLong(snapshot.path("id"), "id снимка"),
                    "Снимок содержит неверный ID существующей связи " + key(snapshot));
        }
    }
    private static ObjectNode withConfig(JsonNode snapshot, long config) {
        assertTrue(snapshot.isObject(), "Отсутствует снимок связи: " + snapshot);
        ObjectNode copy = snapshot.deepCopy();
        positiveLong(copy.path("cjId"), "cjId снимка");
        if (copy.has("configId")) assertEquals(config, positiveLong(copy.path("configId"), "configId снимка"), "Снимок содержит configId другого чанка");
        return copy.put("configId", config);
    }
    private static <T> Map<String,T> select(Map<String,T> source, Set<String> keys) {
        Map<String,T> result = new TreeMap<>(); keys.forEach(k -> result.put(k, source.get(k))); return result;
    }
    public static void times(JsonNode event) {
        assertTrue(event.path("hostname").isTextual() && !event.path("hostname").textValue().isBlank(),
                "В событии отсутствует непустой String hostname");
        assertFalse(time(event.path("endDt").asText()).isBefore(time(event.path("startDt").asText())), "Окончание события раньше начала");
    }
    public static OffsetDateTime time(String value) {
        try { return OffsetDateTime.parse(value); }
        catch (java.time.format.DateTimeParseException shortOffset) {
            return OffsetDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]X"));
        }
    }
}
