# Регрессионный прогон splitter-service

Документ описывает корпоративный запуск полного splitter-регресса через Gradle tasks. Локальная инфраструктура в этом сценарии не поднимается: MAPPER, REACTIONS и Kafka должны быть уже развернуты на корпоративном стенде.

## Предусловия

- `env` указывает на нужный стенд в `src/test/resources/test.properties`, Gradle `-Denv` или CI-переменных.
- Для общего ingress задан `rest.<env>.gateway.base-uri`.
- Если MAPPER и REACTIONS опубликованы как разные splitter-сервисы или версии SDK, заданы отдельные URI:
  - `rest.<env>.splitter-mapper.base-uri`
  - `rest.<env>.splitter-reactions.base-uri`
- Kafka producer/consumer properties выбранного стенда заполнены в `src/test/resources/kafka-*.properties`, `secure.local.override.properties`, environment variables или JVM `-D`.
- Для полного REST+Kafka отчета оба запуска пишут в один `allure.results.directory`.

## Проверка Gradle tasks

```powershell
.\gradlew.bat tasks --all
```

В списке должны быть:

- `splitterRestRegression`
- `splitterKafkaRegression`

## REST config-load

Перед запуском REST-режима на обоих splitter-сервисах выставьте флаг загрузки config через REST API:

```text
MAPPER:    splitter.config.api-config-load=true
REACTIONS: splitter.config.api-config-load=true
```

Если флаг задается через environment variable deployment/config map, используйте эквивалент:

```text
MAPPER:    SPLITTER_CONFIG_API_CONFIG_LOAD=true
REACTIONS: SPLITTER_CONFIG_API_CONFIG_LOAD=true
```

После применения флага дождитесь, что обе версии splitter-service перезапущены и готовы принимать запросы.

Запуск REST-регресса:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat clean splitterRestRegression `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Dsplitter.config.load.mode=rest" `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka"
```

## Kafka config-load

Перед запуском Kafka-режима на обоих splitter-сервисах выставьте флаг загрузки config через Kafka consumer:

```text
MAPPER:    splitter.config.api-config-load=false
REACTIONS: splitter.config.api-config-load=false
```

Если флаг задается через environment variable deployment/config map, используйте эквивалент:

```text
MAPPER:    SPLITTER_CONFIG_API_CONFIG_LOAD=false
REACTIONS: SPLITTER_CONFIG_API_CONFIG_LOAD=false
```

После применения флага дождитесь, что обе версии splitter-service перезапущены, а Kafka consumer читает topic `splitting-config-created`.

Запуск Kafka-регресса в тот же Allure results directory:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat splitterKafkaRegression `
  "-PincludeDisabledTests=true" `
  "-Psplitter.test.profile=current" `
  "-Dsplitter.config.load.mode=kafka" `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka"
```

Если стенд не публикует status topic `splitting-config-requested-and-received`, добавьте к Kafka-запуску:

```powershell
  "-Dsplitter.config.kafka.status.required=false"
```

## Общий Allure отчет

Не очищайте `build\allure-results-splitter-rest-kafka` между REST и Kafka запусками. Тесты, которые применимы к обоим режимам, должны попасть в Allure два раза: один результат с `splitter.config.load.mode=rest`, второй с `splitter.config.load.mode=kafka`.

Сборка HTML-отчета:

```powershell
.\gradlew.bat downloadAllure

.\build\allure\commandline\bin\allure.bat generate `
  build\allure-results-splitter-rest-kafka `
  --clean `
  -o build\reports\allure-report\splitter-rest-kafka-regression
```

Итоговый отчет:

```text
build/reports/allure-report/splitter-rest-kafka-regression/index.html
```

## Выгрузка в TestOps

После двух прогонов `testOpsUpload` может загрузить тот же общий каталог результатов. Старые параметры `allureResultsDir`, `ALLURE_RESULTS_DIR` и `ALLURE_RESULTS` сохраняются; для splitter-регресса можно использовать тот же `allure.results.directory`, который передавался в REST/Kafka tasks.

Пример выгрузки общего REST+Kafka результата:

```powershell
.\gradlew.bat testOpsUpload `
  "-Dallure.results.directory=build\allure-results-splitter-rest-kafka" `
  "-DallureLaunchName=Splitter REST+Kafka regression" `
  "-DallureLaunchTags=splitter,rest,kafka"
```

Если в корпоративном контуре принят отдельный параметр для upload, команда остается совместимой:

```powershell
.\gradlew.bat testOpsUpload `
  "-DallureResultsDir=build\allure-results-splitter-rest-kafka" `
  "-DallureLaunchName=Splitter REST+Kafka regression" `
  "-DallureLaunchTags=splitter,rest,kafka"
```

## Правила фильтрации

- `splitterRestRegression` запускает splitter-сценарии, применимые к REST config-load.
- `splitterKafkaRegression` запускает splitter-сценарии, применимые к Kafka config-load.
- Сценарии неподходящего режима исключаются до test discovery и не попадают в Allure как skipped, broken или unknown.
- Сценарии, помеченные как применимые к обоим режимам, выполняются в обоих запусках.
- `splitter.config.load.mode` добавляется в Allure parameters и history id, поэтому REST и Kafka результаты одного сценария не схлопываются в retry.
