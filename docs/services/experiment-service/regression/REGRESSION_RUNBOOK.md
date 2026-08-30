# Регрессионный прогон experiment-service

Документ описывает ручной запуск experiment-service регресса в корпоративной среде после наката тестового пакета EXPLAB-2696/2928/2929 и исправлений ложных падений EXPLAB-2539/legacy-v2.

## Предусловия

- Пакет файлов разложен в корень корпоративного `integration-test` с сохранением путей.
- Корпоративная ветка использует `platform-v-at-framework=1.10.3-beta`.
- В `src/test/resources/test.properties` настроен REST base-uri нужного стенда.
- В `src/test/resources/database.properties` настроено подключение к БД ExpLab.
- На стенде применены миграции experiment-service, включая `status_change_element` и `exp_status_change_request`.
- EXPLAB-2930 временно исключена через `config/reporting/outdated-tests.properties`, так как реализация заявки откатилась. Тесты остаются в коде, но не должны попадать в статистику и `build/allure-results`.
- Legacy registry/layer/v1 demo-сценарии, которые по итогам корпоративного прогона 29.08.2026 дают шум из-за устаревших контрактов или фиксированных данных стенда, временно вынесены из функциональной статистики через `config/reporting/outdated-tests.properties`.
- MAPPER-dependent сценарии запускаются только на пользователе со scope `MAPPER` или `area_clist_sp_all`. Если корпоративный пользователь имеет только `area_clist_sp=[REACTIONS]`, передайте `-Dexperiment.mapper.scope.available=false`, чтобы EXPLAB-2559 и legacy V2 happy path не попадали в `allure-results` этого прогона. Старый флаг `-Dexlab2559.mapper.scope.available=false` поддерживается для совместимости.

## Базовая подготовка PowerShell

```powershell
cd <path-to-corporate-integration-test>
```

`env`, `encryption.password` и остальные стендовые параметры должны быть заранее заполнены в корпоративных properties, IDEA Run Configuration или CI. В командах ниже они специально не передаются, чтобы не дублировать локальные настройки и не выводить пароль в терминальный лог.

Перед каждым отдельным прогоном задавайте `$env:JAVA_TOOL_OPTIONS` заново: в этой переменной передается состояние toggle и таймауты ожидания.

## Комбинированный регресс с ожиданием toggle=true

Этот вариант запускает основной регресс при `EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false`, исключая toggle-only сценарии до JUnit discovery. После первого этапа терминал 10 минут показывает крупное напоминание: за это время нужно включить toggle в yaml/deploy и перезапустить pod'ы experiment-service. Затем запускаются только сценарии, которым нужен `toggle=true`.

```powershell
$baseJavaOptions = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000 -Dexperiment.mapper.scope.available=false"

$env:JAVA_TOOL_OPTIONS = "$baseJavaOptions -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexperiment.v2.cj.toggle.tests.enabled=false"
.\gradlew.bat clean test --tests "ru.sber.qa.experiments.*" --tests "ru.sber.qa.controllers.refBookController.*"
$toggleFalseExit = $LASTEXITCODE

$waitUntil = (Get-Date).AddMinutes(10)
while ((Get-Date) -lt $waitUntil) {
    $remaining = [int][Math]::Ceiling(($waitUntil - (Get-Date)).TotalSeconds)
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host "     ПЕРЕЗАПУСТИ POD'Ы EXPERIMENT-SERVICE" -ForegroundColor Yellow
    Write-Host "     И ВЫСТАВЬ ФЛАГ:" -ForegroundColor Yellow
    Write-Host "     EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true" -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Yellow
    Write-Host ("До продолжения toggle=true прогона: {0:mm\:ss}" -f [TimeSpan]::FromSeconds($remaining)) -ForegroundColor Cyan
    Start-Sleep -Seconds ([Math]::Min(30, [Math]::Max(1, $remaining)))
}

$env:JAVA_TOOL_OPTIONS = "$baseJavaOptions -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true -Dexperiment.v2.cj.toggle.tests.enabled=true"
.\gradlew.bat test --rerun-tasks --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest"
$toggleTrueExit = $LASTEXITCODE

.\gradlew.bat allureReport

if ($toggleFalseExit -ne 0 -or $toggleTrueExit -ne 0) {
    exit 1
}
```

