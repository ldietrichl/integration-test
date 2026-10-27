package util.db;

import java.util.UUID;

/** Scalar substitutions for DatabaseClient string SQL. */
public final class SqlParameters {
    private SqlParameters() {}

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

}
