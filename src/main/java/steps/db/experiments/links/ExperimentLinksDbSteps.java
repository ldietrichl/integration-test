package steps.db.experiments.links;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.services.core.ExperimentLinksSettings;
import io.perfeccionista.framework.Environment;
import io.qameta.allure.Allure;
import ru.sber.qa.services.db.*;
import util.db.SqlParameters;
import java.util.*;
import java.util.stream.Collectors;

/** Работа только с зарегистрированными собственными объектами и барьером бизнес-очереди. */
public final class ExperimentLinksDbSteps {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DatabaseClient client;
    private final String schema;
    private final String owner = "links-test-" + UUID.randomUUID();
    private final Set<Long> experiments = new LinkedHashSet<>();
    private final Set<Long> configs = new LinkedHashSet<>();
    private Long barrierId;
    public ExperimentLinksDbSteps(ExperimentLinksSettings settings) {
        client = Environment.getForCurrentThread().getService(DatabaseService.class).dataBaseClient(settings.database());
        schema = settings.schema();
    }
    public List<JsonNode> rows(String sql, Object... args) {
        List<JsonNode> rows = new ArrayList<>();
        for (var row : client.executeSelect(SqlParameters.bind(sql, args)).toSimpleTable()) try {
            rows.add(JSON.readTree(String.valueOf(row.values().iterator().next())));
        } catch (Exception e) { throw new IllegalStateException("Не удалось прочитать JSON-снимок БД", e); }
        return rows;
    }
    public void requirePrimary() {
        var state = rows("SELECT json_build_object('recovery',pg_is_in_recovery(),"
                + "'readOnly',current_setting('transaction_read_only'))::text").get(0);
        if (state.path("recovery").asBoolean(true) || !"off".equals(state.path("readOnly").asText()))
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: нужен writable primary PostgreSQL; фактически " + state);
    }
    /** Only real IDs from a bounded sample; existing links are read and never used as test fixtures. */
    public List<Long> campaignCandidates() {
        var sampled = rows("SELECT json_build_object('campaignId',cj_id,'communications',COALESCE(cardinality(comm_ids),0))::text "
                + "FROM " + schema + ".exp_campaign_link ORDER BY id DESC LIMIT 1000");
        sampled.sort(Comparator.comparingInt((JsonNode n) -> n.path("communications").asInt()).reversed());
        return sampled.stream().map(n -> n.path("campaignId").asLong()).filter(id -> id > 0).distinct().limit(20).toList();
    }
    /** Часы primary БД ограничивают request_dt без зависимости от часов JVM тестов. */
    public long clockMillis() {
        JsonNode now = rows("SELECT json_build_object('epochMillis',floor(extract(epoch FROM clock_timestamp())*1000)::bigint)::text").get(0);
        if (!now.path("epochMillis").isIntegralNumber() || !now.path("epochMillis").canConvertToLong() || now.path("epochMillis").longValue() <= 0)
            throw new IllegalStateException("БД не вернула положительный epoch millis Long");
        return now.path("epochMillis").longValue();
    }
    public List<JsonNode> queue() { return rows("SELECT row_to_json(t)::text FROM " + schema + ".exp_campaign_link_status t ORDER BY id"); }
    public List<JsonNode> pending() { return queue().stream().filter(t -> barrierId == null || t.path("id").asLong() != barrierId).toList(); }
    public void holdQueue() {
        if (barrierId != null || !queue().isEmpty())
            throw new IllegalStateException("BLOCKED_ENVIRONMENT: перед собственным барьером очередь должна быть пуста");
        insertBarrier(null);
        var found = queue().stream().filter(t -> owner.equals(t.at("/current_state/failureReason").asText())).toList();
        if (found.size() != 1) throw new IllegalStateException("Не удалось идентифицировать собственный барьер очереди");
        barrierId = found.get(0).path("id").asLong();
        requireBarrier();
        if (!pending().isEmpty()) throw new IllegalStateException("BLOCKED_ENVIRONMENT: обнаружен внешний писатель очереди");
        Allure.addAttachment("Собственный барьер очереди", "text/plain", barrierId + ": " + owner, ".txt");
    }
    private void insertBarrier(Long id) {
        String fields = "request_dt,exp_ids,is_processing,is_interrupted,is_marked_to_stop,attempts_count,recreates_count,start_processing_dt,current_state";
        // Даже после watchdog барьер не должен превратиться в полный пересчёт: реальные fixture ID положительны.
        String values = "?,ARRAY[?]::bigint[],true,false,false,0,0,(CURRENT_TIMESTAMP AT TIME ZONE 'UTC'),?::jsonb";
        String marker = "{\"failureReason\":\"" + owner + "\"}";
        if (id == null) client.executeUpdate(SqlParameters.bind("INSERT INTO " + schema + ".exp_campaign_link_status (" + fields + ") VALUES (" + values + ")", System.currentTimeMillis(), Long.MIN_VALUE, marker));
        else client.executeUpdate(SqlParameters.bind("INSERT INTO " + schema + ".exp_campaign_link_status (id," + fields + ") VALUES (?," + values + ")", id, System.currentTimeMillis(), Long.MIN_VALUE, marker));
    }
    public void releaseQueue() {
        requireBarrier();
        client.executeUpdate(SqlParameters.bind("DELETE FROM " + schema + ".exp_campaign_link_status WHERE id=? AND current_state->>'failureReason'=? AND is_processing=true AND is_marked_to_stop=false", barrierId, owner));
        if (!rows("SELECT row_to_json(t)::text FROM " + schema + ".exp_campaign_link_status t WHERE id=? OR current_state->>'failureReason'=?", barrierId, owner).isEmpty())
            throw new IllegalStateException("После удаления остался собственный барьер очереди");
    }
    /** Восстанавливает только ранее созданный барьер с тем же меньшим ID, не трогая исполняемую задачу. */
    public void holdForCleanup() {
        if (barrierId == null) return;
        requireOwnedPending();
        if (queue().stream().noneMatch(t -> t.path("id").asLong() == barrierId)) insertBarrier(barrierId);
        requireBarrier();
    }
    public boolean hasBarrier() { return barrierId != null; }
    /** Обновляется только время собственного живого барьера; marked/чужие задачи не исправляются. */
    public void refreshBarrier() {
        requireBarrier();
        client.executeUpdate(SqlParameters.bind("UPDATE " + schema + ".exp_campaign_link_status "
                + "SET start_processing_dt=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') WHERE id=? AND is_processing=true "
                + "AND is_marked_to_stop=false AND current_state->>'failureReason'=?", barrierId, owner));
        requireBarrier();
    }
    public void requireOwnedPending() {
        for (JsonNode task : pending()) {
            Set<Long> ids = longs(task.path("exp_ids"));
            if (ids.isEmpty() || !experiments.containsAll(ids))
                throw new IllegalStateException("BLOCKED_ENVIRONMENT: чужая задача не изменяется: " + task);
        }
    }
    public boolean processingOwned() {
        requireOwnedPending();
        return pending().stream().anyMatch(t -> t.path("is_processing").asBoolean());
    }
    public void clearFixtureTasks() {
        requireBarrier(); requireOwnedPending();
        for (JsonNode task : pending()) {
            if (task.path("is_processing").asBoolean()) throw new IllegalStateException("Сохранена выполняемая задача " + task.path("id"));
            client.executeUpdate(SqlParameters.bind("DELETE FROM " + schema + ".exp_campaign_link_status WHERE id=? AND is_processing=false AND exp_ids <@ ARRAY[" + ids(experiments) + "]::bigint[]", task.path("id").asLong()));
        }
        requireBarrier(); requireOwnedPending();
        if (!pending().isEmpty()) throw new IllegalStateException("После очистки остались собственные задачи очереди");
    }
    public List<Long> registerExperiment(long id, String prefix) {
        var actual = rows("SELECT row_to_json(e)::text FROM " + schema + ".experiment e WHERE id=?", id);
        if (actual.size() != 1 || !actual.get(0).path("name").asText().startsWith(prefix))
            throw new IllegalStateException("Не подтверждено владение экспериментом " + id);
        experiments.add(id);
        List<Long> found = rows("SELECT row_to_json(c)::text FROM " + schema + ".exp_group_config c JOIN "
                + schema + ".experiment_experiment_group g ON g.group_id=c.exp_group_id WHERE g.experiment_id=? ORDER BY c.id", id)
                .stream().map(n -> n.path("id").asLong()).toList();
        configs.addAll(found); return found;
    }
    public List<JsonNode> links() {
        if (configs.isEmpty()) return List.of();
        return rows("SELECT json_build_object('id',id,'configId',config_id,'cjId',cj_id,'productCrmIds',product_crm_ids,"
                + "'channelIds',channel_ids,'templateIds',template_ids,'commIds',comm_ids)::text FROM "
                + schema + ".exp_campaign_link WHERE config_id IN (" + ids(configs) + ") ORDER BY config_id,cj_id,id");
    }
    public void clearLinks() {
        requireBarrier();
        if (!configs.isEmpty()) client.executeUpdate("DELETE FROM " + schema + ".exp_campaign_link WHERE config_id IN (" + ids(configs) + ")");
    }
    public void seedEmptyLink(long config, long campaign) {
        requireBarrier();
        if (!configs.contains(config)) throw new IllegalArgumentException("Чужой configId=" + config);
        client.executeUpdate(SqlParameters.bind("INSERT INTO " + schema + ".exp_campaign_link "
                + "(config_id,cj_id,product_crm_ids,channel_ids,template_ids,comm_ids) "
                + "VALUES (?, ?, ARRAY[]::text[], ARRAY[]::bigint[], ARRAY[]::bigint[], ARRAY[]::bigint[])", config, campaign));
    }
    /** Подготовка delete-предусловия данными независимого Campaign-снимка только в собственном конфиге. */
    public void seedLink(long config, long campaign, JsonNode snapshot) {
        requireBarrier();
        if (!configs.contains(config)) throw new IllegalArgumentException("Чужой configId=" + config);
        if (campaign <= 0 || !snapshot.path("cjId").isIntegralNumber() || !snapshot.path("cjId").canConvertToLong() || snapshot.path("cjId").longValue() != campaign)
            throw new IllegalArgumentException("Снимок должен принадлежать выбранной реальной кампании");
        if (links().stream().anyMatch(link -> link.path("configId").asLong() == config && link.path("cjId").asLong() == campaign))
            throw new IllegalStateException("Предусловие seedLink требует отсутствующую собственную связь");
        String values = arraySql(snapshot.path("productCrmIds"), true) + "," + arraySql(snapshot.path("channelIds"), false)
                + "," + arraySql(snapshot.path("templateIds"), false) + "," + arraySql(snapshot.path("commIds"), false);
        String insert = SqlParameters.bind("INSERT INTO " + schema + ".exp_campaign_link "
                + "(config_id,cj_id,product_crm_ids,channel_ids,template_ids,comm_ids) VALUES (?, ?, ", config, campaign);
        client.executeUpdate(insert + values + ")");
        var seeded = links().stream().filter(link -> link.path("configId").asLong() == config && link.path("cjId").asLong() == campaign).toList();
        if (seeded.size() != 1) throw new IllegalStateException("Не подтверждено создание собственной исходной связи");
        for (String field : List.of("productCrmIds", "channelIds", "templateIds", "commIds")) {
            Set<JsonNode> expected = new HashSet<>(), actual = new HashSet<>();
            snapshot.path(field).forEach(expected::add); seeded.get(0).path(field).forEach(actual::add);
            if (!expected.equals(actual)) throw new IllegalStateException("Искажён набор собственной исходной связи: " + field);
        }
    }
    private static String arraySql(JsonNode values, boolean text) {
        if (!values.isArray()) throw new IllegalArgumentException("Ожидался массив исходного снимка");
        List<String> encoded = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode value : values) {
            if (text) {
                if (!value.isTextual()) throw new IllegalArgumentException("productCrmIds должен содержать строки");
                if (!unique.add(value.textValue())) throw new IllegalArgumentException("Повторный элемент productCrmIds исходного снимка");
                encoded.add(SqlParameters.bind("?", value.textValue()));
            } else {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) throw new IllegalArgumentException("Ожидался массив Long исходного снимка");
                if (!unique.add(Long.toString(value.longValue()))) throw new IllegalArgumentException("Повторный элемент Long исходного снимка");
                encoded.add(Long.toString(value.longValue()));
            }
        }
        return "ARRAY[" + String.join(",", encoded) + "]::" + (text ? "text[]" : "bigint[]");
    }
    /** Финальная проверка всех зарегистрированных собственных объектов и относящихся к ним задач. */
    public void verifyCleanup() {
        if (!experiments.isEmpty() && !rows("SELECT row_to_json(e)::text FROM " + schema + ".experiment e WHERE id IN (" + ids(experiments) + ")").isEmpty())
            throw new IllegalStateException("После очистки остались собственные эксперименты");
        if (!configs.isEmpty() && (!rows("SELECT row_to_json(c)::text FROM " + schema + ".exp_group_config c WHERE id IN (" + ids(configs) + ")").isEmpty()
                || !rows("SELECT row_to_json(l)::text FROM " + schema + ".exp_campaign_link l WHERE config_id IN (" + ids(configs) + ")").isEmpty()))
            throw new IllegalStateException("После очистки остались собственные конфиги или связи");
        String ownTasks = "current_state->>'failureReason'=?" + (barrierId == null ? "" : " OR id=" + barrierId)
                + (experiments.isEmpty() ? "" : " OR exp_ids && ARRAY[" + ids(experiments) + "]::bigint[]");
        if (!rows("SELECT row_to_json(t)::text FROM " + schema + ".exp_campaign_link_status t WHERE " + ownTasks, owner).isEmpty())
            throw new IllegalStateException("После очистки остался собственный барьер или задача своих экспериментов");
    }
    public void status(long exp, String status) {
        if (!experiments.contains(exp) || !"DRAFT".equals(status)) throw new IllegalArgumentException("Изменять статус можно только своего эксперимента перед удалением");
        requireBarrier();
        client.executeUpdate(SqlParameters.bind("UPDATE " + schema + ".experiment SET status=? WHERE id=?", status, exp));
    }
    public void verifyDeleted(long id, Collection<Long> configIds) {
        if (!experiments.contains(id)) throw new IllegalArgumentException("Чужой experimentId=" + id);
        if (!rows("SELECT row_to_json(e)::text FROM " + schema + ".experiment e WHERE id=?", id).isEmpty())
            throw new IllegalStateException("Эксперимент не удалён: " + id);
        if (!configIds.isEmpty() && (!rows("SELECT row_to_json(c)::text FROM " + schema + ".exp_group_config c WHERE id IN (" + ids(configIds) + ")").isEmpty()
                || !rows("SELECT row_to_json(l)::text FROM " + schema + ".exp_campaign_link l WHERE config_id IN (" + ids(configIds) + ")").isEmpty()))
            throw new IllegalStateException("После удаления остались собственные конфиги или связи: " + id);
    }
    private void requireBarrier() {
        var tasks = queue();
        if (barrierId == null || tasks.isEmpty() || tasks.get(0).path("id").asLong() != barrierId
                || !owner.equals(tasks.get(0).at("/current_state/failureReason").asText())
                || !tasks.get(0).path("is_processing").asBoolean() || tasks.get(0).path("is_marked_to_stop").asBoolean())
            throw new IllegalStateException("Собственный барьер отсутствует, просрочен или не первый в очереди; чужое состояние не изменяется");
    }
    private static String ids(Collection<Long> ids) { return ids.stream().map(String::valueOf).collect(Collectors.joining(",")); }
    public static Set<Long> longs(JsonNode array) {
        Set<Long> result = new TreeSet<>();
        array.forEach(n -> { if (!n.isIntegralNumber() || !n.canConvertToLong()) throw new IllegalArgumentException("Ожидался массив Long: " + array); result.add(n.longValue()); });
        return result;
    }
}
