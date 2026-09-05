# Splitter Gradle regression commands

Инструкция рассчитана на корпоративный компьютер, где архив распакован в `C:\Work\IdeaProjects`, а проект лежит в `C:\Work\IdeaProjects\integration-test`.

Команды запускают регресс последовательно: сначала REST config-load, затем Kafka config-load. Оба запуска пишут результаты в один каталог `build\allure-results-splitter-rest-kafka`, чтобы сценарии, применимые к обоим режимам, попали в общий Allure отчет двумя отдельными результатами.

## Подготовка PowerShell

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"

chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

Remove-Item Env:\GRADLE_USER_HOME -ErrorAction SilentlyContinue

$gradleBat = Get-ChildItem -Path @(
  "C:\Work\IdeaProjects\gradle-7.3.3-bin\*\gradle-7.3.3\bin\gradle.bat",
  "C:\Work\IdeaProjects\integration-test\gradle-7.3.3-bin\*\gradle-7.3.3\bin\gradle.bat",
  "C:\Work\IdeaProjects\gradle-7.3.3\bin\gradle.bat",
  "C:\Work\IdeaProjects\integration-test\gradle-7.3.3\bin\gradle.bat"
) -ErrorAction SilentlyContinue |
  Select-Object -First 1 -ExpandProperty FullName

if (-not $gradleBat) {
  throw "Не найден Gradle 7.3.3. Распакуйте корпоративный gradle-7.3.3-bin в C:\Work\IdeaProjects или в C:\Work\IdeaProjects\integration-test."
}

& $gradleBat --version
```

Если Gradle снова сообщает о lock в `C:\Users\<user>\.gradle\caches\journal-1`, убейте `Owner PID` из текста ошибки или перезагрузите машину. Не задавайте новый пустой `GRADLE_USER_HOME` для основного прогона, иначе Gradle начнет заново скачивать зависимости.

## Secure override для IFT Kafka

Для DEV Kafka (`splitter_dev`) файл с SSL-секретами не нужен, потому что соединение plaintext.

Для IFT Kafka (`splitter_ift`) файл должен лежать в корне проекта:

```text
C:\Work\IdeaProjects\integration-test\secure.local.override.properties
```

В нем должны быть заполнены split-specific переменные:

```properties
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_TRUSTSTORE_PASSWORD=<password>
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_KEYSTORE_PASSWORD=<password>
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_KEY_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_TRUSTSTORE_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_KEYSTORE_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_KEY_PASSWORD=<password>
```

## Проверка задач

```powershell
& $gradleBat --no-daemon tasks --all --console=plain |
  Select-String "splitterRestRegression|splitterKafkaRegression|splitterRestKafkaAllureReport|prepareSplitterRestKafkaAllureResultsForTestOps|testOpsUpload"
```

## Запуск из Gradle-панели IDEA

Кнопки `splitterRestRegression` и `splitterKafkaRegression` без ручного `-Dallure.results.directory`
пишут в общий каталог:

```text
build\allure-results-splitter-rest-kafka
```

Повторный запуск этих задач не очищает каталог сам по себе: новые `*-result.json` и attachments
добавляются рядом с предыдущими. Результаты будут стерты только при отдельном запуске `clean`
или если вручную удалить `build\allure-results-splitter-rest-kafka`.

## REST config-load

Перед запуском REST-режима выставьте на обоих splitter-сервисах:

```text
splitter-mapper-service:    SPLITTER_API_CONFIG_LOAD=true
splitter-reactions-service: SPLITTER_API_CONFIG_LOAD=true
```

Это ключ `data` в config-map/deployment. После изменения дождитесь перезапуска обоих сервисов.

Топики runtime split-запросов из config-map при этом не меняются:

```text
MAPPER:    splitting_request -> splitting_response
REACTIONS: splitting_request_reactions -> splitting_response_reactions
```

```powershell
& $gradleBat --no-daemon clean splitterRestRegression --console=plain `
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

## Kafka config-load

Перед запуском Kafka-режима выставьте на обоих splitter-сервисах:

```text
splitter-mapper-service:    SPLITTER_API_CONFIG_LOAD=false
splitter-reactions-service: SPLITTER_API_CONFIG_LOAD=false
```

Это ключ `data` в config-map/deployment. После изменения дождитесь перезапуска обоих сервисов и готовности Kafka consumer читать `splitting-config-created`.

Config-load регресс отправляет конфиг и проверяет результат в отдельных топиках:

```text
input:      splitting-config-created
monitoring: omon_explab_splitter_log
```

```powershell
& $gradleBat --no-daemon splitterKafkaRegression --console=plain `
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

Если нужен отдельный контрактный прогон status topic, замените:

```powershell
"-Dsplitter.config.kafka.status.required=false"
```

на:

```powershell
"-Dsplitter.config.kafka.status.topic=splitting-config-requested-and-received" `
"-Dsplitter.config.kafka.status.required=true"
```

## Общий Allure отчет

Не удаляйте `build\allure-results-splitter-rest-kafka` между REST и Kafka запусками.

```powershell
& $gradleBat --no-daemon splitterRestKafkaAllureReport --console=plain
```

Итоговый HTML:

```text
C:\Work\IdeaProjects\integration-test\build\reports\allure-report\splitter-rest-kafka-regression\index.html
```

## Подготовка результатов для TestOps

Эта задача очищает стандартный каталог `build\allure-results` и копирует туда общий REST+Kafka набор из `build\allure-results-splitter-rest-kafka`.

```powershell
& $gradleBat --no-daemon prepareSplitterRestKafkaAllureResultsForTestOps --console=plain
```

После этого можно выгружать стандартный `build\allure-results`:

```powershell
$env:ALLURE_TOKEN = "<TOKEN>"

& $gradleBat --no-daemon testOpsUpload --console=plain `
  "-DallureResultsDir=build\allure-results" `
  "-DallureLaunchName=Splitter REST+Kafka regression" `
  "-DallureLaunchTags=splitter,rest,kafka"
```

Можно сделать подготовку и выгрузку одной Gradle-задачей:

```powershell
$env:ALLURE_TOKEN = "<TOKEN>"

& $gradleBat --no-daemon splitterRestKafkaTestOpsUpload --console=plain `
  "-DallureResultsDir=build\allure-results" `
  "-DallureLaunchName=Splitter REST+Kafka regression" `
  "-DallureLaunchTags=splitter,rest,kafka"
```

## Где смотреть логи

Gradle-задачи splitter-регресса пишут отдельные логи:

```text
build\logs\splitter-regression\splitter-rest-run.log
build\logs\splitter-regression\splitter-kafka-run.log
```

`generateReportEligibility` печатает прогресс по Java-файлам. Если запуск завис, по последней строке `Report eligibility scan [N/M]` видно, на каком файле он остановился.
