# EXPLAB-2974: повтор с альтернативными публичными версиями, 07.09.2026

**Окружение поднято. Все 103 варианта выполнены; 0 passed, 103 failed.** Для каждого теста HTTP-статус и вердикт совпали с исходным окружением. Замена версий не устранила выявленные нарушения контракта v7.

Ветка: `feature/EXPLAB-2974-data-operator`. Исходники сервиса: пользовательский ZIP `06fd6acb646`. Все 74 Java-файла новой сборки повторно сверены по SHA-256 непосредственно с ZIP; изменений нет. Локальная копия POM заменяет закрытые Ignite-зависимости на публичные, исключает недоступные SE security-модули. Исходный архив и первый стенд сохранены.

## Что скачано и запущено

| Компонент | Исходный стенд | Новый стенд |
|---|---|---|
| Compose project | explab-2974-local | explab-2974-compatible |
| Apache Ignite, сервер и клиент | 2.17.0 | **2.18.0** |
| Apache Kafka, брокер | 4.0.2 | **3.9.1** |
| Kafka Java client | 3.9.1 | 3.9.1, совпадает с брокером |
| Spring Kafka / Boot | 3.3.11 / 3.5.8 | 3.3.11 / 3.5.8 |
| Runtime | Zulu JDK 17 | Zulu JDK 17 |
| Data operator HTTP | 127.0.0.1:18074 | **127.0.0.1:18084** |
| Ignite thin client | 127.0.0.1:10874 | 127.0.0.1:10884 |
| Kafka | 127.0.0.1:19074 | 127.0.0.1:19084 |
| Заглушки dictionaries/experiments | 127.0.0.1:18075 | 127.0.0.1:18085 |

Образы предварительно скачаны через Docker Compose, Maven загрузил Ignite 2.18.0 и транзитивные библиотеки. Сборка завершилась `BUILD SUCCESS`. Фактический состав boot JAR проверен: `ignite-core/indexing` и новые бинарные модули — 2.18.0, `kafka-clients` — 3.9.1. Полный список и digest образов записаны в [environment.json](<workspace>/BACK/06_temp_work/explab-2974-compatible/environment.json).

Выбор сохраняет линию Ignite 2.x и выравнивает Kafka с клиентом сервиса. JDK 17 входит в [перечень проверенных JDK Apache Ignite](https://ignite.apache.org/docs/ignite2/latest/quick-start/java). Образ Kafka 3.9.1 указан в [официальной документации Docker-запуска Kafka](https://kafka.apache.org/39/getting-started/docker/). Это основание для локальной проверки, а не утверждение об эквивалентности закрытому Ignite SE 17.6.0.

## Результат и лог

| Показатель | Ignite 2.17.0 / Kafka 4.0.2 | Ignite 2.18.0 / Kafka 3.9.1 |
|---|---:|---:|
| Выполнено вариантов | 103 | 103 |
| Passed / Failed | 0 / 103 | 0 / 103 |
| HTTP 500 | 77 | 77 |
| HTTP 200 на некорректные запросы | 24 | 24 |
| HTTP 400 с нарушением схемы ошибки | 2 | 2 |
| Broken / Skipped | 0 / 0 | 0 / 0 |
| Изменившиеся HTTP-статусы или вердикты | — | **0** |

В тестовом интервале снова 89 ERROR-записей: 63 `HttpMessageNotReadableException`, 4 `MethodArgumentNotValidException`, 10 `NullPointerException`, 10 оборачивающих их `RuntimeException`, 2 `IllegalStateException` для отсутствующего справочника. Десять NPE логируются дважды на разных уровнях; это не дополнительные запросы. Разбор причин в `GlobalExceptionHandler`, DTO и преобразованиях Jackson из [первого отчёта](LOCAL_RUN_20260907.md) остаётся применимым.

Инфраструктура во время проверки работала: Ignite client connected, Dictionaries loaded, Kafka consumer получил partition. Health после прогона — UP. Корректный контрольный POST с `operatorCode=equal` вернул HTTP 200 и пустой список объектов. Первый ручной диагностический запрос по ошибке содержал поле `operation`; его отдельный ответ 500 не входит в 103 теста и исключён из `service-test-interval.log`. Полный лог сохраняет этот запрос для прозрачности анализа.

Холодный старт сервиса занял 53,6 секунды, превысив старое ожидание порта 45 секунд. Сам сервис успешно завершил запуск. Лимит скрипта увеличен до 90 секунд, повторная проверка готовности прошла без перезапуска процесса.

После тестов: `splitting_object_cache=0`, `splitting_field_cache=0`, `actualization_cache=0`; в `param_cache` и `data_source_cache` по одной начальной записи MAPPER. Валидационные сценарии не создают объектов, поэтому удаление не требовалось. Корпоративные данные и внешние системы не использовались.

## Артефакты и продолжение

- [Все 103 результата с HTTP](<workspace>/BACK/06_temp_work/explab-2974-compatible/run-20260907-192019/results-with-http.json).
- [Машинное сравнение и распределение исключений](<workspace>/BACK/06_temp_work/explab-2974-compatible/run-20260907-192019/analysis.json).
- [Лог сервиса в тестовом интервале](<workspace>/BACK/06_temp_work/explab-2974-compatible/run-20260907-192019/service-test-interval.log).
- [Состояние кэшей после тестов](<workspace>/BACK/06_temp_work/explab-2974-compatible/run-20260907-192019/post-run-state.json).
- [Корректный контрольный запрос и ответ](<workspace>/BACK/06_temp_work/explab-2974-compatible/control-response.json).
- [Allure HTML](<workspace>/BACK/06_temp_work/explab-2974-compatible/run-20260907-192019/allure-report/index.html).
- [Команды запуска, прогона и остановки](../../../../local-services/data-operator-explab-2974/README.md).

Это реализованные валидационные SL-35–43 и SL-45; остальные пункты плана не были выполнены этим запуском. Реальные dictionaries/experiments, SE security и TLS/mTLS по-прежнему требуют соответствующих компонентов. [Что можно предоставить для корпоративного контура](INFRASTRUCTURE_REQUIREMENTS.md). Для продолжения текущих локальных проверок дополнительные установки от пользователя не нужны.
