package steps.db.configurations.v2;

import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.services.db.DatabaseClient;
import ru.sber.qa.services.db.validation.ValidatableTable;

public class ConfigsDbSteps {
    private final DatabaseClient client;

    public ConfigsDbSteps(DatabaseClient client) {
        this.client = client;
    }

    public ValidatableTable getExpActionRequestTableByRequestId(String requestId) {
        return client.executeSelect("""
                SELECT * FROM configurations.exp_action_request
                WHERE request_id = '%s'
                """.formatted(requestId));
    }

    public ValidatableTable getSplittingConfigRequestTableByRequestId(String requestId) {
        return client.executeSelect("""
                SELECT * FROM configurations.splitting_config_request
                WHERE request_id = '%s'
                """.formatted(requestId));
    }

    /***
     * удаляет запись из exp_action_request по request_id, проверяет что запись удалена из БД
     * @param requestId UUID запроса на изменение эксперимента
     */
    public void deleteExpActionRequestByRequestId(String requestId) {
        client.executeUpdate("""
                DELETE FROM configurations.exp_action_request
                WHERE request_id = '%s'
                """.formatted(requestId));
        client.executeSelect("""
                                    SELECT * FROM configurations.exp_action_request
                                    WHERE request_id = '%s'
                """.formatted(requestId)).should(
                DatabaseMatchers.tableHaveSize(0));
    }

    /***
     * удаляет запись из splitting_config_request по request_id, проверяет что запись удалена из БД
     * @param requestId UUID запроса на формирование новой конфигурации сплиттования
     */
    public void deleteSplittingConfigRequestByRequestId(String requestId) {
        client.executeUpdate("""
                DELETE FROM configurations.splitting_config_request
                WHERE request_id = '%s'
                """.formatted(requestId));
        client.executeSelect("""
                                    SELECT * FROM configurations.splitting_config_request
                                    WHERE request_id = '%s'
                """.formatted(requestId)).should(
                DatabaseMatchers.tableHaveSize(0));
    }
}
