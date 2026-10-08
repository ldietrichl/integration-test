# EXPLAB-2690 — REST-проверки сработавших групп

Пакет реализован в стиле проекта `integration-test`: Java 17, JUnit 5, DTO + flow, без inline JSON в тестовых методах. Конфигурации создаются через `dto.splitter.*`, вызовы выполняются через существующий `SplitterRestSteps`, общие шаги находятся в `steps.flow.splitter.workedgroup`, проверки — в `util.splittercheck`, параметры — в `support.splitter.cases`.

Контракт no-MAIN уточнён АК и исправлен 07.10.2026. [Тест-план](TEST_PLAN_JIRA.txt) сохраняет исторические статусы 49 выполнений: 38 passed, 11 failed; исправление ожиданий не переписывает результаты прогона. Пустой MAIN при allow=true больше не является кандидатом в дефект сервиса. Исправление профиля T07 подтверждено повторным прогоном 06.10.2026. [Исторические доказательства](TEST_PLAN_AND_BUGS_JIRA.txt) сохранены отдельно от текущего контракта.

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
5. Если итоговый эксперимент не выбран, форма ответа зависит от профиля:
   - при `allow-result-without-main=false` ожидается пустой/отсутствующий `objectResults`;
   - при `true` сохраняется `ALL` с фактически сработавшими связанными группами;
   - если таких групп нет, `ALL` отсутствует;
   - при `true` связанный объект без итогового эксперимента содержит ровно один `MAIN` с `resultExps=[]`;
   - несвязанный объект остаётся с пустым/отсутствующим `objectResults` независимо от флага;
   - `objectFlags` пусты/отсутствуют либо содержат только `filtered=false`.
6. Смешанный запрос из обычного, альтернативного, no-MAIN и несвязанного объектов обрабатывается независимо по каждому `objectId`.

### REACTIONS

1. Альтернативный алгоритм не применяется.
2. `MAIN` формируется только если реально сработавшая группа связана с текущим объектом.
3. Для каждого результата `expGroup = finalExpGroup`.
4. `isAlternative=true` отсутствует во всём ответе.
5. `expFlags` в `MAIN` отсутствует или пуст — дубли `isAlternative=false` не допускаются.
6. T07 при `max-layer-priority=true`, `max-id=false` проверяет один эксперимент максимального `layerPriority` с минимальным `expId`. Подготовка REACTIONS исправлена и подтверждена прогоном 06.10.2026.

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

`SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=true/false` проверяется базовыми и Denied-классами задачи `explab2690Coverage`. При true сохраняется ALL сработавших связанных групп; запрет технического пустого MAIN пока требует согласования противоречивых требований.

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

При запуске `explab2690Coverage` общий Fabric8-механизм подготавливает ConfigMap, управляет рестартом и восстановлением. Приложенный образец YAML не заменяет снимок применённого состояния. Известное исключение T07: сейчас подготовлен MAPPER вместо REACTIONS; см. BUG-2690-TEST-01 в Jira-плане.

## REST test plan

| ID | Приоритет | Класс / метод | Сценарий | Ожидаемый результат |
|---|---:|---|---|---|
| EXPLAB-2690-01-A/B/C | P0 | `SplitterMapperWorkedGroup2690FlowTest.mapperShouldReturnOnlyWorkedGroupAndNoTechnicalMain` | A/B/C имеют разные `conditionId` | `MAIN` и `ALL` содержат только сработавшую группу с корректными параметрами |
| EXPLAB-2690-02-NO-MAIN | P0 | тот же parameterized test | Распределение попало в свободный диапазон | Пустой `objectResults`, нет `MAIN` и `ALL` |
| EXPLAB-2690-03-A/B/C | P0 | `SplitterMapperWorkedGroup2690FlowTest.mapperShouldDeterministicallyUseWorkedGroupWhenSeveralGroupsShareCondition` | A/B/C имеют один `conditionId` | Возвращается фактически сработавшая группа и её `groupResultParams` |
| EXPLAB-2690-04-A/B | P0 | `SplitterMapperAlternative2690FlowTest.alternativeMainShouldUseObjectLinkedGroupAndExposeActualWorkedGroup` | Группы одного эксперимента связаны с разными объектами | Для второго объекта формируется альтернативный `MAIN`: linked group в `expGroup`, worked group в `finalExpGroup` |
| EXPLAB-2690-05-* | P0 | `SplitterMapperNoMain2690FlowTest.objectShouldLookUnlinkedWhenMapperCannotSelectMain` | Нет `actionType`; неизвестный `actionType`; неверный тип; пустые `resultParams` | Нет `MAIN`; при allow=true точный `ALL` группы A, при false пустой результат |
| EXPLAB-2690-06-A/B | P0 | `SplitterReactionsNoAlternative2690FlowTest.reactionsShouldNotSelectAlternativeMain` | REACTIONS-топология двух объектов и двух групп | `MAIN` только у объекта своей сработавшей группы; второй объект пуст |
| EXPLAB-2690-07 | P0 | `SplitterReactionsFinalExperiments2690FlowTest.reactionsMainShouldContainSelectedExperimentOfMaximumLayerPriority` | Один exp priority=1, три exp priority=3 | `MAIN=[269072]`; подготовка REACTIONS требует исправления |
| EXPLAB-2690-08 | P1 | `SplitterMapperMixedObjects2690FlowTest.mapperShouldProcessNormalAlternativeNoMainAndUnlinkedObjectsIndependently` | В одном запросе четыре типа объектов | Результаты не смешиваются между объектами |

