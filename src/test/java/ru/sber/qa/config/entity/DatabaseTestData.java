package ru.sber.qa.config.entity;

import org.sql2o.data.Column;
import org.sql2o.data.Row;
import org.sql2o.data.Table;
import org.sql2o.quirks.NoQuirks;
import ru.sber.qa.services.db.validation.DefaultValidatableTable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @deprecated Данный класс является источником синтетических тестовых данных для демонстрационных тестов и
 * неприменим в реальных тестах.
 */
@Deprecated(forRemoval = true)
public class DatabaseTestData {
    private Map<String, Integer> columnNameToIdxMap = new HashMap<>();
    private List<Column> columnList = new ArrayList<>();
    private static final String TABLE_NAME = "PC";

    public DatabaseTestData() {
        columnNameToIdxMap.put("code", 0);
        columnNameToIdxMap.put("manufacturer", 1);
        columnNameToIdxMap.put("model", 2);
        columnNameToIdxMap.put("price", 3);

        columnList.add(new Column("CODE", 0, "INTEGER"));
        columnList.add(new Column("MANUFACTURER", 1, "CHARACTER VARYING"));
        columnList.add(new Column("MODEL", 2, "CHARACTER VARYING"));
        columnList.add(new Column("PRICE", 3, "REAL"));
    }

    public DefaultValidatableTable getTableWithAllRows() {
        Table table = new Table(
                TABLE_NAME,
                List.of(
                        createRow(columnNameToIdxMap, new Object[]{ 197, "AMD", "Ontares", 217d }),
                        createRow(columnNameToIdxMap, new Object[]{ 784, "Intel", "Falcon", 115d }),
                        createRow(columnNameToIdxMap, new Object[]{ 289, "Intel", "Amost", 158d }),
                        createRow(columnNameToIdxMap, new Object[]{ 274, "Intel", "Imoles", 271d }),
                        createRow(columnNameToIdxMap, new Object[]{ 421, "Intel", "Feller", 321d }),
                        createRow(columnNameToIdxMap, new Object[]{ 384, "Intel", "Boosil", 212d }),
                        createRow(columnNameToIdxMap, new Object[]{ 587, "AMD", "Jeremy", 235d }),
                        createRow(columnNameToIdxMap, new Object[]{ 741, "Azure", "Giant", 121d })
                ),
                columnList
        );

        return DefaultValidatableTable.defaultValidatableTable(table);
    }

    private static Row createRow(Map<String, Integer> columnNameToIdxMap, Object[] values) {
        Row result = new Row(columnNameToIdxMap, 4, false, new NoQuirks());
        Field field;
        try {
            field = result.getClass().getDeclaredField("values");
            field.setAccessible(true);
            field.set(result, values);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return result;
    }
}
