package ru.sber.qa.config.services.grpc.flow;

import ru.sber.qa.config.services.grpc.flow.steps.GrpcBaseSteps;

public interface GrpcFlow {

    default GrpcBaseSteps grpc() {
        return new GrpcBaseSteps();
    }
}