Если корпоративный пользователь имеет `MAPPER` или `area_clist_sp_all`, уберите `-Dexperiment.mapper.scope.available=false` из `$baseJavaOptions` или замените значение на `true`.

## Основной регресс, toggle=false

Перед запуском убедитесь, что на pod'ах experiment-service выключен toggle:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000 -Dexperiment.mapper.scope.available=false"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.*" --tests "ru.sber.qa.controllers.refBookController.*"

.\gradlew.bat allureReport
```

Если стенд/пользователь уже имеет доступ к `MAPPER` или `area_clist_sp_all`, уберите `-Dexperiment.mapper.scope.available=false` или замените значение на `true`.

В этом прогоне EXPLAB-2930 не должна попасть в `build/allure-results`.

## Toggle-регресс, toggle=true

Перед запуском включите toggle на pod'ах experiment-service и перезапустите pod'ы:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest"

.\gradlew.bat allureReport
```

## Проверка только новых задач

Для EXPLAB-2928/2929:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2928.*" --tests "ru.sber.qa.experiments.EXPLAB_2929.*"

.\gradlew.bat allureReport
```

Для EXPLAB-2696 при toggle=false:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningExperimentsV1Cache2696FlowTest" --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningSplitsV1Cache2696FlowTest"

.\gradlew.bat allureReport
```

Для EXPLAB-2539 после исправления DTO-ожиданий:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2539.*"

.\gradlew.bat allureReport
```

Для EXPLAB-2559 запускайте отдельно только на стенде с доступом к `MAPPER`:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dexperiment.mapper.scope.available=true"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2559.*"

.\gradlew.bat allureReport
```

## Allure

HTML-отчет после Gradle-запуска:

```text
build/reports/allure-report/allureReport/index.html
```

Raw results:

```text
build/allure-results
```

Архив отчета:

```powershell
Compress-Archive `
  -Path "build\reports\allure-report\allureReport\*" `
  -DestinationPath "build\reports\allure-report\experiment-service-regression-allure.zip" `
  -Force
```

## Разбор красного прогона

`allure/categories.json` автоматически копируется в `build/allure-results` после задачи `test`.
В отчете падения группируются по классам:

- `Service defect: EXPLAB-2928 status_change_element schema` - дефекты схемы/миграции `status_change_element`.
- `Service defect: EXPLAB-2929 complete-action deserialization` - дефекты приема `CompleteActionRequestDto`.
- `Service defect: EXPLAB-2696 running ids filter` - дефекты фильтрации running splits по `ids`.
- `Service defect: V2 HTTP contract expects 422` - несоответствие HTTP-контракта v15, где документация ожидает `422`, а сервис возвращает `400`.
- `Stand/config: MAPPER scope unavailable` - стендовое ограничение пользователя без `MAPPER`; для REACTIONS-only прогона используйте `-Dexperiment.mapper.scope.available=false`.
- `Test defect: legacy V1 error message expectation` - устаревшее ожидание текста V1-валидации; после текущего patch-набора сценарии должны ожидать `Некорректный запрос`.

Сервисные категории не исключаются из статистики: они остаются красными, чтобы не потерять реальные дефекты сервиса. Стендовые MAPPER-падения нужно убирать флагом запуска, а не помечать как дефект сервиса.

## Ожидаемый результат после текущего patch-набора

- EXPLAB-2930 отсутствует в `build/allure-results`.
- Устаревшие legacy registry/layer/v1 demo-сценарии отсутствуют в функциональной статистике и не маскируют дефекты сервиса.
- Legacy/v2 broken по `EnvironmentConfigWithRestV2.getServiceConfigurations()` больше не воспроизводятся.
- EXPLAB-2539 проверяет актуальный v15 DTO: `splittingPointCode` и `splittingPointName`.
- EXPLAB-2928 может продолжать падать до исправления схемы `status_change_element` на сервисе.
- EXPLAB-2929 может продолжать падать до исправления десериализации `CompleteActionRequestDto` на сервисе.
- EXPLAB-2696 требует отдельные прогоны под `toggle=false` и `toggle=true`.
- EXPLAB-2559 и legacy V2 happy path исключаются из запуска при `-Dexperiment.mapper.scope.available=false`; без этого флага сценарии остаются активными и проверяют поведение на `MAPPER`.
