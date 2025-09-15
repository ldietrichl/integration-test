package ru.sber.qa.config.services.grpc.flow.steps;

import io.grpc.netty.NettyChannelBuilder;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.config.services.grpc.flow.GrpcFlow;
import ru.sber.qa.grpc.FrameGrpcTestRq;
import ru.sber.qa.grpc.FrameGrpcTestRs;
import ru.sber.qa.grpc.FrameTestServiceGrpc;
import services.grpc.GrpcClient;
import services.grpc.GrpcService;
import services.grpc.validation.DefaultValidatableGeneratedMessage;
import services.grpc.validation.ValidatableGeneratedMessage;
import services.interceptors.LoggingClientInterceptor;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GrpcBaseSteps implements GrpcFlow {

    public GrpcBaseSteps() {}

    private GrpcClient getClient() {
        //Собираем клиент и добавляем в него канал и интерцепторы
        //Билдер можно использовать любой ManagedChannelBuilder/NettyChannelBuilder/OkHttpChannelBuilder
        //в зависимости от конкретной потребности
        return
                //Получаем клиент
                Environment.getForCurrentThread().getService(GrpcService.class).grpcClient(
                        //Билдим канал(любым доступным способом ManagedChannelBuilder/NettyChannelBuilder/OkHttpChannelBuilder)
                        NettyChannelBuilder.forAddress("localhost", 8080)
                        .usePlaintext()
                        //ОБЯЗАТЕЛЬНО инжектим интерцептор для логирования в консоль и аллюр(как в примере)
                        .intercept(new LoggingClientInterceptor())
                        .build()
                );
    }

    //обязательно возвращаем ValidatableGeneratedMessage, чтобы можно было использовать мэтчеры
    public ValidatableGeneratedMessage getTestMessage() {

        //создаем нужный стаб из сформированного из proto файла сервиса(в сигнатуре собранный канал)
        FrameTestServiceGrpc.FrameTestServiceBlockingStub stub = FrameTestServiceGrpc.newBlockingStub(getClient().channel());

        //собираем запрос для получения ответа
        FrameGrpcTestRs rs = stub.frameGrpcTestMessage(
                FrameGrpcTestRq.newBuilder()
                        .setId("1")
                        .setRqTm(new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis())))
                        .setRqUid("123456789")
                        .build()
        );

        //преобразуем в ValidatableGeneratedMessage, чтобы можно было использовать мэтчеры
        return new DefaultValidatableGeneratedMessage(rs);
    }


}
