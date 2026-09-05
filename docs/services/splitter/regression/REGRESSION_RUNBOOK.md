# Регрессионный прогон splitter-service

Документ описывает корпоративный запуск splitter-регресса через Gradle tasks. Локальная инфраструктура не поднимается: MAPPER, REACTIONS и Kafka должны быть уже развернуты на корпоративном стенде.

Короткая последовательность PowerShell-команд лежит рядом: `docs/services/splitter/regression/SPLITTER_GRADLE_RUN_COMMANDS.md`.

## Предусловия

- Архив с проектом распакован в `C:\Work\IdeaProjects`.
- Проект находится в `C:\Work\IdeaProjects\integration-test`.
- Корпоративный Gradle 7.3.3 распакован в `C:\Work\IdeaProjects\gradle-7.3.3-bin`, `C:\Work\IdeaProjects\gradle-7.3.3` или внутрь проекта.
- `GRADLE_USER_HOME` перед основным прогоном не задается: используется корпоративный cache пользователя.
- `env` указывает на нужный стенд в `src/test/resources/test.properties`, Gradle `-Denv` или CI-переменных.
- Kafka producer/consumer properties выбранного стенда заполнены в `src/test/resources/kafka-*.properties`, `secure.local.override.properties`, environment variables или JVM `-D`.
- Для полного REST+Kafka отчета оба запуска пишут в один `allure.results.directory`.

## DEV Kafka

Для корпоративного DEV стенда splitter Kafka работает в `PLAINTEXT`. Kafka используется не только splitter-сервисом, поэтому splitter-регресс выбирает отдельный Kafka profile и не переопределяет общие профили проекта.

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

`splitting-config-requested-and-received` используется splitter-service для запроса конфига при старте, когда активный config не найден. При прямой отправке config тестом в `splitting-config-created` обязательным подтверждением является monitoring `SPLITTING_CONFIG_LOAD` и функциональная проверка `/split`, поэтому корпоративный default для регресса: `splitter.config.kafka.status.required=false`.

При `splitter.config.load.mode=kafka` старые сценарии, которые вызывают `loadConfig`, не идут в REST `/config`: проект отправляет тот же DTO в Kafka topic `splitting-config-created`, ждет signal загрузки из `omon_explab_splitter_log` или из status topic при явном `splitter.config.kafka.status.required=true`, затем возвращает тесту синтетический response в REST-like формате. Бизнес-логика после этого проверяется теми же `/split`, pre-calculate и monitoring assertions, что и в REST-регрессе.

## IFT Kafka

Для splitter-регресса на IFT используйте профиль `splitter_ift`, соответствующий кластеру `OOD IFT New` в Offset Explorer:

```text
bootstrap.servers=tsldq-mvp000243.cloud.delta.sbrf.ru:9093,tsldq-mvp000244.cloud.delta.sbrf.ru:9093,tsldq-mvp000245.cloud.delta.sbrf.ru:9093
security.protocol=SSL
truststore=C:\Work\start\kafka\truststore-ood-new.jks
keystore=C:\Work\start\kafka\keystore-ood-new.jks
```

Общие Kafka-профили проекта не используются как splitter defaults: Kafka в проекте нужна не только splitter-сервису, поэтому regression tasks подставляют отдельные `splitter_dev`/`splitter_ift`. Пароли truststore/keystore передаются через разовый `secure.local.override.properties`, environment variables или JVM `-D`. Для `splitter_ift` используются плейсхолдеры:

```properties
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_TRUSTSTORE_PASSWORD=<password>
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_KEYSTORE_PASSWORD=<password>
SECURE_KAFKA_CONSUMER_SPLITTER_IFT_SSL_KEY_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_TRUSTSTORE_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_KEYSTORE_PASSWORD=<password>
SECURE_KAFKA_PRODUCER_SPLITTER_IFT_SSL_KEY_PASSWORD=<password>
```

Не коммитьте файл с секретами.

## Порядок запуска

1. Откройте PowerShell и выполните блок подготовки из `SPLITTER_GRADLE_RUN_COMMANDS.md`.
2. Выставьте для MAPPER и REACTIONS флаг REST-режима: `SPLITTER_API_CONFIG_LOAD=true`.
3. Дождитесь перезапуска обоих splitter-сервисов.
4. Выполните Gradle task `splitterRestRegression` командой из `SPLITTER_GRADLE_RUN_COMMANDS.md`.
5. Выставьте для MAPPER и REACTIONS флаг Kafka-режима: `SPLITTER_API_CONFIG_LOAD=false`.
6. Дождитесь перезапуска обоих splitter-сервисов и готовности consumer читать `splitting-config-created`.
7. Выполните Gradle task `splitterKafkaRegression` в тот же `build\allure-results-splitter-rest-kafka`.
8. Выполните `splitterRestKafkaAllureReport` для общего HTML-отчета.
9. Выполните `prepareSplitterRestKafkaAllureResultsForTestOps`, чтобы переложить общий набор результатов в `build\allure-results`.
10. При необходимости выполните `testOpsUpload` или `splitterRestKafkaTestOpsUpload`.

## Логи

Gradle tasks splitter-регресса сами пишут диагностические логи:

```text
build\logs\splitter-regression\splitter-rest-run.log
build\logs\splitter-regression\splitter-kafka-run.log
```

В этих файлах сохраняются параметры запуска, stdout/stderr тестов, старт/финиш каждого сценария и stacktrace падений. Не направляйте вывод Gradle через `Tee-Object` в `build`: если файл открыт PowerShell, следующий `clean` не сможет удалить каталог `build`.

`generateReportEligibility` печатает прогресс по Java-файлам. Если запуск выглядит зависшим, по последней строке `Report eligibility scan [N/M]` видно, какой файл проверяется.

## Результаты

Общий каталог результатов двух прогонов:

```text
build\allure-results-splitter-rest-kafka
```

Общий HTML-отчет:

```text
build\reports\allure-report\splitter-rest-kafka-regression\index.html
```

Каталог для выгрузки в TestOps после подготовки:

```text
build\allure-results
```
