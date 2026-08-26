# EXPLAB-2690 — REST-проверки Splitter v1.3.0

Пакет реализован в стиле проекта `integration-test`: Java 17, JUnit 5, DTO + flow, без inline JSON в тестовых методах. Конфигурации создаются через `dto.splitter.*`, вызовы выполняются через существующий `SplitterRestSteps`, assertions переиспользуют и расширяют `tests_v9.common.AbstractSplitterV9FlowTest`.

## Контракт, который фиксирует пакет

### MAPPER

1. Обычный `MAIN` использует фактически сработавшую группу:
   - `expGroup = finalExpGroup`;
   - `conditionId` и `groupResultParams` относятся к этой группе.
2. Публичный `ALL` содержит только строки реально сработавших групп:
   - нет `finalExpGroup=null`;
   - нет `expGroup != finalExpGroup`.
3. Если несколько групп связаны с объектом через один `conditionId`, результат выбирается по `leadGroup`, а не через случайный `findFirst()` из `Set`.
4. Для альтернативного `MAIN`:
   - `expGroup`, `conditionId`, `groupResultParams` относятся к группе связи текущего объекта;
   - `finalExpGroup` содержит реально сработавшую группу;
   - альтернативная строка удалена из публичного `ALL`.
5. Если итоговый эксперимент не выбран, объект выглядит как несвязанный:
   - `objectResults` отсутствует, `null` или пуст;
   - технический `MAIN.resultExps=[]` запрещён;
   - диагностический `ALL` в REST запрещён.
6. Смешанный запрос из обычного, альтернативного, no-MAIN и несвязанного объектов обрабатывается независимо по каждому `objectId`.

### REACTIONS

1. Альтернативный алгоритм не применяется.
2. `MAIN` формируется только если реально сработавшая группа связана с текущим объектом.
3. Для каждого результата `expGroup = finalExpGroup`.
4. `isAlternative=true` отсутствует во всём ответе.
5. `expFlags` в `MAIN` отсутствует или пуст — дубли `isAlternative=false` не допускаются.
6. При наличии слоёв в `MAIN` возвращаются все сработавшие эксперименты максимального `layerPriority`, без дополнительного отбора одного `expId`.

## Требуемый профиль стенда

### Общие параметры

```text
SPLITTER_API_CONFIG_LOAD=true
SPLITTER_EMPTY_OBJECTS_RESPONSE_ENABLED=true
```

Для обоих сервисов ручная загрузка `/config` должна возвращать:

```json
{"result":"LOADED"}
```

### MAPPER

```text
SPLITTER_ALL_RULE_CODE_EXP_ENABLED=true
SPLITTER_RETURN_SUPPRESSED=true
```

Правила должны соответствовать:

```text
src/test/resources/splitter/EXPLAB_2690/configmap/mapper-required.yml
```

`SPLITTER_ALLOW_RESULT_WITHOUT_MAIN` может быть `true` или `false`: после EXPLAB-2690 публичный REST-ответ в обоих режимах не должен содержать технический пустой `MAIN`. Отличия формы ответа для конкретного флага проверяются отдельным manual-пакетом `tests_v9/without_main_false`.

### REACTIONS

Правила должны соответствовать:

```text
src/test/resources/splitter/EXPLAB_2690/configmap/reactions-required.yml
```

Ключевое правило:

```text
finalExpByLayerAndId
max-layer-priority=true
max-id=false
```

ConfigMap-файлы прикладываются в Allure как предусловие, но автоматически на стенд не применяются.

## REST test plan

