# EXPLAB-2400. Мониторинг загрузки конфигурации с предрасчетом

Пакет: `ru.sber.qa.splitter.EXPLAB_2400`.

Тесты используют REST API для `loadConfig` и `pre-calculate`, а результат мониторинга читают из Kafka topic `omon_explab_splitter_log`.
Все payload формируются через DTO; inline JSON в сценариях отсутствует.

## Реализованные сценарии

| ID | Сценарий | Основные проверки |
|---|---|---|
| EXPLAB-2400-01 | Повторная API-загрузка конфигурации при существующей таблице предрасчета | `LOADED_WITH_PRECALC`, `loadMethod=API`, версии и точные счетчики `1/2/1/1` |
| EXPLAB-2400-02 | Добавление эксперимента при загрузке новой конфигурации | После пересчета: `notLinkedObjects=0`, `totalObjects=2`, `linkedExps=2`, `totalExps=2` |
| EXPLAB-2400-03 | Удаление эксперимента из новой конфигурации | Связи удаленного эксперимента исключены: `notLinkedObjects=1`, `linkedExps=1`, `totalExps=1` |
| EXPLAB-2400-04 | Старая версия при `forceConfigLoad=true` и существующей таблице предрасчета | Конфигурация загружается; monitoring `LOADED_WITH_PRECALC` с точными счетчиками |
| EXPLAB-2400-05 | Старая версия при `forceConfigLoad=false` | Ответ `OLD_VERSION`, monitoring `NOT_LOADED_OLD_VERSION`, конфигурация не заменяется |
| EXPLAB-2400-06 | `REQUEST_PARAMS` при включенном предрасчете | Ответ `REQUEST_PARAMS_WITH_PRECALC_NOT_SUPPORTED`, monitoring `REQUEST_PARAMS_WITH_PRECALC_ENABLED` |
| EXPLAB-2400-07 | Ошибка валидации конфигурации | Ответ `CONFIG_ERROR`, monitoring `VALIDATION_FAILED`; `resultDetails` совпадает с ответом API |

## Критическая проверка EXPLAB-2400

В успешных сценариях счетчик связанных экспериментов должен публиковаться под ключом `linkedExps`.
Тесты отдельно проверяют, что ошибочный ключ `notLinkedExps` отсутствует. Это предотвращает ложное прохождение при корректном числовом значении, но неверном контракте monitoring event.

## Запуск

Linux/macOS:

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2400.*" \
  -Dsplitter.config.load.monitoring.kafka.env=dev \
  -Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log \
  -Dsplitter.config.load.monitoring.timeout.seconds=45
```

Windows CMD:

```cmd
gradlew.bat test --tests "ru.sber.qa.splitter.EXPLAB_2400.*" -Dsplitter.config.load.monitoring.kafka.env=dev -Dsplitter.config.load.monitoring.topic=omon_explab_splitter_log -Dsplitter.config.load.monitoring.timeout.seconds=45
```

## Предусловия

1. На стенде включен предрасчет.
2. Ручная загрузка конфигурации через API разрешена.
3. Kafka consumer тестового проекта имеет доступ к `omon_explab_splitter_log`.
4. Тесты выполняются последовательно; пакет использует `@ResourceLock("splitter-config")`.
5. Сценарии `LOADED` при выключенном предрасчете и искусственный `EXCEPTION` требуют изменения стендовых параметров или fault injection и в автоматический набор не включены.
