package ru.sber.qa.config.services.grpc.server;

import io.grpc.stub.StreamObserver;
import ru.sber.qa.grpc.FrameGrpcTestRq;
import ru.sber.qa.grpc.FrameGrpcTestRs;
import ru.sber.qa.grpc.FrameTestServiceGrpc;

public class FrameTestServiceGrpcImpl extends FrameTestServiceGrpc.FrameTestServiceImplBase {

    @Override
    public void frameGrpcTestMessage(FrameGrpcTestRq rq, StreamObserver<FrameGrpcTestRs> responseObserver) {
        String message = new StringBuilder()
                .append("id: ")
                .append(rq.getId())
                .append(" sent message on: ")
                .append(rq.getRqTm())
                .toString();

        FrameGrpcTestRs response = FrameGrpcTestRs.newBuilder()
                .setRqUid(rq.getRqUid())
                .setRsTm(rq.getRqTm())
                .setMessage(message)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}