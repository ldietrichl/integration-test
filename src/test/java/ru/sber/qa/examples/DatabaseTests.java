package ru.sber.qa.examples;

import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.config.entity.DatabaseTestFixture;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.services.db.DatabaseService;

import static ru.sber.qa.matchers.DatabaseMatchers.tableHaveSize;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class DatabaseTests extends DatabaseTestFixture {

    @Test
    void dataBaseInitializationTest() {
        DatabaseService databaseService = Environment.getForCurrentThread().getService(DatabaseService.class);
        databaseService.dataBaseClient("way")
                .executeSelect("SELECT * FROM pc")
                .should(tableHaveSize(8))
                .firstRow()
                .should(
                        DatabaseMatchers.haveColumn("code"),
                        DatabaseMatchers.haveCellValue("manufacturer", TextConditions.equalToText("AMD")),
                        DatabaseMatchers.haveCellValue("model", TextConditions.equalToText("Ontares")),
                        DatabaseMatchers.haveCellValue("price", TextConditions.equalToText("217.0")),
                        DatabaseMatchers.haveNonNullCellValue("code"),
                        DatabaseMatchers.haveCellValueEqualTo("code", "197")
                );
    }
}