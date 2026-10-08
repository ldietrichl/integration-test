# tests_v9/document

Пакет содержит REST-проверки актуального контракта Splitter через эндпоинты:

- `/api/v1/splitter/mapper/config`
- `/api/v1/splitter/mapper/split`
- `/api/v1/splitter/reactions/config`
- `/api/v1/splitter/reactions/split`

## Профили ConfigMap

Актуальные примеры сохранены в ресурсах:

- `src/test/resources/splitter/tests_v9/configmaps/current/mapper-config-map.yml`
- `src/test/resources/splitter/tests_v9/configmaps/current/reaction-config-map.yml`

### MAPPER

Проверки рассчитаны на профиль:

- `traffic-based-alternative=true`;
- `MAIN` формируется процедурой `mapperFinalExp` по `actionType`;
- приоритеты задаются через `values-map`;
- публичный `ALL` содержит только реально сработавшие строки;
- строки `finalExpGroup=null` и `expGroup != finalExpGroup` удаляются из REST-ответа;
- альтернативный `MAIN` возвращает группу связи текущего объекта в `expGroup`, а реально сработавшую группу — в `finalExpGroup`;
- объект без выбранного `MAIN` выглядит как несвязанный: `objectResults` отсутствует, равен `null` или пустому массиву.

Технический блок `MAIN` с `resultExps=[]` и диагностический `ALL` для объекта без итогового эксперимента после EXPLAB-2690 не считаются допустимым REST-контрактом независимо от значения `allow-result-without-main`.

### REACTIONS

Проверки рассчитаны на правило `finalExpByLayerAndId`:

- альтернативный алгоритм не применяется;
- `MAIN` формируется только из экспериментов, у которых реально сработавшая группа связана с текущим объектом;
- если заданы слои, в `MAIN` возвращаются **все** сработавшие эксперименты максимального `layerPriority`;
- если слои не заданы, tie-break выполняется по `expId` согласно `max-id`;
- для каждого элемента `MAIN` выполняется `expGroup = finalExpGroup`;
- `expFlags` в `MAIN` отсутствует или пуст;
- объект без подходящего итогового эксперимента возвращается без `MAIN` и без диагностического `ALL`.

В текущем REACTIONS-профиле `ALL` может быть отключён. Строгая проверка `ALL` выполняется в пакете `tests_v9/strict` на профиле с `all-rule-code-exp-enabled=true`.

## Усиленные REST assertions

Общий базовый класс `AbstractSplitterV9FlowTest` проверяет:

- базовые поля ответа и версию загруженного конфига;
- уникальность `objectId`;
- обязательные поля `ResultExp`, включая `finalExpGroup`;
- строгий empty-contract без технического пустого `MAIN`;
- очистку публичного `ALL` от несработавших и альтернативных строк;
- соответствие `expGroup` и `finalExpGroup` для обычных MAPPER-результатов и REACTIONS;
- отсутствие `expFlags` в `MAIN`;
- отсутствие `isAlternative=true` в REACTIONS;
- полный набор итоговых экспериментов максимального приоритета слоя.

Поиск отсутствующего объекта или правила реализован через JUnit assertions с `expected/actual`, чтобы Gradle failure mapper не создавал вторичную ошибку `Cannot invoke Object.getClass() because obj is null`.

## Регрессия отсутствующего MAIN

Класс:

```text
ru.sber.qa.splitter.tests_v9.document.SplitterV9DocumentMapperMainPresenceAndAllCleanupFlowTest
```

Сценарий:

```text
SPL-V9-MAPPER-NO-TECHNICAL-MAIN-01
```

Исходные данные:

```text
src/test/resources/splitter/tests_v9/document/main_presence_regression/conf-main-missing-source.json
src/test/resources/splitter/tests_v9/document/main_presence_regression/req-main-missing-source.json
```

Проверяется, что при отсутствии выбранного итогового эксперимента:

- объект отсутствует либо возвращается с пустым `objectResults`;
- `MAIN` отсутствует;
- `ALL` отсутствует;
- остальные строки `ALL`, если они есть у других объектов, удовлетворяют `expGroup = finalExpGroup`.

## Связь с EXPLAB-2690

Существующие document-тесты пересмотрены под новую семантику:

- одинаковый `conditionId` у нескольких групп больше не фиксирует случайный `findFirst()` как допустимое поведение;
- REACTIONS-матрица не выбирает группу, сработавшую только для другого объекта;
- REACTIONS layer matrix ожидает все итоговые эксперименты максимального приоритета;
- no-MAIN сценарии не разрешают технический пустой `MAIN`;
- публичный REST `ALL` не используется как полный диагностический лог. Полный набор связей проверяется отдельно через Kafka/report-тесты.
