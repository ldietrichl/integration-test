package ru.sber.qa.config.services.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GrpcServer {

    private Server server;

    public void startTestGrpcServer(int port) {
       server = ServerBuilder
                .forPort(port)
                .addService(new FrameTestServiceGrpcImpl()).build();

        try {
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stopTestGrpcServer() {
        server.shutdownNow();
    }
}