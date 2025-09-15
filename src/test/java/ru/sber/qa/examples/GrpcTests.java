package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.config.services.grpc.flow.GrpcFlow;
import ru.sber.qa.config.services.grpc.flow.steps.GrpcBaseSteps;
import ru.sber.qa.config.services.grpc.server.GrpcServer;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;

import java.sql.Date;
import java.text.SimpleDateFormat;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValueEqualTo;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class GrpcTests {

    @Test
    @DisplayName("Тест без Flow")
    void grpcTest() {
//        //нужно только для нашего теста, у ребят будут свои уже раскатанные сервера
        GrpcServer server = new GrpcServer();
        server.startTestGrpcServer(8080);

        step("Проверяем ответ на тестовый запрос", () -> {
//                    FrameGrpcTestRs generatedMessage = (FrameGrpcTestRs) a.toGeneratedMessage();
//                    generatedMessage.getRsTm();

            new GrpcBaseSteps().getTestMessage()
                    .toValidatableJson()
                    .should(
                            haveJsonValueEqualTo(
                                    "rsTm",
                                    new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis())))
                    );
        });

        //нужно только для нашего теста, у ребят будут свои уже раскатанные сервера
        server.stopTestGrpcServer();
    }

    @Test
    @DisplayName("Тест с Flow")
    void grpcFlowTest() {
        //нужно только для нашего теста, у ребят будут свои уже раскатанные сервера
        GrpcServer server = new GrpcServer();
        server.startTestGrpcServer(8080);

        FlowRunner.flowRunnerFor(GrpcTestFlow.class)
                .step("Проверяем ответ на тестовый запрос с флоу", flow -> flow.grpc()
                        .getTestMessage()
                        .toValidatableJson()
                        .should(
                                haveJsonValueEqualTo(
                                        "rsTm",
                                        new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis())))
                        )).run();

        //нужно только для нашего теста, у ребят будут свои уже раскатанные сервера
        server.stopTestGrpcServer();
    }


    static class GrpcTestFlow implements Flow, GrpcFlow {
    }

}
