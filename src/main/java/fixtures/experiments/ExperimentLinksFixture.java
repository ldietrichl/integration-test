package fixtures.experiments;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import config.services.core.ExperimentLinksSettings;
import dto.experiments.v1.ExperimentsV1PostRequestDtoBuilder;
import io.qameta.allure.Allure;
import steps.db.experiments.links.ExperimentLinksDbSteps;
import steps.rest.experiments.links.ExperimentLinksRestSteps;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static util.splittercheck.experimentlinks.ExperimentLinksAssertions.*;

/** Собственные эксперименты и независимое чтение реальных страниц campaign-service. */
public final class ExperimentLinksFixture implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FIELDS = List.of("productCrmIds", "channelIds", "templateIds", "configCommIds");
    private final String prefix = "EXPLAB-3109-" + UUID.randomUUID();
    private final ExperimentLinksSettings settings;
    private final ExperimentLinksRestSteps rest;
    private final ExperimentLinksDbSteps db;
    public final List<Long> experiments = new ArrayList<>();
    public final Map<Long, Long> configs = new LinkedHashMap<>();
    private final Map<Long, Long> campaigns = new LinkedHashMap<>();
    private final Map<Long, ObjectNode> requests = new LinkedHashMap<>();
    private final Set<String> validatedDictionaries = new HashSet<>();
    public final Map<Long, List<Page>> pages = new LinkedHashMap<>();
    public record Page(JsonNode request, int status, JsonNode response) {
        public Page { request = request.deepCopy(); response = response.deepCopy(); }
    }
    public ExperimentLinksFixture(ExperimentLinksSettings settings, ExperimentLinksRestSteps rest, ExperimentLinksDbSteps db) {
        this.settings = settings; this.rest = rest; this.db = db;
    }
    public static long selectWorkingCampaign(ExperimentLinksRestSteps rest, List<Long> candidates) throws Exception {
        for (long candidate : candidates) if (!workingCommunicationIds(rest, candidate).isEmpty()) return candidate;
        throw new IllegalStateException("BLOCKED_ENVIRONMENT: ограниченная выборка кампаний не дала непустых WORKING-коммуникаций");
    }
    /** IDs получаются живым запросом Campaign Service, без примеров или констант коммуникаций. */
    public static Set<Long> workingCommunicationIds(ExperimentLinksRestSteps rest, long campaignId) throws Exception {
        if (campaignId <= 0) throw new IllegalArgumentException("Нужен положительный ID реальной кампании");
        ObjectNode query = JSON.createObjectNode().put("scope", "WORKING").put("allCjComms", false);
        query.putArray("commIdsInclude"); query.putArray("commIdsExclude");
        query.putArray("filters").addObject().put("parameterCode", "cjId").put("operatorCode", "in")
                .putArray("values").add(Long.toString(campaignId));
        var reply = rest.request("POST", "/api/v1/campaigns/communications/id-list", query.toString(), "campaign", "application/json");
        if (reply.status() != 200)
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: чтение настоящего WORKING id-list: HTTP " + reply.status());
        if (!reply.json().path("ids").isArray())
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: Campaign Service не вернул массив ids");
        var ids = ExperimentLinksDbSteps.longs(reply.json().path("ids"));
        if (ids.stream().anyMatch(id -> id <= 0)) throw new IllegalStateException("Некорректные реальные communication IDs");
        return Collections.unmodifiableSet(ids);
    }
    public long create(long campaignId) throws Exception { return create(campaignId, Set.of()); }
    public long create(long campaignId, Collection<Long> excludedCommIds) throws Exception {
        db.refreshBarrier();
        Set<Long> excluded = new TreeSet<>(Objects.requireNonNull(excludedCommIds));
        if (excluded.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("Нужны положительные реальные excludedCommIds");
        if (!excluded.isEmpty() && !workingCommunicationIds(rest, campaignId).containsAll(excluded))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: исключаемые коммуникации отсутствуют в текущем WORKING id-list кампании");
        ObjectNode body = fixtureBody(campaignId, excluded);
        for (String field : List.of("objectId", "formatId")) {
            String value = settings.value("fixture." + field, "");
            if (!value.isBlank()) body.put(field, Integer.parseInt(value));
            if (validatedDictionaries.add(field)) {
                String path = field.equals("objectId") ? "/api/v1/experiments/objects" : "/api/v1/experiments/formats";
                var dictionary = rest.request("GET", path, null, "direct", "application/json");
                if (dictionary.status() != 200 || !dictionary.json().isArray())
                    throw new IllegalStateException("BLOCKED_ENVIRONMENT: не прочитан действующий справочник " + field);
                boolean present = false;
                for (JsonNode item : dictionary.json()) if (item.path("id").asLong(-1) == body.path(field).asLong(-2)) present = true;
                if (!present) throw new IllegalStateException("BLOCKED_ENVIRONMENT: значение " + field + "=" + body.path(field)
                        + " из проектной фабрики отсутствует в действующем справочнике; задайте fixture." + field);
            }
        }
        db.refreshBarrier();
        var reply = rest.request("POST", "/api/v1/experiments", body.toString(), "direct", "application/json");
        assertEquals(200, reply.status(), "Создание собственного эксперимента: ожидался HTTP 200; ответ=" + reply.text());
        long id = reply.json().path("id").asLong(-1);
        assertTrue(id > 0, "Сервис не вернул положительный ID созданного эксперимента: " + reply.json());
        experiments.add(id);
        var found = db.registerExperiment(id, prefix);
        assertEquals(1, found.size(), "В собственном эксперименте должен быть ровно один конфиг; experimentId=" + id);
        long config = found.get(0);
        configs.put(id, config); campaigns.put(config, campaignId);
        // Запрос источника строится из того же DTO, который принят при создании эксперимента.
        ObjectNode groupConfig = (ObjectNode) body.at("/experimentGroups/0/groupConfig");
        ObjectNode request = JSON.createObjectNode().put("page", 0);
        request.putArray("cjIds").add(campaignId);
        ObjectNode cfg = request.putArray("configs").addObject().put("configId", config)
                .put("dynamicConfig", groupConfig.path("dynamicConfig").asBoolean());
        cfg.set("staticConfig", groupConfig.path("staticConfig").deepCopy());
        ObjectNode query = JSON.createObjectNode();
        for (String field : List.of("allCjComms", "filters", "commIdsInclude", "commIdsExclude"))
            query.set(field, groupConfig.path("configQuery").path(field).deepCopy());
        query.put("scope", "WORKING"); cfg.set("basicQuery", query);
        requests.put(config, request);
        attach("Собственные объекты", JSON.createObjectNode().put("experimentId", id)
                .put("configId", config).put("campaignId", campaignId).put("owner", prefix));
        return id;
    }
    private ObjectNode fixtureBody(long campaignId, Set<Long> excluded) {
        var dto = ExperimentsV1PostRequestDtoBuilder.linksFixture(
                prefix + "-" + experiments.size(), List.of(Long.toString(campaignId)), 1);
        if (!excluded.isEmpty()) dto = dto.toBuilder().experimentGroups(dto.getExperimentGroups().stream().map(group ->
                group.getGroupConfig() == null ? group : group.toBuilder().groupConfig(group.getGroupConfig().toBuilder()
                        .configQuery(group.getGroupConfig().getConfigQuery().toBuilder().commIdsExclude(List.copyOf(excluded)).build())
                        .build()).build()).toList()).build();
        return JSON.valueToTree(dto);
    }
    public long config(long exp) { return configs.get(exp); }
    private ObjectNode sourceRequest(long config, int page, int size) {
        ObjectNode base = requests.get(config);
        assertNotNull(base, "Не зарегистрирована собственная конфигурация " + config);
        return base.deepCopy().put("requestId", System.currentTimeMillis()).put("page", page).put("size", size);
    }
    private List<Page> observe(long config, int size) throws Exception {
        List<Page> result = new ArrayList<>();
        int limit = 100;
        for (int page = 0; page < limit; page++) {
            ObjectNode request = sourceRequest(config, page, size);
            var reply = rest.request("POST", "/api/v1/campaigns/communications/config-details",
                    request.toString(), "campaign", "application/json");
            result.add(new Page(request, reply.status(), reply.json()));
            attach("Независимый ответ источника: config=" + config + ", page=" + page,
                    JSON.createObjectNode().set("pages", JSON.valueToTree(result)));
            assertEquals(200, reply.status(), "Независимое чтение страницы " + page + ": ожидался HTTP 200; ответ=" + reply.text());
            JsonNode scope = scope(reply.json(), config);
            assertTrue(scope.path("isLastPage").isBoolean(), "В ответе отсутствует boolean isLastPage: " + scope);
            assertTrue(scope.path("cjs").isArray(), "В ответе отсутствует массив cjs: " + scope);
            for (JsonNode campaign : scope.path("cjs")) {
                assertTrue(campaign.isObject(), "Источник вернул не объект кампании");
                assertEquals(campaigns.get(config).longValue(), positiveLong(campaign.path("id"), "id кампании источника"), "Источник вернул кампанию вне фильтра собственного конфига");
                for (String field : FIELDS) assertTrue(campaign.path(field).isArray(), "Нет массива " + field + ": " + campaign);
            }
            if (scope.path("isLastPage").asBoolean()) return List.copyOf(result);
        }
        throw new IllegalStateException("BLOCKED_ENVIRONMENT: источник не завершил выдачу за " + limit + " страниц");
    }
    /** Из журнала берутся только наблюдаемые page/size; эталонный ответ читается независимо. */
    public void captureFromJournal(Set<Long> selected, List<JsonNode> events) throws Exception {
        Map<Long, JsonNode> byConfig = new LinkedHashMap<>();
        for (JsonNode chunk : new LinkedHashSet<>(chunks(events))) {
            assertEquals(1, chunk.path("configIds").size(), "Для сопоставления источника размер чанка должен быть 1");
            long config = chunk.path("configIds").get(0).asLong();
            assertTrue(selected.contains(config), "В журнале задачи обнаружен чужой configId=" + config);
            assertNull(byConfig.put(config, chunk), "Несколько различных событий чанка для одной успешной конфигурации " + config);
        }
        assertEquals(selected, byConfig.keySet(), "Нет событий всех собственных конфигураций");
        for (long config : selected) {
            JsonNode requestsLog = byConfig.get(config).path("requestResponsePages");
            assertTrue(requestsLog.isArray() && !requestsLog.isEmpty(), "Нет фактических запросов Campaign Service для config=" + config);
            int size = -1;
            for (int page = 0; page < requestsLog.size(); page++) {
                JsonNode request = requestsLog.get(page).path("request");
                assertTrue(request.path("size").isIntegralNumber() && request.path("size").canConvertToInt()
                        && request.path("size").intValue() > 0, "Не наблюдается положительный размер страницы: " + request);
                if (size == -1) size = request.path("size").intValue();
                assertEquals(size, request.path("size").intValue(), "Размер страницы изменился внутри одной конфигурации");
                assertEquals(normalRequest(sourceRequest(config, page, size)), normalRequest(request),
                        "Исходящий запрос не соответствует принятой собственной конфигурации; config=" + config + ", page=" + page);
            }
            settings.recordObservedPageSize(size);
            Allure.addAttachment("Фактически наблюдаемый размер страницы: config=" + config, "text/plain",
                    "source=published-campaign-request\nchunk=1\npageSize=" + size
                    + "\nРазмеры стенда не изменяются; ответы источника получаются независимым HTTP-чтением.", ".txt");
            List<Page> independent = pages.get(config);
            if (independent == null) {
                independent = observe(config, size);
                pages.put(config, independent);
            }
            assertEquals(requestsLog.size(), independent.size(), "Независимый источник и журнал имеют разное число страниц; возможны изменения источника");
            for (int page = 0; page < independent.size(); page++) {
                assertEquals(normalRequest(independent.get(page).request()), normalRequest(requestsLog.get(page).path("request")),
                        "Запрос страницы отличается от независимого preflight; config=" + config + ", page=" + page);
                campaignResponses(independent.get(page).response(), requestsLog.get(page).path("response"));
            }
        }
    }
    /** Независимый эталон до запуска проверяемой задачи; размер уже наблюдался на реальном warmup. */
    public List<JsonNode> captureSource(Set<Long> selected) throws Exception {
        int size = settings.pageSize();
        if (selected.isEmpty()) throw new IllegalArgumentException("Нужны собственные конфигурации для preflight");
        for (long config : selected) {
            db.refreshBarrier();
            pages.put(config, observe(config, size));
        }
        return expected(selected);
    }
    public void verifyStable() throws Exception { verifyStable(Set.copyOf(pages.keySet())); }
    public void verifyStable(Set<Long> selected) throws Exception {
        for (long config : selected) {
            List<Page> previous = pages.get(config);
            assertNotNull(previous, "Нет независимого preflight для config=" + config);
            List<Page> actual = observe(config, settings.pageSize());
            assertEquals(previous.size(), actual.size(), "Изменилась пагинация реального источника; config=" + config);
            for (int i = 0; i < actual.size(); i++) {
                assertEquals(previous.get(i).status(), actual.get(i).status(), "Изменился HTTP-статус источника; page=" + i);
                if (actual.get(i).status() == 200) campaignResponses(previous.get(i).response(), actual.get(i).response());
            }
        }
    }
    public List<JsonNode> expected(Set<Long> selected) {
        List<JsonNode> result = new ArrayList<>();
        for (long config : selected) {
            Map<Long, ObjectNode> merged = new LinkedHashMap<>();
            for (Page page : pages.get(config)) {
                if (page.status() != 200) continue;
                for (JsonNode campaign : scope(page.response(), config).path("cjs")) {
                    ObjectNode target = merged.computeIfAbsent(campaign.path("id").asLong(), id -> link(config, campaign));
                    ObjectNode incoming = link(config, campaign);
                    for (String field : List.of("productCrmIds", "channelIds", "templateIds", "commIds")) {
                        Set<JsonNode> union = new LinkedHashSet<>();
                        target.path(field).forEach(union::add); incoming.path(field).forEach(union::add);
                        ArrayNode values = target.putArray(field); union.forEach(values::add);
                    }
                }
            }
            result.addAll(merged.values());
        }
        return result;
    }
    public static JsonNode scope(JsonNode response, long config) {
        assertTrue(config > 0, "Нужен положительный собственный configId");
        assertTrue(response.isObject() && response.path("configScope").isArray(), "Ожидался массив configScope в ответе источника");
        assertEquals(1, response.path("configScope").size(), "Одиночный независимый запрос должен вернуть ровно один configScope");
        JsonNode found = response.path("configScope").get(0);
        assertTrue(found.isObject(), "configScope должен содержать объект");
        assertEquals(config, positiveLong(found.path("configId"), "configId источника"), "Источник вернул чужой configId");
        assertTrue(found.path("isLastPage").isBoolean(), "В источнике отсутствует boolean isLastPage");
        assertTrue(found.path("cjs").isArray(), "В источнике отсутствует массив cjs");
        return found;
    }
    private static void attach(String name, JsonNode value) { Allure.addAttachment(name, "application/json", value.toString(), ".json"); }
    @Override public void close() throws Exception {
        if (!db.pending().isEmpty()) throw new IllegalStateException("Очистка отложена: очередь не пуста; собственные эксперименты=" + experiments);
        List<Throwable> failures = new ArrayList<>();
        for (long id : experiments) try {
            db.refreshBarrier();
            db.status(id, "DRAFT");
            var reply = rest.request("DELETE", "/api/v1/experiments/" + id, null, "direct", "application/json");
            assertEquals(200, reply.status(), "Не удалён собственный эксперимент " + id + ": " + reply.text());
            db.verifyDeleted(id, configs.containsKey(id) ? List.of(configs.get(id)) : List.of());
        } catch (Exception | AssertionError error) { failures.add(error); }
        if (!failures.isEmpty()) {
            var error = new IllegalStateException("Не завершена очистка собственных экспериментов: " + experiments);
            failures.forEach(error::addSuppressed); throw error;
        }
    }
}
