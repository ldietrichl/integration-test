# Дубли параметров в conditions

Пакет проверяет, что splitter допускает повторяющиеся `paramCode` в `objectSelectConditions.rules` и вычисляет каждое выражение независимо.

## Нумерация сценариев

Нумерация приведена к сквозному виду без пропусков: `SPL-01` ... `SPL-25`.

`caseId`, `splittingId`, `salt` и номер сценария в `@ParameterizedTest(name = "{0}")` совпадают. `expId` также синхронизирован с номером сценария по шаблону `2633NN`, где `NN` — номер сценария.

## Тестовый план и соответствие классам

| ID | Класс | Сценарий | Ожидание |
|---|---|---|---|
| SPL-01 | `SplitterDuplicateConditionsSmokeFlowTest` | `[[param1 = 2, param2 = FFF]]` | true |
| SPL-02 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[param1 = 2, param1 = 2]]` | true |
| SPL-03 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[param1 = 2, param1 = 4]]` | false |
| SPL-04 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[param1 = 2, param1 = 3, param2 = FFF]]` | false |
| SPL-05 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[param1 = 2, param1 in [2,3], param1 >= 1]]` | true |
| SPL-06 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[param1 = 2, param1 in [3,4], param1 >= 1]]` | false |
| SPL-07 | `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | `[[paramBool = true, paramBool is_not_null]]` | true |
| SPL-08 | `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | `[[param1 = 2], [param1 = 2]]` | true |
| SPL-09 | `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | `[[param1 = 2], [param1 = 4]]` | true |
| SPL-10 | `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | `[[param1 = 2, param2 = FFF], [param1 = 3, param6 = uuu]]` | true |
| SPL-11 | `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | `[[param1 = 1], [param1 = 2], [param1 = 3]]` | true |
| SPL-12 | `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | `[[param1 = 1], [param1 = 2], [param1 = 3]]` | false |
| SPL-13 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 >= 2, param1 <= 5]]` | true |
| SPL-14 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 > 2, param1 < 5]]` | false |
| SPL-15 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 in [2,3], param1 not_in [4,5]]]` | true |
| SPL-16 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 in [2,3], param1 not_in [2,5]]]` | false |
| SPL-17 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramStr like ABC, paramStr like_any [ABC,XYZ]]]` | true |
| SPL-18 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramStr like ABC, paramStr not_like ABC]]` | false |
| SPL-19 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 is_not_null, param1 = 2]]` | true |
| SPL-20 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 is_null, param1 = 2]]` | false |
| SPL-21 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramDate >= 2026-01-01, paramDate <= 2026-12-31]]` | true |
| SPL-22 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramNumber > 10.5, paramNumber < 20.5]]` | true |
| SPL-23 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[param1 not_equal 4, param1 = 2]]` | true |
| SPL-24 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramStr like ABC, paramStr not_like_any [XYZ,QQQ]]]` | true |
| SPL-25 | `SplitterDuplicateOperatorsParameterizedFlowTest` | `[[paramDateTime >= 2026-06-02 00:00:00.000000, paramDateTime <= 2026-06-02 23:59:59.000000]]` | true |

## Что входит в основной набор

| Класс | Назначение |
|---|---|
| `SplitterDuplicateConditionsSmokeFlowTest` | sanity-check без дублей: подтверждает, что стенд, config load и split базово работают |
| `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | параметризованная матрица дублей внутри одной AND-группы |
| `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | параметризованная матрица повторов одного `paramCode` в разных OR-группах |
| `SplitterDuplicateOperatorsParameterizedFlowTest` | параметризованная матрица дублей одного `paramCode` с разными операторами и типами данных, включая `not_equal`, `not_like_any`, `DATETIME` |

`REQUEST_PARAMS`-сценарии удалены из автоматического набора, потому что на стенде с включенным предрасчетом загрузка таких конфигов блокируется статусом `REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED`. Это отдельное предусловие стенда, не проверка фикса по задаче.

## Текущее покрытие автоматического набора

После исключения `REQUEST_PARAMS` автоматический набор покрывает 25 сценариев:

| Класс | Проверок |
|---|---:|
| `SplitterDuplicateConditionsSmokeFlowTest` | 1 |
| `SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest` | 6 |
| `SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest` | 5 |
| `SplitterDuplicateOperatorsParameterizedFlowTest` | 13 |

Покрытие по `SPLITTING_OBJECTS` доведено до 100% по запланированной матрице:

- операторы: `equal`, `not_equal`, `more`, `less`, `more_equal`, `less_equal`, `in`, `not_in`, `is_null`, `is_not_null`, `like`, `not_like`, `like_any`, `not_like_any`;
- типы данных: `INTEGER`, `NUMBER`, `STRING`, `DATE`, `DATETIME`, `BOOLEAN`;
- логика групп: дубли внутри одной AND-группы и повторы одного `paramCode` в разных OR-группах;
- ожидаемые исходы: true-комбинации и false-комбинации.

## Команда запуска

```bash
./gradlew test --tests "ru.sber.qa.splitter.explab2633.*"
```

Точечный запуск без smoke:

```bash
./gradlew test --tests "ru.sber.qa.splitter.explab2633.SplitterDuplicateParamsInsideAndGroupParameterizedFlowTest" \
               --tests "ru.sber.qa.splitter.explab2633.SplitterDuplicateParamsAcrossOrGroupsParameterizedFlowTest" \
               --tests "ru.sber.qa.splitter.explab2633.SplitterDuplicateOperatorsParameterizedFlowTest"
```

## Ожидание до установки фикса

До установки фикса сценарии с дублем внутри одной AND-группы должны падать на `loadConfigStep` с `LOAD_ERROR Duplicate key <paramCode>`. Это корректное красное состояние регрессионных тестов.

После установки фикса ожидается:

- config с дублями внутри AND-группы загружается как `LOADED`;
- split возвращает `200 OK`;
- true-комбинации возвращают объект с ожидаемым `MAIN`/`ALL`, `expId`, `expGroup=A`, `conditionId=1`, `actionType=0`;
- false-комбинации не привязывают объект к эксперименту, но не приводят к технической ошибке.

## Усиленные проверки результата

Для всех сценариев после успешного `split` дополнительно проверяется:

- `splittingConfigVersion` совпадает с версией только что загруженного config;
- в ответе ровно один элемент `splittingResults`, так как в запросе всегда один объект;
- для positive-сценариев объект содержит `MAIN` и `ALL`, `expId`, `expGroup=A`, `conditionId=1`, `actionType=0`, `filtered=false`;
- для positive-сценариев тестовый `expId` встречается ровно в двух ожидаемых местах: `MAIN` и `ALL`;
- для negative-сценариев `objectResults` строго пустой, а тестовый `expId` отсутствует во всех результатах объекта.


## Отображение ID в JUnit/Allure

ID сценариев также указаны в `@DisplayName`: `SPL-01` для smoke и диапазоны `SPL-02..SPL-07`, `SPL-08..SPL-12`, `SPL-13..SPL-25` для параметризованных классов. Точный ID каждого параметризованного сценария отображается через `@ParameterizedTest(name = "{0}")`.
