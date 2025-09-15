package ru.sber.qa.config.entity.graphql.flow.custom_steps;

import ru.sber.qa.config.entity.graphql.AppData;
import ru.sber.qa.config.entity.graphql.GraphQLTestData;
import ru.sber.qa.flow.steps.GraphQLSteps;
import ru.sber.qa.services.rest.RestService;


import java.time.LocalDateTime;
import java.util.UUID;

public class GraphQLCustomSteps extends GraphQLSteps {

    public GraphQLCustomSteps(RestService service) {
        super(service);
    }

    public GraphQLTestData getTestData(String ticker) {
        AppData appData = new AppData("3.5.0", "ANDROID");
        return new GraphQLTestData(UUID.randomUUID().toString(),
                LocalDateTime.now().toString(),
                ticker,
                "someClassCode",
                "торговая площадка",
                appData);
    }
}