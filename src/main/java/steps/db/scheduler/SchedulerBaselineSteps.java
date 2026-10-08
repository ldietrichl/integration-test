package steps.db.scheduler;

import config.services.core.SchedulerSettings;
import io.qameta.allure.Allure;
import java.util.*;

/** Read-only compatibility checks using the flow's existing framework database client. */
public final class SchedulerBaselineSteps {
    private final SchedulerDbSteps db;
    public SchedulerBaselineSteps(SchedulerDbSteps db) { this.db = db; }

    public void requireSchema(SchedulerSettings settings) {
        Allure.step("Scheduler: inspect schema through framework explab client", () -> {
            String profile = settings.optional("schema.profile", "dev-v2");
            if (!Set.of("dev-v2", "legacy-null").contains(profile))
                throw new IllegalStateException("scheduler.<env>.schema.profile must be dev-v2 or legacy-null");
            List<Map<String, Object>> columns = db.query(
                    "SELECT table_name, column_name, data_type, is_nullable, column_default " +
                    "FROM information_schema.columns WHERE table_schema = 'scheduler' " +
                    "AND table_name IN ('task', 'task_action') ORDER BY table_name, ordinal_position");
            Allure.addAttachment("Scheduler schema (" + settings.environment + ", " + profile + ")", columns.toString());
            Map<String, Object> version = columns.stream()
                    .filter(c -> "task".equals(c.get("table_name")) && "version".equals(c.get("column_name")))
                    .findFirst().orElseThrow(() -> new IllegalStateException("scheduler.task.version is unavailable to explab client"));
            String expectedNullable = "dev-v2".equals(profile) ? "NO" : "YES";
            if (!expectedNullable.equals(version.get("is_nullable")))
                throw new IllegalStateException("Schema differs from selected " + profile + " baseline: task.version.is_nullable=" + version.get("is_nullable"));
            for (String table : List.of("task", "task_action")) {
                if (columns.stream().noneMatch(c -> table.equals(c.get("table_name")) && "id".equals(c.get("column_name"))))
                    throw new IllegalStateException("Missing accessible scheduler." + table + ".id");
            }
        });
    }

    public void attachReferenceInventory() {
        Allure.step("Scheduler: read dictionary and migration inventory without changing data", () -> {
            for (String table : List.of("field_dict", "field_enum_dict", "field_operator_dict", "databasechangelog")) {
                // Only hard-coded identifiers are interpolated; no operator input becomes SQL.
                List<Map<String, Object>> rows = db.query("SELECT count(*) AS row_count FROM scheduler." + table);
                Allure.addAttachment("scheduler." + table + " row count", rows.toString());
                if (rows.isEmpty() || !(rows.get(0).get("row_count") instanceof Number)
                        || ((Number) rows.get(0).get("row_count")).longValue() == 0)
                    throw new IllegalStateException("Empty scheduler baseline table: " + table);
            }
            Allure.addAttachment("Scheduler constraints", db.query(
                    "SELECT conname, pg_get_constraintdef(oid) AS definition FROM pg_constraint " +
                    "WHERE conrelid = 'scheduler.task_action'::regclass ORDER BY conname").toString());
        });
    }
}
