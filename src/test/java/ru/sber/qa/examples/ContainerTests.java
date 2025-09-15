package ru.sber.qa.examples;

import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.restassured.path.json.JsonPath;
import jdk.jfr.Description;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.flow.ContainerServiceFlow;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.service.ContainerService;
import ru.sber.qa.validation.DefaultValidatableJson;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValueEqualTo;


@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
@Disabled("Для запуска требуется указать креды в файле container-service.properties")
public class ContainerTests {

    /**
     * Пример получения списка applicaiton.yml из блока data configmap для дальнейшей работы с ним
     *
     * @param service сервис для работы с контейнерами
     */
    @Test
    @Description("Получение application yml из конфигмапы для дальнейшей работы с ней")
    void containerServiceGetAppYMLTest(ContainerService service) {

        step("Получаем application yml", () -> {
            //Получаем application yml из конфигмапы с обоих блоков(dc1 и dc2)
            String appYml = service.containerServiceClient()
                    //Получаем список application.yml из конфигмапы
                    .getApplicationYml("podName")
                    //Выбираем из списка application.yml нужный, они идентичны
                    .get(0);
            //Поиск значения в полученном файле
            //тут можно поправить регулярное выражение, если вдруг не найдётся нужное значение
            Pattern pattern = Pattern.compile("item" + ": (\\S+)");
            //Объявление переменной для хранения результата поиска
            Matcher matcher = pattern.matcher(appYml);
            String finderResult = "";
            //Поиск значения
            if (matcher.find()) {
                finderResult = matcher.group(1);
            }
            //Преобразуем результат поиска в проверяемое значение, чтобы можно было применить
            //проверки с помощью реализованных методов в библиотеке ru.sber.qa.matchers.JsonMatchers
            DefaultValidatableJson actualValue = new DefaultValidatableJson(new JsonPath(finderResult));

            //Сравниваем ожидаемое значение с фактическим
            actualValue.should(haveJsonValueEqualTo("item", "expectedValue"));
        });
    }

    /**
     * Пример проверки статуса пода
     *
     * @param service сервис для работы с контейнерами
     */
    @Test
    @Description("Проверка статуса пода")
    void containerServiceCheckPodStatusTest(ContainerService service) {

        step("Получение статуса пода и сравнение с ожидаемым значением", () -> {

            //Получаем статус подов, Map будет содержать состояние двух подов из dc1 и dc2
            Map<String, String> podsStatusMap = service
                    .containerServiceClient()
                    .checkPodsStatus("podName");

            //Преобразуем результат поиска в проверяемое значение, чтобы можно было применить
            //проверки с помощью реализованных методов в библиотеке ru.sber.qa.matchers.JsonMatchers
            DefaultValidatableJson validatableJson = new DefaultValidatableJson(new JsonPath(String.valueOf(new JSONObject(podsStatusMap))));

            //Проверяем что статус подов true
            for (var key : podsStatusMap.keySet()) {
                validatableJson.should(haveJsonValueEqualTo(key, String.valueOf(true),
                        "Поды перезагружаются или не запущены"));
            }
        });
    }

    /**
     * Пример перезапуска подов
     *
     * @param service сервис для работы с контейнерами
     */
    @Test
    @Description("Перезапуск подов в кластере")
    void containerServiceRestartPodTest(ContainerService service) {

        step("Перезапуск подов", () -> {
            //Метод перезапуска подов кластере в dc1 и dc2
            service.containerServiceClient().restartPods("podName");

            //Получаем статус подов, Map будет содержать состояние двух подов из dc1 и dc2
            Map<String, String> podsStatusMap = service.containerServiceClient().checkPodsStatus("podName");

            //Преобразуем результат поиска в проверяемое значение, чтобы можно было применить
            //проверки с помощью реализованных методов в библиотеке ru.sber.qa.matchers.JsonMatchers
            DefaultValidatableJson validatableJson = new DefaultValidatableJson(new JsonPath(String.valueOf(new JSONObject(podsStatusMap))));

            //Проверяем что статус подов false, они находятся в состоянии перезагрузки
            for (var key : podsStatusMap.keySet()) {
                validatableJson.should(haveJsonValueEqualTo(key, String.valueOf(false),
                        "Поды запущены, а не находятся в состоянии перезагрузки"));
            }
        });
    }

    /**
     * Пример обновления поля в applicationYML блоком data configmap
     *
     * @param service сервис для работы с контейнерами
     */
    @Test
    @Description("Обновление поля в applicationYML ")
    void containerServiceUpdateValuesInAppYMLTest(ContainerService service) {

        step("Обновляем поля в application yml", () -> {
            //Обновление значений в yml, необходимо указать имя приклада(например invest-selection),
            //имя поля и новое значение
            service.containerServiceClient().updateValuesInApplicationYml("podName"
                    , "keyForChange"
                    , "valueForChange");
        });

        step("Проверка что поля обновились", () -> {
            //Получаем application yml из конфигмапы с обоих блоков(dc1 и dc2)
            String appYml = service.containerServiceClient()
                    //Получаем список application.yml из конфигмапы
                    .getApplicationYml("podName")
                    //Выбираем из списка application.yml нужный, они идентичны
                    .get(0);
            //Поиск значения в полученном файле
            //тут можно поправить регулярное выражение, если вдруг не найдётся нужное значение
            Pattern pattern = Pattern.compile("item" + ": (\\S+)");
            //Объявление переменной для хранения результата поиска
            Matcher matcher = pattern.matcher(appYml);
            String finderResult = "";
            //Поиск значения
            if (matcher.find()) {
                finderResult = matcher.group(1);
            }
            //Преобразуем результат поиска в проверяемое значение, чтобы можно было применить
            //проверки с помощью реализованных методов в библиотеке ru.sber.qa.matchers.JsonMatchers
            DefaultValidatableJson actualValue = new DefaultValidatableJson(new JsonPath(finderResult));

            //Сравниваем ожидаемое значение с фактическим
            actualValue.should(haveJsonValueEqualTo("keyForChange", "valueForChange"));
        });
    }

    @Test
    @Description("Проверка статусов подов через flow")
    void containerServiceCheckPodStatusFlowTest() {

        FlowRunner.flowRunnerFor(ConteinerTestFlow.class)
                .step("Получаем состояние подов", flow -> {

                    //Получаем статус подов, Map будет содержать состояние двух подов из dc1 и dc2
                    Map<String, String> podsStatusMap = flow
                            .applicationPlatform()
                            .checkPodsStatus("podName");

                    //Преобразуем результат поиска в проверяемое значение, чтобы можно было применить
                    //проверки с помощью реализованных методов в библиотеке ru.sber.qa.matchers.JsonMatchers
                    DefaultValidatableJson validatableJson = new DefaultValidatableJson(new JsonPath(String
                            .valueOf(new JSONObject(podsStatusMap))));

                    //Проверяем что статус подов true, они запущены
                    for (var key : podsStatusMap.keySet()) {
                        validatableJson.should(haveJsonValueEqualTo(key, String.valueOf(true),
                                "Поды перезагружаются или не запущены"));
                    }
                })
                //Запускаем выполнение flow
                .run();
    }

    //маркируем класс как Flow, что бы его можно было запустить через FlowRunner
    static class ConteinerTestFlow implements Flow, ContainerServiceFlow {
    }
}