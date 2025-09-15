package ru.sber.qa.examples;

import graphql.ExecutionInput;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.config.entity.graphql.GraphQLTestData;
import ru.sber.qa.config.entity.graphql.flow.GraphQLCustomFlow;
import ru.sber.qa.config.entity.graphql.flow.custom_steps.GraphQLCustomSteps;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.services.rest.RestService;

import java.util.Map;

import static ru.sber.qa.matchers.RestMatchers.haveStatusCode;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class GraphQLTests {

    GraphQLCustomSteps steps = new GraphQLCustomSteps(Environment.getForCurrentThread().getService(RestService.class));

    @Test
    void initTests() {
        //GraphQL запрос
        String query = steps.readSchema("pathToSchema.graphqls");
        //Формирование переменной для запроса (в данном случае переменная - класс GraphQLTestData)
        //Переменная "MGNT" является тикером инструмента на бирже
        GraphQLTestData variables = steps.getTestData("MGNT");

        ExecutionInput executionInput = steps.getExecutionInput(query, Map.of("rq", variables),
                "someOperationName");

        //Выполнение запроса с помощью RestService с использованием токена авторизации
        steps.sendMessageWithToken("authorizationToken", executionInput, "hostname")
                .should(haveStatusCode(200));
        //Выполнение запроса с помощью RestService с использованием сертификатов
        //Раскомментировать, если сертификаты используются
        //steps.sendMessageWithCerts(restService, steps.getExecutionInput(query,
        //Map.of("rq", variables), "someOperationName"))
        //.should(haveStatusCode(200));
    }

    @Test
    void initFlowTests() {
        FlowRunner.flowRunnerFor(GraphQLTestFlow.class)
                .step("Проверяем ответ на тестовый запрос GraphQL", flow -> {
                    //Формирование переменной для запроса (в данном случае переменная - класс GraphQLTestData)
                    GraphQLTestData variables = flow.graphQLCustom().getTestData("someTicker");
                    //Формирование тела запроса GraphQL
                    ExecutionInput execution = flow.graphQLCustom()
                            .getExecutionInput(flow.graphQLCustom()
                                            .readSchema("pathToSchema.graphqls")
                                    , Map.of("rq", variables), "someOperationName");
                    //Выполнение запроса с помощью RestService с использованием токена авторизации
                    flow.graphQLCustom()
                            .sendMessageWithToken("AuthorizationToken", execution, "hostName")
                            .should(haveStatusCode(200));
                    //Выполнение запроса с помощью RestService, с использованием сертификатов
                    //Раскомментировать, если сертификаты используются
                    //flow.graphQLCustom()
                    //.sendMessageWithCerts(execution, "hostName")
                    //.should(haveStatusCode(200));
                })
                .run();
    }

    static class GraphQLTestFlow implements Flow, GraphQLCustomFlow {
    }
}