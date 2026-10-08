package steps.db.experiments.v2;

import config.services.core.StatusChange2972Settings;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.DatabaseService;
import java.sql.Timestamp;
import java.util.*;

/** Read-only observations through the project's Platform V AT DatabaseService. */
public final class StatusChange2972DbSteps implements AutoCloseable {
    private final DatabaseClient client;

    public StatusChange2972DbSteps(StatusChange2972Settings settings, String service) {
        client = Environment.getForCurrentThread().getService(DatabaseService.class)
                .dataBaseClient(settings.optional("db." + service + ".name", "explab"));
    }

    public List<Map<String, Object>> query(String sql, Object... parameters) {
        if (!sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT "))
            throw new IllegalArgumentException("Only SELECT observations are supported");
        String query = bind(sql, parameters);
        return io.qameta.allure.Allure.step("БД: наблюдение EXPLAB-2972", () -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> source : client.executeSelect(query).toSimpleTable()) {
                Map<String, Object> row = new LinkedHashMap<>();
                source.forEach((key, value) -> row.put(key, normalize(value)));
                result.add(row);
            }
            return result;
        });
    }

    public List<Map<String, Object>> actions(long id) {
        return query("SELECT * FROM experiments.status_change_element WHERE exp_id=? ORDER BY id", id);
    }

    private static Object normalize(Object value) {
        if (value instanceof Timestamp time) return time.toInstant().toEpochMilli();
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean) return value;
        return value.toString();
    }

    // DatabaseClient exposes string SQL; substitutions are restricted to quoted scalar values.
    public static String bind(String template, Object... values) {
        StringBuilder sql = new StringBuilder();
        int index = 0;
        for (int i = 0; i < template.length(); i++) {
            if (template.charAt(i) != '?') { sql.append(template.charAt(i)); continue; }
            if (index >= values.length) throw new IllegalArgumentException("Missing SQL argument");
            Object value = values[index++];
            if (value == null) sql.append("NULL");
            else if (value instanceof Long || value instanceof Integer || value instanceof Boolean) sql.append(value);
            else if (value instanceof String || value instanceof UUID)
                sql.append('\'').append(value.toString().replace("'", "''")).append('\'');
            else throw new IllegalArgumentException("Unsupported SQL argument type: " + value.getClass());
        }
        if (index != values.length) throw new IllegalArgumentException("Too many SQL arguments");
        return sql.toString();
    }

    @Override public void close() { /* Environment owns the service lifecycle. */ }
}
