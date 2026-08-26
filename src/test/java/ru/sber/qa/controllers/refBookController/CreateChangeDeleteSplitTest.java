package ru.sber.qa.controllers.refBookController;

import config.environment.EnvironmentConfigWithDbRest;
import flow.Flows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import request.splitter.splits.SplitsParams;
import ru.sber.qa.matchers.DatabaseMatchers;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.validation.ValidatableJson;
import ru.sber.qa.allure.CriticalRegression;

import static io.perfeccionista.framework.datasource.Stash.stash;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithDbRest.class)
public class CreateChangeDeleteSplitTest extends Flows {

    @CriticalRegression
    @Test
    void createChangeDeleteRefBookSplitSuccess() {
        getFlowWithDbRest()
                .step("Отправка запроса на создание сплита, проверка успешного ответа", flow -> {
                    SplitsParams params = new SplitsParams();
                    ValidatableJson result = flow
                            .restCustomSteps().splitSteps().createSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                            ).toValidatableJson();
                    stash().put("splitId", result.getJsonPath().get("id"));
                })

                .step("Проверка, что сплит создался в БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.split WHERE id = %s
                                        
                                        """.formatted(stash().get("splitId")))
                                .should(DatabaseMatchers.tableHaveSize(1)))

                .step("Отправка запроса на изменение сплита, проверка успешного ответа", flow -> {
                    SplitsParams params = SplitsParams.builder()
                            .id(((Number) (stash().get("splitId"))).longValue())
                            .status("COMPLETED")
                            .build();
                    flow.restCustomSteps().splitSteps().changeSplit(params)
                            .should(
                                    RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                            );

                })

                .step("Отправка запроса на удаление сплита, проверка успешного ответа", flow ->
                        flow.restCustomSteps().splitSteps().deleteSplit(stash().get("splitId"))
                                .should(
                                        RestMatchers.haveStatusCode(HttpStatus.SC_OK)
                                ))

                .step("Проверка, что сплит удалился в БД", flow ->
                        flow.dbExpLabClient()
                                .executeSelect("""
                                        SELECT * FROM experiments.split WHERE id = %s
                                        
                                        """.formatted(stash().get("splitId")))
                                .should(DatabaseMatchers.tableHaveSize(0)))
                .run();
    }
}
