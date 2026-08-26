package ru.sber.qa.experiments.EXPLAB_2928;

import config.environment.special.EnvironmentConfigWIthRestDbV2;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.sber.qa.allure.CriticalRegression;
import ru.sber.qa.allure.Regression;
import ru.sber.qa.experiments.statuschange.AbstractStatusChangeFlowTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(PerfeccionistaExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@SetEnvironmentConfiguration(EnvironmentConfigWIthRestDbV2.class)
@ResourceLock("experiment-status-change")
public class StatusChangeElementSchema2928FlowTest extends AbstractStatusChangeFlowTest {
    private static final Set<String> EXPECTED_COLUMNS = Set.of(
            "id",
            "transaction_id",
            "request_id",
            "splitting_point_code",
            "exp_id",
            "exp_status",
            "exp_target_status",
            "action_type",
            "action",
            "result",
            "result_details",
            "user_id",
            "created_dt",
            "updated_dt"
    );

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            "id",
            "transaction_id",
            "request_id",
            "splitting_point_code",
            "exp_id",
            "exp_status",
            "exp_target_status",
            "action_type",
            "action",
            "user_id",
            "created_dt",
            "updated_dt"
    );

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2928-DB-01. Таблица status_change_element создана миграцией")
    void statusChangeElementTableShouldExist() {
        assertStatusChangeElementTableExists();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2928-DB-02/03/04. Состав и обязательность колонок status_change_element соответствуют PDF")
    void tableColumnsAndNullabilityShouldMatchSpecification() {
        assumeStatusChangeElementTableExists();

        getFlowWithDb()
                .step("Читаем физический контракт таблицы status_change_element", flow -> {
                    List<Map<String, Object>> rows = flow.dbExpLabClient().executeSelect("""
                            SELECT column_name, data_type, is_nullable, character_maximum_length
                            FROM information_schema.columns
                            WHERE table_schema = 'experiments'
                              AND table_name = 'status_change_element'
                            ORDER BY ordinal_position
                            """).toSimpleTable();

                    Map<String, Map<String, Object>> columns = rows.stream().collect(Collectors.toMap(
                            row -> String.valueOf(row.get("column_name")),
                            Function.identity()
                    ));

                    assertEquals(EXPECTED_COLUMNS, columns.keySet(), "Состав колонок отличается от спецификации");
                    assertAll(
                            REQUIRED_COLUMNS.stream()
                                    .map(column -> () -> assertEquals(
                                            "NO",
                                            String.valueOf(columns.get(column).get("is_nullable")),
                                            "Колонка должна быть NOT NULL: " + column
                                    ))
                    );
                    assertEquals("YES", String.valueOf(columns.get("result").get("is_nullable")));
                    assertEquals("YES", String.valueOf(columns.get("result_details").get("is_nullable")));
                })
                .run();
    }

    @CriticalRegression
    @Test
    @DisplayName("EXPLAB-2928-DB-05. Незавершенное действие сохраняется с result=null")
    void pendingActionShouldBeStoredWithNullResult() {
        assumeStatusChangeElementTableExists();

        String transactionId = newTransactionId();
        String requestId = newRequestId();

        getFlowWithDb()
                .step("Создаем незавершенный элемент смены статуса", flow ->
                        flow.dbCustomSteps().statusChangeDbSteps().insertStatusChangeElement(
                                transactionId,
                                requestId,
                                syntheticExpId(),
                                null,
                                null
                        ))
                .step("Проверяем состояние записи", flow -> {
                    Map<String, Object> row = flow.dbCustomSteps().statusChangeDbSteps()
                            .findElementByRequestId(requestId)
                            .singleRow()
                            .toSimpleRow();

                    assertNull(row.get("result"));
                    assertNull(row.get("result_details"));
                    assertNotNull(row.get("created_dt"));
                    assertNotNull(row.get("updated_dt"));
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2928-DB-07. result_details не ограничен 255 символами")
    void resultDetailsShouldSupportLongErrorText() {
        assumeStatusChangeElementTableExists();

        getFlowWithDb()
                .step("Проверяем тип result_details", flow -> {
                    Map<String, Object> column = flow.dbExpLabClient().executeSelect("""
                            SELECT data_type, character_maximum_length
                            FROM information_schema.columns
                            WHERE table_schema = 'experiments'
                              AND table_name = 'status_change_element'
                              AND column_name = 'result_details'
                            """).singleRow().toSimpleRow();

                    String dataType = String.valueOf(column.get("data_type"));
                    Object maximumLength = column.get("character_maximum_length");
                    boolean supportsLongText = "text".equals(dataType)
                            || maximumLength == null
                            || ((Number) maximumLength).longValue() >= 1_000L;

                    assertTrue(supportsLongText, "result_details не должен ограничиваться VARCHAR(255)");
                })
                .run();
    }

    @Regression
    @Test
    @DisplayName("EXPLAB-2928-DB-08. exp_id и user_id защищены внешними ключами")
    void experimentAndUserReferencesShouldHaveForeignKeys() {
        assumeStatusChangeElementTableExists();

        getFlowWithDb()
                .step("Проверяем внешние ключи status_change_element", flow -> {
                    List<Map<String, Object>> foreignKeys = flow.dbExpLabClient().executeSelect("""
                            SELECT
                                kcu.column_name,
                                ccu.table_schema AS foreign_table_schema,
                                ccu.table_name AS foreign_table_name,
                                ccu.column_name AS foreign_column_name
                            FROM information_schema.table_constraints tc
                            JOIN information_schema.key_column_usage kcu
                              ON tc.constraint_name = kcu.constraint_name
                             AND tc.constraint_schema = kcu.constraint_schema
                            JOIN information_schema.constraint_column_usage ccu
                              ON tc.constraint_name = ccu.constraint_name
                             AND tc.constraint_schema = ccu.constraint_schema
                            WHERE tc.constraint_type = 'FOREIGN KEY'
                              AND tc.table_schema = 'experiments'
                              AND tc.table_name = 'status_change_element'
                            """).toSimpleTable();

                    assertAll(
                            () -> assertTrue(hasForeignKey(
                                    foreignKeys, "exp_id", "experiments", "experiment", "id"),
                                    "Не найден FK exp_id -> experiments.experiment.id"),
                            () -> assertTrue(hasForeignKey(
                                    foreignKeys, "user_id", "users", "user", "id"),
                                    "Не найден FK user_id -> users.user.id")
                    );
                })
                .run();
    }

    private static boolean hasForeignKey(
            List<Map<String, Object>> rows,
            String column,
            String foreignSchema,
            String foreignTable,
            String foreignColumn
    ) {
        return rows.stream().anyMatch(row ->
                column.equals(String.valueOf(row.get("column_name")))
                        && foreignSchema.equals(String.valueOf(row.get("foreign_table_schema")))
                        && foreignTable.equals(String.valueOf(row.get("foreign_table_name")))
                        && foreignColumn.equals(String.valueOf(row.get("foreign_column_name"))));
    }
}
