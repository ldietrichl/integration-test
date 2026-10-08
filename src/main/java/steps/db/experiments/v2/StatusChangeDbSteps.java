package steps.db.experiments.v2;

import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.validation.ValidatableTable;

public class StatusChangeDbSteps {
    private static final String EXPERIMENTS_SCHEMA = "experiments";
    private static final String STATUS_CHANGE_ELEMENT_TABLE = "status_change_element";
    private static final String STATUS_CHANGE_REQUEST_TABLE = "exp_status_change_request";

    private final DatabaseClient client;

    public StatusChangeDbSteps(DatabaseClient client) {
        this.client = client;
    }

    public void insertStatusChangeElement(
            String transactionId,
            String requestId,
            long expId,
            String result,
            String resultDetails
    ) {
        insertStatusChangeElement(
                transactionId,
                requestId,
                expId,
                "DRAFT",
                "DRAFT",
                result,
                resultDetails
        );
    }

    public void insertStatusChangeElement(
            String transactionId,
            String requestId,
            long expId,
            String expStatus,
            String expTargetStatus,
            String result,
            String resultDetails
    ) {
        client.executeUpdate("""
                INSERT INTO experiments.status_change_element (
                    transaction_id,
                    request_id,
                    splitting_point_code,
                    exp_id,
                    exp_status,
                    exp_target_status,
                    action_type,
                    action,
                    result,
                    result_details,
                    user_id,
                    created_dt,
                    updated_dt
                ) VALUES (
                    %s,
                    %s,
                    'MAPPER',
                    %d,
                    %s,
                    %s,
                    'STATUS_CHANGE',
                    'START',
                    %s,
                    %s,
                    1,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """.formatted(
                sqlValue(transactionId),
                sqlValue(requestId),
                expId,
                sqlValue(expStatus),
                sqlValue(expTargetStatus),
                sqlValue(result),
                sqlValue(resultDetails)
        ));
    }

    public ValidatableTable findElementsByTransactionId(String transactionId) {
        return client.executeSelect("""
                SELECT *
                FROM experiments.status_change_element
                WHERE transaction_id = %s
                ORDER BY id
                """.formatted(sqlValue(transactionId)));
    }

    public ValidatableTable findElementByRequestId(String requestId) {
        return findElementsByRequestId(requestId);
    }

    public ValidatableTable findElementsByRequestId(String requestId) {
        return client.executeSelect("""
                SELECT *
                FROM experiments.status_change_element
                WHERE request_id = %s
                ORDER BY id
                """.formatted(sqlValue(requestId)));
    }

    public ValidatableTable findRequestsByTraceId(String transactionId) {
        return client.executeSelect("""
                SELECT *
                FROM experiments.exp_status_change_request
                WHERE trace_id = %s
                ORDER BY id
                """.formatted(sqlValue(transactionId)));
    }

    public int countElements(String transactionId) {
        return findElementsByTransactionId(transactionId).size();
    }

    public int countRequests(String transactionId) {
        return findRequestsByTraceId(transactionId).size();
    }

    public boolean statusChangeElementTableExists() {
        return tableExists(EXPERIMENTS_SCHEMA, STATUS_CHANGE_ELEMENT_TABLE);
    }

    public boolean statusChangeRequestTableExists() {
        return tableExists(EXPERIMENTS_SCHEMA, STATUS_CHANGE_REQUEST_TABLE);
    }

    public void deleteFixture(String transactionId) {
        if (statusChangeElementTableExists()) {
            client.executeUpdate("""
                    DELETE FROM experiments.status_change_element
                    WHERE transaction_id = %s
                    """.formatted(sqlValue(transactionId)));
        }
        if (statusChangeRequestTableExists()) {
            client.executeUpdate("""
                    DELETE FROM experiments.exp_status_change_request
                    WHERE trace_id = %s
                    """.formatted(sqlValue(transactionId)));
        }
    }

    private boolean tableExists(String schemaName, String tableName) {
        Object count = client.executeSelect("""
                        SELECT COUNT(*) AS table_count
                        FROM information_schema.tables
                        WHERE table_schema = %s
                          AND table_name = %s
                        """.formatted(sqlValue(schemaName), sqlValue(tableName)))
                .singleRow()
                .toSimpleRow()
                .get("table_count");
        return count instanceof Number && ((Number) count).longValue() == 1L;
    }

    private static String sqlValue(String value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.replace("'", "''") + "'";
    }
}
