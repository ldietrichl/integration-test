# tests_v9/sdk

Пакет содержит REST/Kafka диагностические проверки, которые следуют из анализа SDK и текущих ConfigMap.

## Что проверяется

- `MAIN` в MAPPER определяется по `actionType`, а не по документному параметру `result`.
- `resultDt` ожидается в Kafka/report payload, а не в REST `split` response.
- Поведение `ALL`, пустых объектов и suppressed-объектов зависит от настроек приложения:
  - `splitter.config.return-suppressed`;
  - `splitter.config.all-rule-code-exp-enabled`;
  - `splitter.config.empty-objects-response-enabled`;
  - `splitter.config.allow-result-without-main`.

## Текущий профиль

Текущие ConfigMap сохранены в ресурсах:

- `src/test/resources/splitter/tests_v9/configmaps/current/mapper-config-map.yml`
- `src/test/resources/splitter/tests_v9/configmaps/current/reaction-config-map.yml`

Для проверок Kafka используются теги `requires-kafka`; для проверок, зависящих от env/ConfigMap, используется тег `requires-config-flags`.

## Как запускать из IDE

Системный флаг включения Kafka-проверок больше не нужен. Любой тест можно запускать напрямую из IDE кнопкой Run.

Перед запуском конкретного сценария вручную подготовить соответствующий профиль:

- для REST current-config проверок — current ConfigMap;
- для Kafka-проверок — доступный topic `explab-splitting-result`;
- для `requires-config-flags` — нужное значение application/ConfigMap флага и рестарт pod.

Регрессионные и critical-regression лейблы намеренно не используются.
