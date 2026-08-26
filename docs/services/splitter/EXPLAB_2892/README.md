# EXPLAB-2892 - REACTIONS final group selection

Пакет фиксирует баг из спецификации `jira/EXPLAB-2892`: при одном experiment, одном conditionId и трех группах A/B/C итоговый REACTIONS-результат не должен брать первую связанную группу как `expGroup`.

Сценарии используют REST-загрузку конфигурации через `/api/v1/splitter/reactions/config`, затем проверяют REST split и Kafka/report.

## Покрытие

| ID | Сценарий | Ожидаемый результат |
|---|---|---|
| EXPLAB-2892-01-B/C | REST split для objectId=1, который связан со всеми группами, при срабатывании B или C | В `MAIN` и публичном `ALL`, если он включен, `expGroup = finalExpGroup = workedGroup`, `result` соответствует сработавшей группе; `isAlternative=true` отсутствует |
| EXPLAB-2892-02-B | Kafka/report по примеру из PDF: `splittingId=1129464980047006855`, `spreadValue=1928`, сработала B | В `MAIN` одна запись `B/B`; если `ALL` включен стендовой ConfigMap, проверяются `A/B`, `B/B`, `C/B`; объекты 2-4 остаются без результатов |

## Запуск

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2892.SplitterReactionsFinalGroup2892FlowTest"
```
