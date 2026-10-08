# Tests-v9: configCommId in [] + configCommId in [1]

Пакет проверяет MAPPER-сценарий с двумя экспериментами в одном config по примерам из постановки:

1. `expId=1` — `objectSelectConditions[0].rules=[[configCommId in []]]`, группа `A`, `share=0..10000`, `actionType=0`.
2. `expId=2` — `objectSelectConditions[0].rules=[[configCommId in ["1"]]]`, группа `A`, `share=0..10000`, `actionType=0`.

После загрузки config выполняются ровно два split-запроса:

1. `configCommId=2` — ни один эксперимент не должен сработать, так как `values=[]` не матчится, а `values=["1"]` не подходит.
2. `configCommId=1` — должен сработать только `expId=2`.

Проверки:

- каждый split возвращает `HTTP 200`;
- `requestId`, `splittingId`, `splittingConfigVersion`, `responseId`, `splittingResults` соответствуют базовому REST-контракту;
- первый split возвращает объект без результата: объект отсутствует либо `objectResults` пуст; технический `MAIN.resultExps=[]` и диагностический `ALL` не допускаются;
- второй split возвращает `MAIN` и `ALL` только с `expId=2`;
- `expId=1` с `values=[]` не попадает ни в `MAIN`, ни в `ALL`;
- REST-блок `ALL`, если возвращен, не содержит `finalExpGroup=null` и `expGroup != finalExpGroup`.

## Запуск

```bash
./gradlew clean test \
  --tests "ru.sber.qa.splitter.tests_v9.empty_rules.*" \
  -Dencryption.password=<PASSWORD> \
  -Denv=dev
```

PowerShell:

```powershell
.\gradlew.bat clean test `
  --tests "ru.sber.qa.splitter.tests_v9.empty_rules.*" `
  -Dencryption.password="<PASSWORD>" `
  -Denv=dev
```
