package steps.db.fixtures;

import config.services.core.StandFixtureUsers;
import config.services.core.StandSettings;
import ru.sber.qa.services.db.DatabaseClient;
import steps.db.experiments.v2.StatusChange2972DbSteps;
import java.util.ArrayList;
import java.util.List;
import static io.qameta.allure.Allure.step;

/** Reusable SELECT-only fixture lookup through the project's selected stand database. */
public final class StandUserFixtureSteps {
    private final DatabaseClient client;
    public StandUserFixtureSteps(DatabaseClient client) { this.client = client; }

    public StandFixtureUsers.Pair selectActiveUsersForPrimary(long primary) {
        if (primary <= 0) throw new IllegalArgumentException("Expected a positive resolved user ID");
        return step("Select the resolved caller and one distinct active stand user", () -> {
            StandSettings stand = new StandSettings();
            String schema = stand.required("fixtures.users.schema");
            String point = stand.required("fixtures.users.splitting-point");
            if (!schema.matches("[a-z][a-z0-9_]*") || !point.matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException("Expected an approved user schema and splitting-point code");
            String sql = "SELECT id FROM \"" + schema + "\".\"user\" "
                    + "WHERE id > 0 AND blocked IS FALSE AND employee_id <> 'system_admin' "
                    + "AND splitting_points::jsonb @> CAST(? AS jsonb) "
                    + "ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END, id LIMIT 2";
            List<Long> ids = new ArrayList<>();
            for (var row : client.executeSelect(StatusChange2972DbSteps.bind(sql, "[\"" + point + "\"]", primary))
                    .toSimpleTable()) {
                Object value = row.entrySet().stream().filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                        .map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
                ids.add(new java.math.BigDecimal(value.toString()).longValueExact());
            }
            if (ids.size() != 2 || ids.get(0) != primary || ids.get(0).equals(ids.get(1)))
                throw new IllegalStateException("APPLICATION_FIXTURE_USER_UNAVAILABLE: resolved caller must be active "
                        + "with the selected splitting point; one distinct existing control user is also required");
            return new StandFixtureUsers.Pair(ids.get(0), ids.get(1));
        });
    }

    public StandFixtureUsers.Pair selectTwoActiveUsers() {
        return step("Select two distinct unblocked stand users without changing accounts or roles", () -> {
            StandSettings stand = new StandSettings();
            String schema = stand.required("fixtures.users.schema");
            if (!schema.matches("[a-z][a-z0-9_]*"))
                throw new IllegalArgumentException("A simple approved user schema name is required");
            String point = stand.required("fixtures.users.splitting-point");
            if (!point.matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException("A splitting-point code is required");
            String sql = "SELECT id FROM \"" + schema + "\".\"user\" "
                    + "WHERE id > 0 AND blocked IS FALSE AND employee_id <> 'system_admin' "
                    + "AND splitting_points::jsonb @> CAST(? AS jsonb) ORDER BY id LIMIT 2";
            List<Long> ids = new ArrayList<>();
            for (var row : client.executeSelect(StatusChange2972DbSteps.bind(sql, "[\"" + point + "\"]"))
                    .toSimpleTable()) {
                Object value = row.entrySet().stream().filter(e -> "id".equalsIgnoreCase(e.getKey()))
                        .map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
                ids.add(new java.math.BigDecimal(value.toString()).longValueExact());
            }
            if (ids.size() != 2 || ids.get(0).equals(ids.get(1)))
                throw new IllegalStateException("FIXTURE_USERS_UNAVAILABLE: need two unblocked existing users "
                        + "with the configured splitting point in the selected stand DB; no accounts created");
            return new StandFixtureUsers.Pair(ids.get(0), ids.get(1));
        });
    }
}
