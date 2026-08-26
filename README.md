# Автоматизация тестирования на проекте "ExpLab - модуль A/B тестирования"


## Предварительная настройка


### IDE

* Кодировка файлов
  * **Menu - File - Settings - Tree: Editor - File Encodings**: Установить значения
  параметров "_Global Encoding_", "_Project Encoding_", 
  "_Default encoding for property files_" в значение `UTF-8`.
* Разворачивание импортов
  * **Menu - File - Settings - Tree: Editor - CodeStyle - Java**: В полях 
  "_Class count to use import with '*'_:"
  и "_Names count to use static import with '*'_:" установить любые достаточно
  большие значения например 500 или 999.
  * Что-бы раскрыть импорты в текущем файле использовать hotkey: `Ctrl + Alt + o`.
* Одна пустая строка в конце файлов
  * **Menu - File - Settings - Tree: Editor - General**: В разделе On Save активировать
  чекбокс "_Ensure every saved file ends with a line break_".


### Переменные окружения:

* Мастер пароль
  * `encryption.password=***************`
* Тестовое окружение (dev, ift)
  * `env=dev`

В итоге должна получится строка
`encryption.password=***************;env=dev`
с установленными <span style="color:red">верными параметрами</span>, которая прописывается в IDE: 
***Run\Debug Configuration Templates*** для ***Gradle*** или ***JUnit***
в поле ***Environment variables***.


## Информация о проекте:


### Использованные сервисы

* DatabaseService  
    * Для PostgresDB
* RestService
* KafkaService


### Планируемые сервисы

* ContainerService
  * Для OpenShift

## REST окружение

Все REST base URI читаются из `src/test/resources/test.properties` через
`RestEndpointResolver`. Для переключения стенда достаточно изменить `env` на
`dev`, `ift`, `ift-dm` или `lt`.

В тестовом коде используются логические имена сервисов (`SPLITTER`,
`EXPERIMENTS`, `DICTIONARIES`, `DATA_OPERATOR`, `MESSAGES`,
`CONFIGURATION_SERVICE`, `MESSAGE_AUDIT`). Если отдельный URI сервиса не задан,
resolver использует `rest.<env>.gateway.base-uri`. Абсолютные адреса в Java-коде
не допускаются.

## Теги и состав Allure-отчета

Перед функциональным запуском выполняется проверка состава отчета:

```bash
./gradlew generateReportEligibility
```

Результат создается в `build/report-eligibility`. Тесты с `@Disabled`, без проверок и явно устаревшие тесты исключаются до JUnit discovery.

Для технической регистрации только актуальных тестов в TestOps:

```bash
./gradlew bypassTests
```

Канонические tags формируются централизованно: задача, сервис, regress/critical-regress и manual/automated. Подробности приведены в `TAGGING_AND_REPORTING_POLICY.md`.