| ID | Приоритет | Класс / метод | Сценарий | Ожидаемый результат |
|---|---:|---|---|---|
| EXPLAB-2690-01-A/B/C | P0 | `SplitterMapperWorkedGroup2690FlowTest.mapperShouldReturnOnlyWorkedGroupAndNoTechnicalMain` | A/B/C имеют разные `conditionId` | `MAIN` и `ALL` содержат только сработавшую группу с корректными параметрами |
| EXPLAB-2690-02-NO-MAIN | P0 | тот же parameterized test | Распределение попало в свободный диапазон | Пустой `objectResults`, нет `MAIN` и `ALL` |
| EXPLAB-2690-03-A/B/C | P0 | `SplitterMapperWorkedGroup2690FlowTest.mapperShouldDeterministicallyUseWorkedGroupWhenSeveralGroupsShareCondition` | A/B/C имеют один `conditionId` | Возвращается фактически сработавшая группа и её `groupResultParams` |
| EXPLAB-2690-04-A/B | P0 | `SplitterMapperAlternative2690FlowTest.alternativeMainShouldUseObjectLinkedGroupAndExposeActualWorkedGroup` | Группы одного эксперимента связаны с разными объектами | Для второго объекта формируется альтернативный `MAIN`: linked group в `expGroup`, worked group в `finalExpGroup` |
| EXPLAB-2690-05-* | P0 | `SplitterMapperNoMain2690FlowTest.objectShouldLookUnlinkedWhenMapperCannotSelectMain` | Нет `actionType`; неизвестный `actionType`; неверный тип; пустые `resultParams` | Во всех вариантах объект возвращается без `MAIN/ALL` |
| EXPLAB-2690-06-A/B | P0 | `SplitterReactionsNoAlternative2690FlowTest.reactionsShouldNotSelectAlternativeMain` | REACTIONS-топология двух объектов и двух групп | `MAIN` только у объекта своей сработавшей группы; второй объект пуст |
| EXPLAB-2690-07 | P0 | `SplitterReactionsFinalExperiments2690FlowTest.reactionsMainShouldContainAllWorkedExperimentsOfMaximumLayerPriority` | Один exp priority=1, три exp priority=3 | `MAIN=[269072,269073,269074]`, без `expFlags` |
| EXPLAB-2690-08 | P1 | `SplitterMapperMixedObjects2690FlowTest.mapperShouldProcessNormalAlternativeNoMainAndUnlinkedObjectsIndependently` | В одном запросе четыре типа объектов | Результаты не смешиваются между объектами |

Итого основной REST-набор: **17 test invocations**.

## Пересмотр существующих тестов проекта

Под актуальный контракт скорректированы не только классы `EXPLAB_2690`, но и существующие проверки:

- `tests_v9/common/AbstractSplitterV9FlowTest` — строгий empty-contract и безопасные JUnit assertions без вторичного Gradle mapper NPE;
- `tests_v9/document/SplitterV9DocumentMapperMainPresenceAndAllCleanupFlowTest` — пустой технический `MAIN` больше не допускается;
- `tests_v9/document/SplitterV9DocumentSingleExperimentMatrixFlowTest` — один `conditionId` не фиксирует случайную группу как допустимую;
- `tests_v9/document/SplitterV9DocumentReactionsAlternativeMatrixFlowTest` — REACTIONS выбирает только реально сработавшие группы текущего объекта;
- `tests_v9/document/SplitterV9DocumentReactionsLayerMatrixFlowTest` — ожидаются все эксперименты максимального приоритета;
- `tests_v9/strict/SplitterV9StrictDocumentProfileFlowTest` — усилены MAIN и no-MAIN assertions;
- `tests_v9/sdk/SplitterV9SdkDerivedBehaviorFlowTest` — REST-часть отделена от полного Kafka-result: API `ALL` проверяется только по worked rows;
- `tests_v9/report/SplitterV9ReportKafkaFlowTest` — REST REACTIONS ожидает все итоговые эксперименты максимального `layerPriority`;
- `NewTest/document` — REST `ALL` больше не ожидает строки с `expGroup=null`;
- `NewTest/SplitterExplab2414GroupConditionBindingTest` — группы A/B проверяются отдельными управляемыми split-запросами, потому что публичный `ALL` больше не раскрывает обе связи одновременно;
- `analytictests` — альтернативные/несработавшие bindings не ожидаются в публичном `ALL`.

Полный диагностический набор связей остаётся предметом Kafka/report-тестов и не должен проверяться через очищенный REST-ответ.

## Запуск

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2690.*"
```

Только MAPPER:

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2690.SplitterMapper*"
```

Только REACTIONS:

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2690.SplitterReactions*"
```

## Ожидаемые красные сценарии на библиотеке 0.9.34

По двум фактическим прогонам стабильно подтверждаются:

- MAPPER не формирует альтернативный `MAIN` для объекта другой связанной группы;
- при одном `conditionId` выбирается случайная linked group (`B`) вместо `leadGroup`;
- REACTIONS формирует семантическую альтернативу (`expGroup != finalExpGroup`);
- REACTIONS возвращает один минимальный `expId` вместо всех экспериментов максимального `layerPriority`;
- REACTIONS переносит и дублирует `isAlternative=false` в `MAIN.expFlags`.
