# Команды запуска splitter-регресса через Gradle

Файл предназначен для корпоративного компьютера, где проект расположен в `C:\Work\IdeaProjects\integration-test`, а MAPPER, REACTIONS и Kafka уже подняты на стенде.

## Gradle wrapper

По умолчанию не задавайте `GRADLE_USER_HOME`: wrapper использует корпоративный Gradle cache пользователя.

Если wrapper не может скачать `gradle-7.3.3-bin.zip` и вы отдельно вручную скопировали distribution cache в проектную `.gradle`, только тогда укажите `GRADLE_USER_HOME` на проект:

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
$env:GRADLE_USER_HOME = "C:\Work\IdeaProjects\integration-test\.gradle"
.\gradlew.bat --version
```

Wrapper должен показать `Gradle 7.3.3` без попытки скачать `gradle-7.3.3-bin.zip` из Nexus.

## Перед REST-прогоном

На обоих splitter-сервисах выставьте загрузку config через REST API:

```text
MAPPER:    splitter.config.api-config-load=true
REACTIONS: splitter.config.api-config-load=true
```

Если флаг задается через environment variable deployment/config map:

```text
MAPPER:    SPLITTER_CONFIG_API_CONFIG_LOAD=true
REACTIONS: SPLITTER_CONFIG_API_CONFIG_LOAD=true
```

## REST-прогон

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

.\gradlew.bat clean splitterRestRegression `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Denv=dev" `
  "-Dsplitter.config.load.mode=rest" `
  "-Dsecure.placeholders.fail-on-unresolved=false" `
  "-Dsplitter.kap.kafka.env=splitter_dev" `
  "-Dsplitter.kap.topic=explab-splitting-result" `
  "-Dsplitter.kap.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.precalc.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.load.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log" `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka"
```

## Быстрая отладка CFG-01 через REST

Команда запускает только один сценарий `CFG-01. Валидный конфиг принимается и начинает применяться в split`.

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

.\gradlew.bat clean splitterRestDebug `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Denv=dev" `
  "-Dsplitter.config.load.mode=rest" `
  "-Dsecure.placeholders.fail-on-unresolved=false" `
  "-Dsplitter.kap.kafka.env=splitter_dev" `
  "-Dsplitter.kap.topic=explab-splitting-result" `
  "-Dsplitter.kap.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.precalc.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.load.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log" `
  "-Dallure.results.directory=build\allure-results-splitter-debug"
```

## Перед Kafka-прогоном

На обоих splitter-сервисах выставьте загрузку config через Kafka consumer:

```text
MAPPER:    splitter.config.api-config-load=false
REACTIONS: splitter.config.api-config-load=false
```

Если флаг задается через environment variable deployment/config map:

```text
MAPPER:    SPLITTER_CONFIG_API_CONFIG_LOAD=false
REACTIONS: SPLITTER_CONFIG_API_CONFIG_LOAD=false
```

Дождитесь, что оба сервиса перезапущены и читают topic `splitting-config-created`.

## Kafka-прогон

Запускайте без `clean`, чтобы REST и Kafka результаты остались в одном каталоге Allure.

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

.\gradlew.bat splitterKafkaRegression `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Denv=dev" `
  "-Dsplitter.config.load.mode=kafka" `
  "-Dsecure.placeholders.fail-on-unresolved=false" `
  "-Dsplitter.config.kafka.env=splitter_dev" `
  "-Dsplitter.config.kafka.input.topic=splitting-config-created" `
  "-Dsplitter.config.kafka.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.kafka.status.required=false" `
  "-Dsplitter.config.kafka.unique.consumer.group.enabled=true" `
  "-Dsplitter.config.kafka.consumer.group.prefix=integration-test-splitter-config-load" `
  "-Dsplitter.config.kafka.consumer.warmup.seconds=0" `
  "-Dsplitter.kap.kafka.env=splitter_dev" `
  "-Dsplitter.kap.topic=explab-splitting-result" `
  "-Dsplitter.kap.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.precalc.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.load.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log" `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka"
```

## Быстрая отладка CFG-01 через Kafka

Команда запускает тот же сценарий `CFG-01`, но загрузка config идет через topic `splitting-config-created`, а подтверждение ожидается из monitoring topic `omon_explab_splitter_log`.

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

.\gradlew.bat splitterKafkaDebug `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Denv=dev" `
  "-Dsplitter.config.load.mode=kafka" `
  "-Dsecure.placeholders.fail-on-unresolved=false" `
  "-Dsplitter.config.kafka.env=splitter_dev" `
  "-Dsplitter.config.kafka.input.topic=splitting-config-created" `
  "-Dsplitter.config.kafka.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.kafka.status.required=false" `
  "-Dsplitter.config.kafka.unique.consumer.group.enabled=true" `
  "-Dsplitter.config.kafka.consumer.group.prefix=integration-test-splitter-config-load" `
  "-Dsplitter.config.kafka.consumer.warmup.seconds=3" `
  "-Dsplitter.kap.kafka.env=splitter_dev" `
  "-Dsplitter.kap.topic=explab-splitting-result" `
  "-Dsplitter.kap.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.precalc.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log" `
  "-Dsplitter.config.load.monitoring.kafka.env=splitter_dev" `
  "-Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log" `
  "-Dallure.results.directory=build\allure-results-splitter-debug"
```

Topic `splitting-config-requested-and-received` используется splitter-service для запроса конфига при старте и не является обязательным ответным topic для прямой отправки config в `splitting-config-created`.
На DEV у этого topic короткий retention, примерно 10 минут, поэтому он пригоден только для оперативной диагностики startup-request.

Если нужен отдельный контрактный прогон status topic, добавьте:

```powershell
"-Dsplitter.config.kafka.status.topic=splitting-config-requested-and-received" `
"-Dsplitter.config.kafka.status.required=true"
```

## Allure HTML

```powershell
.\gradlew.bat downloadAllure

.\build\allure\commandline\bin\allure.bat generate `
  build\allure-results-splitter-rest-kafka `
  --clean `
  -o build\reports\allure-report\splitter-rest-kafka-regression
```

Итоговый отчет:

```text
C:\Work\IdeaProjects\integration-test\build\reports\allure-report\splitter-rest-kafka-regression\index.html
```

## Логи Gradle tasks

Regression tasks сами создают логи:

```text
C:\Work\IdeaProjects\integration-test\build\logs\splitter-regression\splitter-rest-run.log
C:\Work\IdeaProjects\integration-test\build\logs\splitter-regression\splitter-kafka-run.log
C:\Work\IdeaProjects\integration-test\build\logs\splitter-regression\splitter-rest-debug-run.log
C:\Work\IdeaProjects\integration-test\build\logs\splitter-regression\splitter-kafka-debug-run.log
```

В логах есть параметры запуска, stdout/stderr тестов, старт/финиш сценариев и stacktrace падений.

## TestOps upload

```powershell
.\gradlew.bat testOpsUpload `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka" `
  "-DallureLaunchName=Splitter REST+Kafka regression" `
  "-DallureLaunchTags=splitter,rest,kafka"
```
