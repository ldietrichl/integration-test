# Analytic splitter tests by analyst blocks

Пакет `ru.sber.qa.splitter.analytictests` разложен по подпакетам, соответствующим колонке аналитика `Блок из текущих тесткейсов`.

Эти тесты являются REST-only покрытием. Kafka, мониторинг, текстовые логи, performance-сравнения и сценарии, требующие runtime-переключения ConfigMap, остаются отдельными TODO-направлениями вне REST-only harness. Реализуемые через REST TODO по базовой матрице operatorCode, share-граням и возврату filtered=true объекта включены в активный набор.

## Структура

| Папка | Блок аналитика |
|---|---|
| `common` | Общие helper-методы REST-only analytic tests |
| `config_lifecycle` | Загрузка и валидация split config |
| `split_api_contract` | Split API / базовая логика ответа |
| `link_rules_operators` | Привязка объектов / AND-OR rules + операторы правил привязки |
| `group_distribution` | Вычисление групп / диапазоны share |
| `all_result_condition_binding` | ALL result / все связанные эксперименты + EXPLAB-2414 / привязка condition к группе |
| `precalc` | Предрасчет связей |
| `main_priority` | Выбор MAIN / приоритет actionType |
| `alternatives` | Альтернативы / alternative markup |
| `filtering` | Фильтрация / suppression / filtered flag |
| `layers` | Слои и layerPriority |

## ConfigMap

Тесты прикладывают эталонную ConfigMap из `src/test/resources/splitter/configmap`. Ресурсы не применяют ConfigMap на стенд автоматически, а фиксируют предусловие и контракт ожиданий.


## Patch: manual tags и дополнительное REST-only покрытие

В текущем наборе `AN-RULES-04` оставлен как активный диагностический сценарий: он продолжает подсвечивать расхождение по `more`, `less`, `is_null`.

Дополнительно добавлены REST-only сценарии:
- `AN-RULES-06` — LIKE-семейство операторов для `STRING`.
- `AN-GROUP-06` — одна группа с двумя соседними интервалами и проверкой внутренней границы.
- `AN-ALL-07` — одна группа с несколькими matched conditions выбирает минимальный `conditionId`.
- `AN-ALL-08` — несколько групп с несколькими matched conditions выбирают `conditionId` внутри выбранной группы.

Manual-сценарии помечены `@ManualTest`. Это Kafka, рестарт pod/чистый state, смена ConfigMap и performance harness.
