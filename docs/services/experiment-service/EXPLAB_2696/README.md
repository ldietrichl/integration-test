# EXPLAB-2696. Running cache checks

## Automated coverage

| Area | Scenarios |
| --- | --- |
| Running experiments, v2 CJ toggle disabled | `EXP-01..EXP-07`: manual evict, GET-triggered cache refresh, DRAFT/AGREED/STOPPED exclusion, `version=4`, no duplicate ids, external DTO contract. |
| Running splits, v2 CJ toggle disabled | `SPL-01..SPL-05`: only `IN_PROGRESS`, strict `ids` filter, invalid raw `ids`, unknown `ids=[]`, external DTO contract. |
| v2 CJ toggle enabled | `TGL-01..TGL-03`: running experiments/splits return empty arrays and evict does not fill v1 cache. |

## Jira markup

```jira
|| Блок || Тестовый файл || Сценарии || Состояние toggle || Ожидание || Фактический результат ||
| Running experiments v1 | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningExperimentsV1Cache2696FlowTest.java | EXPLAB-2696-EXP-01..EXP-07 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false или не задан | GET /api/v1/experiments/list/running возвращает только IN_PROGRESS version=4, исключает DRAFT/AGREED/STOPPED, не дублирует id, отдает внешний DTO-контракт | Корпоративный прогон 2026-08-28: 7/7 passed |
| Running splits v1 | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningSplitsV1Cache2696FlowTest.java | EXPLAB-2696-SPL-01..SPL-05 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false или не задан | GET /api/v1/experiments/splits/list/running возвращает только IN_PROGRESS, строго фильтрует по ids, для ids=abc возвращает 400, для неизвестного ids возвращает [], отдает внешний DTO-контракт | Корпоративный прогон 2026-08-28: 3/5 passed, 2/5 failed. SPL-02: при GET /api/v1/experiments/splits/list/running?ids=140 вернулся также id=141 и другие IN_PROGRESS сплиты. SPL-04: для ids=9223372036854773111 вернулся непустой массив |
| V2 CJ toggle mode | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningV1CacheV2CjEnabled2696FlowTest.java | EXPLAB-2696-TGL-01..TGL-03 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true, yaml обновлен, pod'ы перезапущены, в запуск передан -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true | Running experiments/splits v1 возвращают пустые массивы, ручной evict не наполняет v1 running-cache | Корпоративный прогон 2026-08-28: первая попытка 3/3 failed из-за HTTP 503 `no healthy upstream` сразу после рестарта pod'а; повторный запуск через ~30 секунд 3/3 passed. В логе сервиса подтверждено `isV2CjExperimentsEnabled=true`, v1 cache load skipped, running experiments/splits return empty v1 lists |
| Общая база сценариев | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/AbstractRunningV1Cache2696FlowTest.java | helpers: создание/cleanup данных, ожидание running-cache, проверки DTO | Зависит от наследующего тестового класса | Общие шаги создают тестовые данные, переводят статусы, ждут обновление cache и чистят данные после сценариев | Проверена через корпоративные прогоны: финально 13/15 passed, 2/15 failed. В накопительном `allure-results` дополнительно есть 3 skipped из запуска TGL без JVM-флага; это не функциональное падение. Локально на заглушке ранее было 15/15 passed |
```

## Run commands

Run disabled-toggle scenarios:

```powershell
.\gradlew.bat test `
  --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningExperimentsV1Cache2696FlowTest" `
  --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningSplitsV1Cache2696FlowTest"
```

Run enabled-toggle scenarios after enabling `EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true`
in experiment-service yaml and restarting pods:

```powershell
.\gradlew.bat test `
  -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true `
  --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest"
```

For slow stands:

```powershell
.\gradlew.bat test `
  -Dexlab2696.running.cache.wait.timeout.ms=60000 `
  -Dexlab2696.running.cache.wait.poll.ms=3000 `
  --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningExperimentsV1Cache2696FlowTest"
```
