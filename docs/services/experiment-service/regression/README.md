# Регресс experiment-service

Инструкция описывает запуск автоматизированного регресса experiment-service в корпоративной среде.

Пошаговая инструкция для ручного корпоративного наката и прогона вынесена в
`docs/services/experiment-service/regression/REGRESSION_RUNBOOK.md`.

## Предусловия

- В корпоративной ветке разложен пакет тестов EXPLAB-2696/2928/2929.
- Тесты EXPLAB-2930 остаются в коде, но временно исключены через `config/reporting/outdated-tests.properties`: реализация откатилась, поэтому заявка не участвует в статистике и не формирует `allure-results`.
- Зависимости доступны из Nexus или локального Gradle cache.
- В `src/test/resources/test.properties` настроены REST base-uri для нужного `env`.
- В `src/test/resources/database.properties` настроено подключение к БД ExpLab.
- На стенде применены миграции БД experiment-service, включая `status_change_element` и `exp_status_change_request`.
- Legacy registry/layer/v1 demo-сценарии, подтвержденные как шум корпоративного прогона 29.08.2026, временно исключены из функциональной статистики через `config/reporting/outdated-tests.properties`.
- Для MAPPER-dependent сценариев нужен пользователь со scope `MAPPER` или `area_clist_sp_all`; на REACTIONS-only стенде передайте `-Dexperiment.mapper.scope.available=false`, чтобы EXPLAB-2559 и legacy V2 happy path не попадали в `allure-results`. Старый флаг `-Dexlab2559.mapper.scope.available=false` поддерживается для совместимости.
- Для EXPLAB-2696 toggle-прогоны выполняются отдельно:
  - v1 running-cache: `EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false`;
  - V2 CJ toggle: `EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true`.

## Подготовка PowerShell

```powershell
cd <path-to-corporate-integration-test>
```

`env`, `encryption.password` и остальные стендовые параметры должны быть заранее заполнены в корпоративных properties, IDEA Run Configuration или CI. В командах ниже они специально не передаются, чтобы не дублировать локальные настройки и не выводить пароль в терминальный лог.

Перед каждым отдельным прогоном задавайте `JAVA_TOOL_OPTIONS` заново: в этой переменной находится состояние toggle и таймауты ожидания.

## Комбинированный регресс с паузой на toggle=true

Запуск ниже сначала выполняет общий experiment-service регресс при выключенном toggle, не добавляя toggle-only сценарии в `allure-results` как skipped. Затем терминал 10 минут показывает большое напоминание включить toggle и перезапустить pod'ы, после чего запускает только toggle-сценарии EXPLAB-2696.

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

## Основной регресс, toggle=false

Запуск проверяет весь experiment-service срез: experiments v1/v2, registry v2, layers, refbook splits/cj-links, EXPLAB-2696 v1 running-cache, EXPLAB-2928 и EXPLAB-2929.

Перед запуском убедитесь, что на сервисе выключен toggle:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000 -Dexperiment.mapper.scope.available=false"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.*" --tests "ru.sber.qa.controllers.refBookController.*"

.\gradlew.bat allureReport
```

Если корпоративный пользователь имеет `MAPPER` или `area_clist_sp_all`, уберите `-Dexperiment.mapper.scope.available=false` или замените значение на `true`.

## Toggle-регресс, toggle=true

Этот запуск проверяет только поведение v1 running endpoints при включенном V2 CJ режиме.

Перед запуском включите toggle на сервисе и перезапустите pod'ы:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest"

.\gradlew.bat allureReport
```

## Запуск только новых задач

Для проверки только EXPLAB-2928/2929:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2928.*" --tests "ru.sber.qa.experiments.EXPLAB_2929.*"

.\gradlew.bat allureReport
```

EXPLAB-2930 временно не запускается в регрессе: правило `ru.sber.qa.experiments.EXPLAB_2930.*` исключает эти сценарии до JUnit discovery, поэтому они не попадают в `build/allure-results` даже как skipped. После возврата реализации правило нужно удалить из `config/reporting/outdated-tests.properties`.

Для проверки EXPLAB-2559 отдельно нужен доступ к `MAPPER`:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -Dexperiment.mapper.scope.available=true"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2559.*"

.\gradlew.bat allureReport
```

Для проверки только EXPLAB-2696 в режиме toggle=false:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat clean test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningExperimentsV1Cache2696FlowTest" --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningSplitsV1Cache2696FlowTest"

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

Если нужен отдельный архив отчета для передачи на анализ:

```powershell
Compress-Archive `
  -Path "build\reports\allure-report\allureReport\*" `
  -DestinationPath "build\reports\allure-report\experiment-service-regression-allure.zip" `
  -Force
```

## Ожидаемый результат

- EXPLAB-2930 отсутствует в `build/allure-results`.
- Legacy registry/layer/v1 demo-сценарии отсутствуют в функциональной статистике.
- EXPLAB-2559 и legacy V2 happy path не попадают в `allure-results` при `-Dexperiment.mapper.scope.available=false`.
- EXPLAB-2928/2929 и EXPLAB-2696 SPL-02 остаются красными до исправления сервиса: эти падения нельзя исключать из сигнального прогона.
- `broken` по `EnvironmentConfigWithRestV2.getServiceConfigurations()` больше не должен воспроизводиться.
