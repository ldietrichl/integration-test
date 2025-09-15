package ru.sber.qa.examples;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.flow.KafkaFlow;
import ru.sber.qa.flow.steps.KafkaConsumerSteps;
import ru.sber.qa.flow.steps.KafkaProducerSteps;
import ru.sber.qa.matchers.KafkaMatchers;
import ru.sber.qa.matchers.conditions.NumberConditions;
import ru.sber.qa.services.kafka.KafkaConsumerClient;
import ru.sber.qa.services.kafka.KafkaProducerClient;
import ru.sber.qa.services.kafka.KafkaService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.qameta.allure.Allure.step;
import static ru.sber.qa.matchers.conditions.TextConditions.equalToText;

/**
 * Данный тестовый класс содержит демонстрационные тесты работы с KafkaService.
 * Запуск тестов через TestContainer.
 * Для успешного запуска требуется установленный DockerClient
 *
 * @see <a href="https://sberusersoft.sigma.sbrf.ru/#program/s/190">DockerClient в SberUser Soft</a>
 *
 * Все упоминания о документации и отсылки к ней в комментариях
 * касаются документации в головном проекте platform-v-at-framework.
 *
 * @see <a href="https://stash.delta.sbrf.ru/projects/SWATS/repos/platform-v-at-framework/browse/docs/services/kafkaService.md">документация по KafkaService</a>
 *
 */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
@Testcontainers
public class KafkaTests {

