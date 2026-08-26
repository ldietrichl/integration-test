# EXPLAB-2729 — `splitting-objects-ids`

Автотесты метода:

```text
POST /api/v2/data-operator/splitting-objects-ids
```

## Архитектура

Реализация выполнена в текущем проектном стиле `FlowRunner + REST steps + DTO`:

- DTO запроса/ответа: `dto.dataoperator.*`;
- фабрика запросов: `request.dataoperator.DataOperatorTestDataFactory`;
- REST steps: `steps.rest.dataoperator.DataOperatorSteps`;
- assertions/response mapping: `util.dataoperator.*`;
- тесты временно размещены в `ru.sber.qa.splitter.EXPLAB_2729`.

Allure `functionalArea` для этого пакета принудительно определяется как `data-operator-service`, несмотря на временное размещение внутри пакета `splitter`.

## Классы

- `DataOperatorObjectIds2729FunctionalFlowTest` — контракт, пустые rules, parent, сортировка, уникальность, лимит, повторяемость;
- `DataOperatorObjectIds2729RulesFlowTest` — `AND/OR`, дедупликация и матрица операторов;
- `DataOperatorObjectIds2729ValidationFlowTest` — контракт `400` и структура ошибки.

## Тестовые данные

Тесты не изменяют Ignite. В качестве контрольного источника для `parent=false` используется действующий метод:

```text
POST /api/v2/data-operator/splitting-objects
returnIds=true
```

Rule-сценарии динамически выбирают существующий отображаемый параметр из первой страницы объектов. Если в Ignite нет подходящих данных для STRING или числового сценария, соответствующий параметризованный тест будет `skipped` с явным описанием предусловия.

Точка сплиттования по умолчанию — `MAPPER`. Ее можно переопределить без изменения кода:

```text
-DdataOperator.splittingPoint=<CODE>
```

## Зафиксированные ожидания спецификации

- `ids` присутствует всегда, включая пустой результат;
- тип элементов `ids` — JSON string;
- идентификаторы уникальны;
- сортировка выполняется по возрастанию строкового значения ID;
- возвращается не более 20 000 элементов;
- условия внутри группы объединяются по `AND`, группы — по `OR`;
- `parent=null` или отсутствие поля эквивалентно `parent=false`;
- ошибки валидации возвращают `400` и DTO с обязательными `id` UUID и `message`.

Сценарии `422` и `500` не автоматизированы, поскольку требуют управляемого повреждения справочника/кэша либо fault injection Ignite.
