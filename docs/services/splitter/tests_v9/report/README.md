# tests_v9/report

Пакет содержит проверки результата сплиттования, который Сплиттер асинхронно отправляет в Kafka/КАП.

Эти сценарии закрывают часть документа `Тесты-v9`, которую нельзя проверить только через REST `/split`:

- наличие `resultDt`;
- полный `ALL` до постобработки REST-ответа;
- наличие несработавших групп в логе/report;
- наличие `finalExpGroup` в report payload;
- проверка `requestId`, `splittingId`, `splittingConfigVersion` в report.

## Как запускать из IDE

Системный флаг включения больше не нужен. Класс можно запускать напрямую из IDE кнопкой Run на классе или методе.

Перед запуском вручную подготовить:

1. применить current ConfigMap для MAPPER/REACTIONS;
2. убедиться, что доступен Kafka topic `explab-splitting-result`;
3. убедиться, что в IDE Run Configuration заданы базовые параметры проекта (`encryption.password`, `env`), если они требуются локальной конфигурацией;
4. при необходимости переопределить Kafka env/topic/timeout через Run Configuration, но это не обязательно: по умолчанию topic = `explab-splitting-result`.

Опциональные параметры, если нужен нестандартный контур/topic:

```bash
-Dsplitter.kap.kafka.env=dev
-Dsplitter.kap.topic=explab-splitting-result
-Dsplitter.kap.timeout.seconds=60
```

Регрессионные и critical-regression лейблы не используются.
