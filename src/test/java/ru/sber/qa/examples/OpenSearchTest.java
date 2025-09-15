package ru.sber.qa.examples;

import com.fasterxml.jackson.databind.JsonNode;
import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import io.qameta.allure.Epic;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.flow.Flow;
import ru.sber.qa.flow.FlowRunner;
import ru.sber.qa.flow.OpenSearchFlow;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.services.OpenSearchService;
import ru.sber.qa.services.utils.BulkOperations;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static ru.sber.qa.matchers.JsonMatchers.haveJsonKey;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValueEqualTo;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValues;

/**
 * Данный тестовый класс содержит демонстрационные тесты работы с OpenSearchService.
 * Для успешного запуска требуется указать логин, пароль от пользователя OpenSearch, и добавить сертификаты.
 * Все упоминания о документации и отсылки к ней в комментариях
 * касаются документации в головном проекте platform-v-at-framework.
 *
 * @see <a href="https://stash.delta.sbrf.ru/projects/SWATS/repos/platform-v-at-framework/browse/docs/services/openSearchService.md">документация по OpenSearchService</a>
 *
 */
@Disabled("Для запуска необходимо указать логин, пароль от пользователя OpenSearch, и добавить сертификаты.  " +
        "Инструкция указана в документации")
@Epic("opensearch")
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class OpenSearchTest {

    /**
     * Тест можно написать несколькими способами
     * Примеры:
     * <pre>
     * - {@link #checkOpenSearch()} - через step() во flow;
     * - {@link #checkOpenSearchWithRange(OpenSearchService openSearchService)} - через прямой вызов методов клиента;
     * - {@link #checkOpenSearchWithReqBuilder(Environment environment)} - через Environment.
     * </pre>
     */
    @Story("Аннотация, которая проставляет лейбл связанной истории для тест-кейса")
    @DisplayName("Имя теста. В данном тесте мы будем записывать и считывать с топика Кафки данные через step()")
    @TmsLink("Аннотация для связи теста и тест-кейса в Jira")
    @Test
    void checkOpenSearch() {
        // Создание и запуск тестового потока с помощью FlowRunner.
        // Чтобы написать тест на флоу, необходимо создать класс с флоу(в данном случае OpenSearchTestFlow.class)
        // и реализовать все необходимые интерфейсы для работы с шагами(в данном случае Flow и OpenSearchFlow)
        // В OpenSearchFlow описан метод openSearchCerts()
        // который позволяет вызвать все методы для работы с запросами(OpenSearchSteps)
        // при помощи вызова flow.openSearchCerts().search() и т.д.
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                // Описание шага теста
                .step("Проводим поиск по инструментам в OpenSearch", flow ->
                        // Вызов метода openSearchCerts() для получения клиента OpenSearch
                        flow.openSearchCerts()
                                // Вызов метода search() для выполнения запроса к OpenSearch
                                // с указанием индекса, поля, количества результатов и значения для поиска
                                .search("screener_instruments", "market.ticker", 3, "SBER")
                                // Проверка, что в результате есть ключ "hits"
                                .should(haveJsonKey("hits")))
                // Запуск тестового потока
                .run();
    }

    @Test
    void checkOpenSearchWithReqBuilder(Environment environment) {
        // Создание кастомного билдера для поискового запроса
        SearchRequest.Builder builder = new SearchRequest.Builder();
        // Получение сервиса OpenSearch из окружения
        environment.getService(OpenSearchService.class)
                // Получение клиента OpenSearch
                .getOpenSearchClient()
                // Выполнение поиска с заданными параметрами.
                // В качестве параметра передается кастомный билдер, который можно гибко настроить
                .search(builder
                        // Указание индекса для поиска
                        .index("screener_instruments")
                        // Указание количества возвращаемых результатов
                        .size(3)
                        // Указание типа запроса (в данном случае - match_all)
                        .query(q -> q
                                .matchAll(ma -> ma)
                        )
                );
    }

    @Test
    void checkOpenSearchWithMap() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Проводим поиск по инструментам в OpenSearch", flow ->
                        flow.openSearchCerts()
                                // Выполнение поиска с передачей параметров в виде Map
                                .search("screener_instruments", 3,
                                        Map.of(
                                                "market.ticker", "SBER",
                                                "market.instrumentName", "Сбербанк России"))
                                .should(haveJsonKey("hits")))
                .run();
    }

    @Test
    void checkOpenSearchWithRange(OpenSearchService openSearchService) {
        openSearchService
                .getOpenSearchClient()
                // Вызов метода searchInRange для поиска по диапазону значений
                .searchInRange("screener_instruments", "market.ticker", "0", "SBER");
    }

    @Test
    void checkExecuteSingleBulkOperation() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Выполняем одиночную bulk операцию", flow -> {
                    // Создание тестовых данных в виде Map для bulk-операции
                    Map<String, Object> document = Map.of("field1", "value2", "field2", 123);
                    // Создание bulk-операции для индексации документа
                    BulkOperation operation = BulkOperations.buildIndexOperation("screener_instrument", "doc2", document);

                    flow.openSearchCerts()
                            // Выполнение одиночной bulk-операции
                            .executeBulkOperations(operation);
                })
                .run();
    }

    @Test
    void checkExecuteBulkOperationsList() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Выполняем список bulk операций в OpenSearch", flow -> {
                    // Создание нескольких наборов тестовых данных для bulk-операций
                    Map<String, Object> document = Map.of("field", "Test value", "field3", 100);
                    Map<String, Object> document1 = Map.of("field1", "Test value1", "field4", 200);
                    // Создание списка bulk-операций
                    List<BulkOperation> operations = List.of(
                            BulkOperations.buildIndexOperation("screener_instrument", "doc3", document),
                            BulkOperations.buildIndexOperation("screener_instrument", "doc4", document1)
                    );

                    // Выполнение списка bulk-операций
                    flow.openSearchCerts()
                            .executeBulkOperations(operations);
                })
                .run();
    }

    @Test
    void checkExecuteBulkWithParameters() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Выполняем bulk операции с параметрами в OpenSearch", flow -> {
                    // Создание нескольких наборов тестовых данных для bulk-операций
                    Map<String, Object> document = Map.of("field", "Test value", "field3", 100);
                    Map<String, Object> document1 = Map.of("field1", "ValueUpdated", "field4", 200);
                    // Создание списка bulk-операций разных типов
                    List<BulkOperation> operations = List.of(
                            BulkOperations.buildCreateOperation("screener_instrument", "doc5", document),
                            BulkOperations.buildDeleteOperation("screener_instrume", "do"),
                            BulkOperations.buildIndexOperation("screener_instrument", "doc4", document1)
                    );

                    // Выполнение bulk-операций с дополнительными параметрами
                    flow.openSearchCerts()
                            .executeBulk(
                                    operations,
                                    List.of("field", "field1"), // Поля для включения в ответ
                                    List.of("field3"),      // Поля для исключения из ответа
                                    "screener_instrument",      // Имя индекса
                                    "30s",                      // Таймаут
                                    Refresh.True,               // Параметр обновления
                                    1,                          // Количество реплик
                                    null                        // Пайплайн
                            );
                })
                .run();
    }

    @Test
    void checkExecuteBulkWithBuilder() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Выполняем bulk операцию через builder в OpenSearch", flow -> {
                    // Создание билдера для bulk-запроса для более гибкой настройки параметров
                    BulkRequest.Builder builder = new BulkRequest.Builder()
                            // Добавление операций обновления
                            .operations(
                                    BulkOperations.buildUpdateOperation("screener_instrument", "doc6",
                                            Map.of("field", "Test value", "field3", 100)),
                                    BulkOperations.buildUpdateOperation("screener_instrument", "doc4",
                                            Map.of("field1", "Value     Updated", "field4", 200))
                            )
                            // Указание индекса
                            .index("screener_instrument")
                            // Установка таймаута
                            .timeout(Time.of(t -> t.time("60s")))
                            // Установка параметра обновления
                            .refresh(Refresh.WaitFor);

                    // Выполнение bulk-операции с передачей билдера
                    flow.openSearchCerts()
                            .executeBulk(builder);
                })
                .run();
    }

    @Test
    void checkGetAllHits() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Получаем все документы из индекса", flow -> {
                    flow.openSearchCerts()
                            // Получение всех документов с ограничением в 10
                            .getAllHits("screener_instrument", 10)
                            // Проверка значений полей документа с id=doc2, используя Map
                            .should(haveJsonValues(Map.of(
                                    "hits.find{it._id == 'doc2'}._source.field2", TextConditions.equalToText("123"),
                                    "hits.find{it._id == 'doc2'}._source.field1", TextConditions.equalToText("value2"))))
                            // Проверка значения поля документа с id=doc2
                            .should(haveJsonValueEqualTo("hits.find{it._id == 'doc2'}._source.field2", "123"))
                            // Фильтрация результатов по id=doc2
                            .filter("hits.findAll{it._id == 'doc2'}")
                            // Проверка значения поля после фильтрации
                            .should(haveJsonValueEqualTo("$[0].field1", "value2"));
                })
                .run();
    }

    @Test
    void checkGetHitsWithFieldFilter() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Получаем документы с фильтрацией по полям", flow -> {
                    flow.openSearchCerts()
                            // Получение документов с фильтрацией по полю field2=123
                            .getHitsWithFilter(
                                    "screener_instrument",
                                    5,
                                    "field2",
                                    "123"
                            )
                            // Проверка значения поля в результате после фильтрации
                            .should(haveJsonValueEqualTo("_source.field2", "123"))
                    ;
                })
                .run();
    }

    @Test
    void checkGetHitsWithPredicateFilter() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Получаем документы с фильтрацией через предикат", flow -> {
                    // Создание предиката для фильтрации документов по полю field2=123
                    Predicate<Hit<JsonNode>> filterCondition = s -> s
                            .source()
                            .get("field2").asText().equals("123");

                    // Получение документов с применением предиката
                    flow.openSearchCerts()
                            .getHitsWithFilter(
                                    "screener_instrument",
                                    3,
                                    filterCondition
                            )
                            // Проверка значения поля в результате
                            .should(haveJsonValueEqualTo("_source.field2", "123"));
                })
                .run();
    }

    @Test
    void checkGetHitById() {
        FlowRunner.flowRunnerFor(OpenSearchTestFlow.class)
                .step("Получаем документ по ID", flow -> {
                    flow.openSearchCerts()
                            // Получение документа по ID с дополнительной фильтрацией по полю
                            .getHitById(
                                    "screener_instrument",
                                    "field2",
                                    "123",
                                    "doc1"
                            )
                            // Проверка наличия поля field2
                            .should(haveJsonKey("field2"))
                            // Проверка значения поля field2
                            .should(haveJsonValueEqualTo("field2", "123"));
                })
                .run();
    }

    // Внутренний класс, реализующий интерфейсы Flow и OpenSearchFlow
    static class OpenSearchTestFlow implements Flow, OpenSearchFlow {
    }
}