    /**
     * Для тестирования, отладки и демонстрации возможностей сервиса используются контейнеры.
     * Предполагается, что в вашей реализации тестов для Кафки, данное решение не понадобится,
     * так как ваши тесты будут проверять продукт на бою, на одном из тестовых стендов.
     * Настройки контейнера ниже требуются только для демонстрации возможных сценариев использования KafkaService.
     * В случае, если вам понадобится это же решение, следует обратиться к документации по тест-контейнерам
     * в нашем головном проекте.
     *
     * @see <a href="https://stash.delta.sbrf.ru/projects/SWATS/repos/platform-v-at-framework/browse/docs/services/testContainers.md">документация по Testcontainers</a>
     *
     */
    @Container
    KafkaContainer container = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
            .withExposedPorts(9092)
            .withCreateContainerCmdModifier(cmd -> cmd.withHostConfig(
                    new HostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(9092), new ExposedPort(9092)))))
            ;

    /**
     * Тест можно написать несколькими способами
     * Примеры:
     * <pre>
     * - {@link #withStepsAndMatchersTest(KafkaService kafkaService)} - через step();
     * - {@link #withManyConsumersGroupsTest(KafkaService kafkaService)} - через прямой вызов методов клиента;
     * - {@link #withFlowRunnerTest(Environment environment)} - через Flow.
     * </pre>
     */
    @Story("Аннотация, которая проставляет лейбл связанной истории для тест-кейса")
    @DisplayName("Имя теста. В данном тесте мы будем записывать и считывать с топика Кафки данные через step()")
    @TmsLink("Аннотация для связи теста и тест-кейса в Jira")
    @Test
    void withStepsAndMatchersTest(KafkaService kafkaService) {
        // Инициализируем сервис, вызывая клиентов потребителя и поставщика.
        // В качестве аргумента передаем имя клиента из пропертей в проекте.
        // В документации это обозначено как {name_of_client}, -
        // префикс, который определяет параметры для клиента, для примера перейдите в .properties.
        // kafka_consumer.consumer2.group.id=2 - consumer2 это префикс.
        KafkaConsumerClient<String, String> consumerClient = kafkaService.consumerClient("consumer");
        KafkaProducerClient<String, String> producerClient = kafkaService.producerClient("producer");

        // Далее вызываем метод записи данных в топик, передавая три аргумента на вход.
        // Передаем TestTopic как имя нужного нам топика, ключ и значение.
        // Для тестирования клиента кафки наполним топик тремя записями.
        producerClient.sendRecord("TestTopic", "key 1", "Value 1");
        producerClient.sendRecord("TestTopic", "key 1", "Value 2");
        producerClient.sendRecord("TestTopic", "key 1", "Value 3");

        step("Подписываемся на нужный нам топик", () ->
        {
            // Далее, из степа вызываем метод консьюмера для того,
            // чтобы подписаться на нужный нам топик.
            // Важно учитывать, что если топик еще не существует, то он будет создан в момент подписки.
            // Так что, писать точное имя топика очень важно.
            consumerClient.subscribe("TestTopic");
        });

        step("Считываемы данные и выполняем проверки над ними", () ->
        {
            // !важно! в .should() для KafkaConsumerMatcher и ValidatableConsumerRecordMatcher
            // выполняются только KafkaMatchers.
            // Для просмотра доступных мэтчеров перейти в ru.sber.qa.matchers.KafkaMatchers
            consumerClient
                    // Проверим данные в клиенте консьюмера.
                    // Вызываем метод .should() для клиента и на вход передаем проверки
                    // KafkaMatchers для типа KafkaConsumerMatcher.
                    // Например, проверяем, что количество считанных записей потребителем из топика больше одной.
                    // !ВАЖНО! при вызове .should() под капотом будет вызван метод poll()
                    // и все доступные записи в топике будут считаны и добавлены в список records.
                    .should(KafkaMatchers.haveRecordsCount(NumberConditions.greaterThan(1), record -> true))
                    // После чтения и проверки записи из клиента можно вызвать метод для получения конкретной записи.
                    // Получаем первую запись из списка records.
                    .firstRecord(record -> true)
                    // Затем, когда мы получили запись будет вызван .should() из ValidatableConsumerRecord
                    // В этом случае будут выполняться проверки для типа ValidatableConsumerRecordMatcher.
                    .should(KafkaMatchers.haveStringRecordValueEqualTo("Value 1"));

            consumerClient
                    .firstRecord(record -> true)
                    // Метод .should() из ValidatableConsumerRecord перегружен и может принимать на вход список проверок
                    .should(List.of(
                                    KafkaMatchers.haveStringRecordValueEqualTo("Value 1"),
                                    KafkaMatchers.haveStringRecordValueContains("Value 1"),
                                    KafkaMatchers.haveStringRecordValue(equalToText("Value 1"))
                            )
                    );
        });
    }

    @Test
    void withManyProducersClientsTest(KafkaService kafkaService) {
        // В этом примере реализованы проверки для нескольких продюсеров
        // Инициализируем сервис, вызывая клиента поставщика и отправляем запись в топик
        kafkaService.producerClient("producer")
                .sendRecord("TestTopic", "K1", "test Value");
        // Делаем запись в тот же топик уже другим продюсером
        kafkaService.producerClient("producer1")
                .sendRecord("TestTopic", "K1", "test Value");
        // Делаем запись в новый топик третьим продюсером
        kafkaService.producerClient("producer2")
                .sendRecord("TestTopicNew", "K1", "test Value");

        // Далее вызываем клиент потребителя
        kafkaService.consumerClient("consumer")
                // Подписываемся
                .subscribe("TestTopic")
                // Вызываем метод, который считает все записи из топика,
                // на который мы подписались и добавит в список records
                .poll()
                // Подписываемся на еще один топик
                .subscribe("TestTopicNew")
                // И считываем все записи из него
                // Если вы хотите подряд сделать несколько подписаний,
                // то нужно запускать считывание после каждого из подписаний.
                .poll()
                // Вызываем .should() из клиента для проверки записей. Например, проверка на размер списка записей.
                .should(KafkaMatchers.haveRecordsCount(3))
                .unsubscribe();
    }

    @Test
    void withManyConsumersGroupsTest(KafkaService kafkaService) {
        // В этом примере реализованы проверки для нескольких групп консьюмеров
        // Инициализируем сервис, вызывая клиента поставщика и отправляем запись в топик
        kafkaService.producerClient("producer")
                .sendRecord("TestTopic", "K1", "test Value");

        // Вызываем клиент потребителя consumer, подписываемся и выполняем проверки
        kafkaService.consumerClient("consumer")
                .subscribe("TestTopic")
                .poll()
                .should(KafkaMatchers.haveRecordsCount(1))
                // !ВАЖНО! если оба потребителя, например consumer и consumer1 находятся в одной группе потребителей,
                // то после работы первого потребителя, необходимо отписаться от топика,
                // чтобы с этим же топиком мог работать другой потребитель.
                // Группа потребителей или консьюмеров определяется параметром из пропертей kafka_consumer.all.group.id
                // Для consumer и consumer1 этот параметр имеет одинаковое значение 1 в пропертях.
                .unsubscribe();

        // Вызываем клиент другого потребителя consumer1, подписываемся на тот же топик, что читает первый потребитель
        // и выполняем проверки
        kafkaService.consumerClient("consumer1")
                .subscribe("TestTopic")
                .poll()
                // Проверки пройдут успешно, если предыдущий потребитель отписался от топика
                .should(KafkaMatchers.haveRecordsCount(1));

        // Напротив, если консьюмеры находятся в разных группах, то отписываться перед чтением необязательно, например:
        // Потребитель consumer2 находится в другой группе, его параметр kafka_consumer.all.group.id равен 2
        kafkaService.consumerClient("consumer2")
                // подписываемся на уже существующий топик, на который подписан consumer1
                .subscribe("TestTopic")
                .poll()
                // Проверки пройдут успешно, даже если предыдущий потребитель consumer1 не отписался от топика,
                // так как и он, и consumer2 находятся в разных группах
                .should(KafkaMatchers.haveRecordsCount(1));
    }

    @Test
    void withFlowRunnerTest(Environment environment) {
        // Чтобы написать тест на флоу, необходимо создать класс с флоу(в данном случае KafkaTestFlow.class) и реализовать
        // все необходимые интерфейсы для работы с шагами(в данном случае Flow и KafkaFlow)
        // В KafkaFlow описаны методы kafkaProducer() и kafkaConsumer().
        // Метод kafkaConsumer() перегружен. Его можно вызвать как без таймаута, так и с ним.
        // которые позволяют вызвать все методы для работы с запросами(KafkaConsumerSteps или KafkaProducerSteps)
        // при помощи вызова flow.kafkaConsumer().subscribe() и т.д.
        FlowRunner.flowRunnerFor(KafkaTestFlow.class)
                // описываем step и вызываем flow
                .step("Подписываемся и отправляем запрос на топик TestTopic", flow -> {
                    // в нашем случаем flow.kafkaConsumer() возвращает KafkaConsumerSteps,
                    // который позволяет вызвать все методы для работы с запросами(KafkaConsumerSteps)
                    flow.kafkaConsumer("consumer")
                            .subscribe("TestTopic");
                    // и через flow.kafkaProducer() вызывается KafkaProducerSteps
                    flow.kafkaProducer("producer")
                            .sendRecord("TestTopic", "key 1", "Value 1");
                })

                .step("Вызываем проверки через .should() и проверяем записанные данные", flow -> {
                    KafkaConsumerSteps<Object, String> myKafka = flow.kafkaConsumer("consumer");
                    myKafka.should(KafkaMatchers.haveRecordsCount(1, Duration.ofSeconds(100L)))
                            .should(KafkaMatchers.haveRecordsCount(1, record -> "key 1".equals(record.key()) && "Value 1".equals(record.value())))
                            .singleRecord(record -> true)
                            .should(KafkaMatchers.haveStringRecordValueEqualTo("Value 1"));
                })
                .run();
    }

    @Test
    void sendSingleMessageWithFlowTest() {
        KafkaTestFlow flow = new KafkaTestFlow();
        KafkaConsumerSteps<String, String> consumer = flow.kafkaConsumer("consumer");
        KafkaProducerSteps<String, String> producer = flow.kafkaProducer("producer");

        // Создаем список с типом Header для дальнейшего создания объекта записи ProducerRecord
        List<Header> headers = new ArrayList<>();
        // !ВАЖНО! В список заголовков можно добавить заголовок только типа RecordHeader.
        headers.add(new RecordHeader("requestId", "123".getBytes()));
        // Собираем объект типа ProducerRecord.
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>("TestTopic", 0, "key 1", "Value 1", headers);

        // Как уже было сказано в одном из предыдущих примеров, мы можем сначала вызвать метод подписания на топик,
        // которого не существует и тогда будет создан топик с таким же наименованием, что в аргументе метода подписания.
        consumer.subscribe("TestTopic");
        // Выполняем метод отправки записи в топик, передавая на вход, в качестве аргумента созданный ранее объект producerRecord
        // Для выполнения запроса мы вызываем перегруженный метод sendRecord(), который может принимать на вход объекты
        // типа ProducerRecord, если есть необходимость подготовить запись самостоятельно
        producer.sendRecord(producerRecord);
        // Затем, например, мы можем выполнить проверки на размер всего списка записей
        consumer.should(KafkaMatchers.haveRecordsCount(1))
                // и проверить, что при определенном фильтре,
                // количество подходящих под него записей будет равно определенному числу.
                .should(KafkaMatchers.haveRecordsCount(1, record -> "key 1".equals(record.key()) && "Value 1".equals(record.value())));
    }

    static class KafkaTestFlow implements Flow, KafkaFlow {
    }

}
