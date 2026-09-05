# EXPLAB-2399

Пакет содержит Kafka/flow/DTO сценарии для проверки мониторинга функции загрузки конфигурации сплиттования из Kafka. Все конфигурации, включая подготовочные seed/current-конфигурации, загружаются только через `splitting-config-created`; REST `/api/v1/splitter/mapper/config` в этих сценариях не используется. `pre-calculate` вызывается через REST только как отдельная проверяемая функция предрасчета, а не как способ загрузки конфига.

## Покрытие

| ID | Автотест | Что проверяет |
|---|---|---|
| EXPLAB-2399-01 | `kafkaConfigLoadShouldWriteLoadedWithPrecalcMonitoring` | Seed-конфиг загружается через Kafka, затем выполняется `pre-calculate`, затем новая конфигурация снова загружается через Kafka. Проверяется `LOADED_WITH_PRECALC` в `omon_explab_splitter_log` с метриками предрасчета; status topic проверяется только при `splitter.config.kafka.status.required=true`. |
| EXPLAB-2399-02 | `kafkaOldConfigVersionShouldWriteNotLoadedOldVersionMonitoring` | Current-конфиг загружается через Kafka, затем старая версия с `forceConfigLoad=false` отправляется через Kafka. Проверяется monitoring `NOT_LOADED_OLD_VERSION`; status topic опционален. |
| EXPLAB-2399-03 | `kafkaConfigWithRequestParamsShouldWriteRequestParamsWithPrecalcMonitoring` | Отклонение `REQUEST_PARAMS` при включенном предрасчете: monitoring `REQUEST_PARAMS_WITH_PRECALC_ENABLED`; status topic опционален. |
| EXPLAB-2399-04 | `kafkaInvalidConfigShouldWriteValidationFailedMonitoring` | Ошибка валидации конфигурации: monitoring `VALIDATION_FAILED`; status topic опционален. |
| EXPLAB-2399-05 | `kafkaInvalidMessageStructureShouldWriteValidationFailedMonitoring` | Невалидная структура входного Kafka-сообщения: мониторинг `VALIDATION_FAILED`. |

## Переопределяемые параметры запуска

- `-Dsplitter.config.kafka.env=dev`
- `-Dsplitter.config.kafka.input.topic=splitting-config-created`
- `-Dsplitter.config.kafka.monitoring.topic=omon_explab_splitter_log`
- `-Dsplitter.config.kafka.status.required=false`
- `-Dsplitter.config.kafka.timeout.seconds=45`
- `-Dkafka_producer.dev.bootstrap.servers=<hosts>`

Тесты используют существующий `KafkaService` проекта для чтения monitoring topic и для отправки входного сообщения в `splitting-config-created`. Topic `splitting-config-requested-and-received` относится к запросу конфига самим splitter-service при старте; для прямой отправки config из теста он не является обязательным ответным topic. Для запуска стенд должен быть в режиме `SPLITTER_API_CONFIG_LOAD=false`; иначе Kafka listener загрузки конфигурации не поднимается.
