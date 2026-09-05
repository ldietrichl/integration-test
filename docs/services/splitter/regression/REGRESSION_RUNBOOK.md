# Регрессионный прогон splitter-service

Документ описывает корпоративный запуск полного splitter-регресса через Gradle tasks. Локальная инфраструктура в этом сценарии не поднимается: MAPPER, REACTIONS и Kafka должны быть уже развернуты на корпоративном стенде.

Короткая последовательность команд без пояснений лежит в `docs/services/splitter/regression/SPLITTER_GRADLE_RUN_COMMANDS.md`.

## Предусловия

- По умолчанию не задавайте `GRADLE_USER_HOME`: wrapper должен использовать корпоративный Gradle cache пользователя. Если wrapper не может скачать `gradle-7.3.3-bin.zip` и distribution cache был отдельно скопирован в проект, задайте `$env:GRADLE_USER_HOME = "C:\Work\IdeaProjects\integration-test\.gradle"` только для этого запуска.
- `env` указывает на нужный стенд в `src/test/resources/test.properties`, Gradle `-Denv` или CI-переменных.
- Для общего ingress задан `rest.<env>.gateway.base-uri`.
- Если MAPPER и REACTIONS опубликованы как разные splitter-сервисы или версии SDK, заданы отдельные URI:
  - `rest.<env>.splitter-mapper.base-uri`
  - `rest.<env>.splitter-reactions.base-uri`
- Kafka producer/consumer properties выбранного стенда заполнены в `src/test/resources/kafka-*.properties`, `secure.local.override.properties`, environment variables или JVM `-D`.
- Для полного REST+Kafka отчета оба запуска пишут в один `allure.results.directory`.

## DEV Kafka

Для корпоративного DEV стенда splitter Kafka работает в `PLAINTEXT`.
Kafka используется не только splitter-сервисом, поэтому splitter-регресс должен выбирать отдельный Kafka profile и не переопределять общие профили проекта.

Профиль `splitter_dev`:

```text
bootstrap.servers=tsleq-mvp000412.esrt.sber.ru:9092,tsleq-mvp000413.esrt.sber.ru:9092
security.protocol=PLAINTEXT
```

Splitter topics по логам `splitter-service`:

```text
splitter.config.kafka.input.topic=splitting-config-created
splitter.config.kafka.monitoring.topic=omon_explab_splitter_log
splitter.kap.topic=explab-splitting-result
splitter.kap.monitoring.topic=omon_explab_splitter_log
```

`splitting-config-requested-and-received` используется splitter-service для запроса конфига при старте, когда активный config не найден. При прямой отправке config тестом в `splitting-config-created` обязательным подтверждением является monitoring `SPLITTING_CONFIG_LOAD` и функциональная проверка `/split`, поэтому `splitter.config.kafka.status.required=false` является корпоративным default.

Для диагностики учитывайте короткий retention `splitting-config-requested-and-received`: на DEV он может быть около 10 минут, поэтому старые startup-request сообщения быстро исчезают и этот topic нельзя использовать как долговременный источник доказательств прогона.

## IFT Kafka

Для splitter-регресса на IFT используйте профиль `splitter_ift`, соответствующий кластеру `new explab` в Offset Explorer:

```text
bootstrap.servers=tsleq-mvp000273.esrt.sber.ru:9092,tsleq-mvp000275.esrt.sber.ru:9092
security.protocol=PLAINTEXT
```

IFT topics:

```text
splitter.config.kafka.input.topic=splitting-config-created
splitter.config.kafka.monitoring.topic=explab_sowa_log
splitter.kap.topic=explab-splitting-result
splitter.kap.monitoring.topic=explab_sowa_log
```

Общий профиль `ift` оставлен под `OOD IFT New`, потому что это другой SSL-кластер и он может использоваться другими Kafka-сценариями проекта:

```text
bootstrap.servers=tsldq-mvp000243.cloud.delta.sbrf.ru:9093,tsldq-mvp000244.cloud.delta.sbrf.ru:9093,tsldq-mvp000245.cloud.delta.sbrf.ru:9093
security.protocol=SSL
```

Пароли truststore/keystore для `ift`/`ood_ift_new` передаются через разовый `secure.local.override.properties` из пакета установки. Не коммитьте этот файл.

Gradle splitter tasks передают `secure.placeholders.fail-on-unresolved=false`, чтобы KafkaService не падал на незаполненных `SECURE_*` плейсхолдерах невыбранных Kafka profiles. Для выбранного SSL-профиля реальные truststore/keystore файлы и пароли все равно должны быть заполнены.

## Проверка Gradle tasks

```powershell
.\gradlew.bat tasks --all
```

В списке должны быть:

- `splitterRestRegression`
- `splitterKafkaRegression`
- `splitterRestDebug`
- `splitterKafkaDebug`

`splitterRestDebug` и `splitterKafkaDebug` запускают только сценарий `CFG-01. Валидный конфиг принимается и начинает применяться в split`. Используйте их для отладки REST/Kafka цепочки перед полным регрессом.

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

Если нужен отдельный контрактный прогон status topic, добавьте к Kafka-запуску:

```powershell
  "-Dsplitter.config.kafka.status.topic=splitting-config-requested-and-received" `
  "-Dsplitter.config.kafka.status.required=true"
```

## Запуск на IFT

Для IFT используйте те же REST/Kafka команды, но замените/добавьте параметры окружения и monitoring topic:

```powershell
"-Denv=ift" `
"-Dsplitter.config.kafka.env=splitter_ift" `
"-Dsplitter.kap.kafka.env=splitter_ift" `
"-Dsplitter.precalc.monitoring.kafka.env=splitter_ift" `
"-Dsplitter.config.load.monitoring.kafka.env=splitter_ift" `
"-Dsplitter.config.kafka.monitoring.topic=explab_sowa_log" `
"-Dsplitter.kap.monitoring.topic=explab_sowa_log" `
"-Dsplitter.precalc.monitoring.topic=explab_sowa_log" `
"-Dsplitter.config.load.monitoring.topic=explab_sowa_log"
```

## Общий Allure отчет

Не очищайте `build\allure-results-splitter-rest-kafka` между REST и Kafka запусками. Тесты, которые применимы к обоим режимам, должны попасть в Allure два раза: один результат с `splitter.config.load.mode=rest`, второй с `splitter.config.load.mode=kafka`.

Gradle tasks сами пишут диагностические логи прогона в `build\logs\splitter-regression`:

```text
build\logs\splitter-regression\splitter-rest-run.log
build\logs\splitter-regression\splitter-kafka-run.log
```

В этих файлах сохраняются параметры запуска, stdout/stderr тестов, старт/финиш каждого сценария и stacktrace падений. Не направляйте вывод Gradle через `Tee-Object` в `build`: если файл открыт PowerShell, следующий `clean` не сможет удалить каталог `build`.

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