Актуальная задача `explab2690Coverage`: **49 выполнений**, включая Denied-профили, T08-CURRENT и матрицу REACTIONS `groups`. Полная параметризация и результаты — в Jira-плане выше; таблица здесь представляет семейства исходных сценариев.

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

Полный управляемый набор корпоративной модульной сборки:

```powershell
.\gradlew.bat explab2690Coverage
```

Прямые фильтры ниже сами по себе не гарантируют включение управляемых Denied-профилей.

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

## Исторические наблюдения на библиотеке 0.9.34 (не открытые баги текущего прогона)

В прежних прогонах фиксировались следующие симптомы; этот список не является заключением по версии 0.12.3:

- MAPPER не формирует альтернативный `MAIN` для объекта другой связанной группы;
- при одном `conditionId` выбирается случайная linked group (`B`) вместо `leadGroup`;
- REACTIONS формирует семантическую альтернативу (`expGroup != finalExpGroup`);
- REACTIONS возвращает один минимальный `expId` вместо всех экспериментов максимального `layerPriority`;
- REACTIONS переносит и дублирует `isAlternative=false` в `MAIN.expFlags`.

## Согласованный no-MAIN контракт, 07.10.2026

В T05 (четыре варианта) и T08 (два смешанных запроса) неверный/отсутствующий
actionType мешает выбору MAIN, но не отменяет связь со сработавшей группой A.
При allow=true проверяем точный ALL: expId, conditionId, expGroup/finalExpGroup,
параметры группы, соль, слой, распределение и isAlternative=false. Лишние строки,
дубли отклоняются. При allow=true дополнительно обязателен MAIN с resultExps=[].
Denied-профиль сохраняет пустой результат объекта без MAIN и ALL.

Основание: явное уточнение АК о значении флага разрешает прежнее противоречие
спецификации v16 (стр.91 против стр.96/425). В реальном JSON splittingResults —
верхний массив объектов, objectResults — правила отдельного объекта. При false
сохраняется объект с его objectId, а не удаляется весь верхний массив результатов.
Прежние 11 падений по запрету технического MAIN являются ошибкой ожиданий тестов.
После исправления необходим новый прогон: ранее недостигнутые проверки не считаются
выполненными. Контракт Kafka/КАП этим уточнением публичного ответа не изменяется.

Изменены существующие WorkedGroupAssertions, MapperWorkedGroupSteps и проверки
extension. Общий Workload lifecycle, ConfigMap/lease/rollback и Gradle не менялись.
ID, имена методов и параметры функциональных сценариев сохранены.

Локальная проверка 07.10.2026: Java 17, компиляция изменённых компонентов и
сценариев 2690/2984/extension на доступном корпоративном classpath; 34 теста
успешны (включая архитектуру и повторную проверку сохранённых ответов),
108 наборов extension и 617 отклонённых мутаций. Исправленный контракт no-MAIN
принят для 11 из 11 сохранённых ответов DEV. Это проверка ответов, не новый
стендовый прогон и не подтверждение ранее недостигнутых assertions.
Полная корпоративная сборка с Nexus и повторный DEV ещё требуются.

## Исправление подготовки T07 (06.10.2026)

В `ReactionsFinalExperimentsSteps` явно выбран профиль REACTIONS. Runtime metadata,
ConfigMap, readiness и логи pod используют ту же точку, что бизнес-запросы.
Это исправление локально скомпилировано и проверено; повторного прогона на стенде
ещё не было. Описанный в Jira-документах прогон 05.10.2026 остаётся историческим
свидетельством дефекта до исправления. Бизнес-проверки пустого MAIN не изменялись.

Результаты всех заявок пишутся в общий `build/allure-results`; детали отдельных
bypass/регрессионных каталогов и централизованных Gradle-модулей TestOps — в
`docs/infrastructure/SPLITTER_RELIABILITY.md`, раздел v4.

Подтверждение 06.10.2026: повторный T07 прошёл; runtime metadata, снимки ConfigMap
и COLLECTED pod-log относятся к REACTIONS. Дефект выбора сервиса исправлен на
стенде. Статусы остальных 48 выполнений не изменились. Подробнее:
[разбор нового прогона](../RUN_REVIEW_20261006_V4.txt).
