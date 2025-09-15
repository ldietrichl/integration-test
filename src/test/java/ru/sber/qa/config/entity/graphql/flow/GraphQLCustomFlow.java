package ru.sber.qa.config.entity.graphql.flow;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.config.entity.graphql.flow.custom_steps.GraphQLCustomSteps;
import ru.sber.qa.services.rest.RestService;

public interface GraphQLCustomFlow {

    default GraphQLCustomSteps graphQLCustom() {
        RestService restService = Environment
                .getForCurrentThread()
                .getService(RestService.class);
        return new GraphQLCustomSteps(restService);
    }
}