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
| Running experiments v1 | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningExperimentsV1Cache2696FlowTest.java | EXPLAB-2696-EXP-01..EXP-07 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false или не задан | GET /api/v1/experiments/list/running возвращает только IN_PROGRESS version=4, исключает DRAFT/AGREED/STOPPED, не дублирует id, отдает внешний DTO-контракт | Локальный прогон на заглушке: 7/7 passed |
| Running splits v1 | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningSplitsV1Cache2696FlowTest.java | EXPLAB-2696-SPL-01..SPL-05 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false или не задан | GET /api/v1/experiments/splits/list/running возвращает только IN_PROGRESS, строго фильтрует по ids, для ids=abc возвращает 400, для неизвестного ids возвращает [], отдает внешний DTO-контракт | Локальный прогон на заглушке: 5/5 passed |
| V2 CJ toggle mode | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/RunningV1CacheV2CjEnabled2696FlowTest.java | EXPLAB-2696-TGL-01..TGL-03 | EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true, yaml обновлен, pod'ы перезапущены, в запуск передан -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true | Running experiments/splits v1 возвращают пустые массивы, ручной evict не наполняет v1 running-cache | Локальный прогон на заглушке: 3/3 passed |
| Общая база сценариев | src/test/java/ru/sber/qa/experiments/EXPLAB_2696/AbstractRunningV1Cache2696FlowTest.java | helpers: создание/cleanup данных, ожидание running-cache, проверки DTO | Зависит от наследующего тестового класса | Общие шаги создают тестовые данные, переводят статусы, ждут обновление cache и чистят данные после сценариев | Проверено через 15/15 passed в наследующих тестовых классах |
```

## Local run commands

Create ignored local dependency config if the stand needs local libs:

```properties
useLocalLibs=true
localLibDir=<workspace>/BACK/01_reference_sources/lib
```

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

## Manual environment checks

| ID | Check | Reason |
| --- | --- | --- |
| `CACHE-01` | Prepare `version=4` + `IN_PROGRESS` experiment, restart experiment-service, call `GET /api/v1/experiments/list/running` immediately after startup. | Requires controlled service lifecycle. |
| `CACHE-02` | Temporarily speed up `KLEI_EXPERIMENT_SERVICE_CACHE_EVICT_CRON_COMMUNICATION`, add/remove running experiments, wait for cron refresh. | Requires ConfigMap/yaml change and service restart. |
| `ERR-01` | Inject inconsistent DB/cache data and call running experiments/splits methods. | Requires DB fault or service-level mock; expected `422` and log entry. |
| `ERR-02` | Call `DELETE /api/v1/experiments/evict-cash` without required auth on a stand where auth is enabled. | Auth contract depends on stand profile. |
