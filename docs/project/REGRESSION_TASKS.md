# Регрессы через Gradle в IDEA

Четыре задачи запускают поддерживаемые регрессы проекта. Среда выбирается значением `env` в `src/test/resources/test.properties`; этот файл является основным источником среды и для Gradle, и для запуска тестов непосредственно из IDEA.

Для DEV сохраните:

```properties
env=dev
```

Для ИФТ сохраните:

```properties
env=ift
```

REST, Kafka и Ignite используют настройки выбранной среды. Перед прогоном заполните соответствующие профили и секреты в `secure.local.override.properties`. Пароли в команды запуска не передаются.

При запуске через **Run Tests** в IDEA сохраните файл и используйте Working directory = `$PROJECT_DIR$`. Читается редактируемый `src/test/resources/test.properties`, а не устаревшая копия из `build/resources/test`. Если исходного файла нет (упакованные тесты), используется classpath. Значения `-Denv`, `-Penv` и `ENV` больше не выбирают другую среду. Отдельные адреса/пароли соответствующих профилей продолжают настраиваться штатно.

Файл `src/test/resources/regression-profiles.properties` содержит несекретные Kafka-профили и топики DEV/IFT. Старые непрофилированные `splitter_dev` в `test.properties` не перенаправляют ИФТ на DEV. БД выбирается по точной среде `env`: для `ift-dm` требуется собственный профиль `db.ift-dm.<name>.*` в `database.properties`, подмена на `ift` отсутствует. Для других сред Kafka нужно заполнить отдельный профиль.

## Задачи в окне Gradle

После замены файлов выполните **Reload All Gradle Projects** в IDEA. В **Tasks → regression** доступны:

| Задача | Действие |
| --- | --- |
| `experimentServiceRegression` | Поддерживаемые тесты `ru.sber.qa.experiments.*` и `ru.sber.qa.controllers.refBookController.*` с учётом исключений и состояния toggle |
| `splitterRestRegression` | Поддерживаемый splitter-регресс с загрузкой конфигурации через REST |
| `splitterKafkaRegression` | Поддерживаемый splitter-регресс с загрузкой конфигурации через Kafka |
| `dataOperatorRegression` | Общие REST-проверки data-operator, EXPLAB-2411, EXPLAB-2729 и полный EXPLAB-2974, включая 173 сценария links |
| `prepareRegressionTestOpsResults` | Проверить и объединить последние завершённые прогоны четырёх этапов выбранной среды |
| `regressionTestOpsUpload` | Подготовить объединённые результаты регрессов и загрузить их в TestOps с действующими настройками проекта |
| `cleanRegressionResults` | Удалить все результаты выбранной среды из `build/regression-results/<env>` |

В **Tasks → testops** находятся задачи для произвольно выбранных тестов, запущенных из IDEA или через обычный `test --tests`:

| Задача | Действие |
| --- | --- |
| `prepareTestOpsResults` | Проверить текущие raw Allure results и подготовить набор в `build/testops-results/<env>` |
| `testOpsUpload` | Подготовить и загрузить этот набор выбранных тестов |
| `cleanTestOpsResults` | Удалить подготовленный набор текущей среды и распознанные Allure-файлы из разрешённого raw-каталога, сохранив неизвестные файлы |

Порядок запуска папки, фичи или отдельного теста описан в [TestOps для выбранных тестов](../reporting/TESTOPS_SELECTED_RUNS.md). Результаты `dataOperatorRegression`, как и остальных трёх регрессов, обслуживаются задачами группы **regression**.

Источник для `prepareTestOpsResults` и `testOpsUpload` задаётся через `testOpsSourceResultsDir`; без него наследуется Gradle-настройка `allure.results.directory` со стандартным значением `build/allure-results`. Автоматическая очистка raw разрешена только для `build/allure-results` и корневого `allure-results` проекта; любой нестандартный source требует ручной очистки.

Служебные задачи `test`, `bypassTests` и splitter debug остаются доступны по имени. Для обычного регресса используйте четыре выделенные задачи. Старые обёртки `testAndUploadToTestOps` и `bypassTestsAndUploadToTestOps` заменены отдельным запуском тестов и последующей загрузкой нужного набора.

## Терминал IDEA: PowerShell

Откройте терминал в корне корпоративного проекта:

```powershell
Set-Location 'C:\Work\IdeaProjects\integration-test'
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc
```

Первая проверка после наката — компиляция тестов без обращений к стенду:

```powershell
.\gradlew.bat testClasses --offline --console=plain
```

Проверки выбора среды и исключений без запросов к стенду:

```powershell
.\gradlew.bat test --offline --console=plain --rerun-tasks `
  --tests 'config.services.core.TestConfigurationFilesTest' `
  --tests 'config.services.core.RegressionProfileConfigurationTest' `
  --tests 'config.services.core.DatabaseEnvironmentSelectionTest' `
  --tests 'ru.sber.qa.allure.ReportExclusionsSelectionTest'
```

Эти четыре новых класса содержат 16 проверок, включая защиту Kafka helpers от устаревших JVM-настроек среды и топиков. Их результаты не включаются в набор четырёх регрессов для TestOps.

При успешной компиляции запускайте нужные регрессы отдельно:

```powershell
.\gradlew.bat experimentServiceRegression --offline --console=plain
```

```powershell
.\gradlew.bat splitterRestRegression --offline --console=plain
```

```powershell
.\gradlew.bat splitterKafkaRegression --offline --console=plain
```

```powershell
.\gradlew.bat dataOperatorRegression --offline --console=plain
```

Параметры `-I`, `-Penv`, имена тестовых классов и флаг включения фикстур задавать не требуется. Каждый запуск получает новый каталог результатов внутри `build`. Обычный `clean` удаляет весь `build`, включая результаты прошлых регрессов, подготовленные наборы TestOps и сгенерированные манифесты фикстур. Поэтому между этапами одного цикла регресса `clean` не запускайте. Выполняйте его перед новым независимым циклом только после сохранения нужных результатов и завершения очистки данных стенда.

`--offline` использует уже подготовленные корпоративные Gradle dependencies. Если нужной зависимости нет в кеше, сначала загрузите её через разрешённый корпоративный репозиторий.

Запускайте этапы последовательно. Дождитесь завершения активного прогона перед подготовкой, загрузкой или очисткой результатов.

## Режим experiment-service

В `src/test/resources/test.properties` задайте фактическое состояние toggle сервиса:

```properties
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false
```

Значение `false` используется для основного регресса, включая два класса проверки v1 running-cache EXPLAB-2696. Для проверки режима V2 CJ сначала включите toggle на pod'ах experiment-service, дождитесь их готовности, затем сохраните в файле:

```properties
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true
```

Повторно запустите `experimentServiceRegression`. Противоположные toggle-сценарии исключаются до обнаружения тестов. Значение в тестовой конфигурации выбирает ожидаемое поведение; настройку самого сервиса меняет оператор.

Последний запуск этого этапа становится `latest`. Если нужны отдельные отчёты для обоих состояний toggle, сохраните подготовленный отчёт первого состояния до повторного запуска этапа; оба исходных прогона остаются в `runs`.

## Режимы splitter

Перед `splitterRestRegression` включите на **обоих** сервисах MAPPER и REACTIONS:

```text
splitter.config.api-config-load=true
SPLITTER_CONFIG_API_CONFIG_LOAD=true
```

Перед `splitterKafkaRegression` переключите на **обоих** сервисах:

```text
splitter.config.api-config-load=false
SPLITTER_CONFIG_API_CONFIG_LOAD=false
```

Первая строка — свойство приложения, вторая — его представление через переменную окружения. Если конкретная поставка использует имя `SPLITTER_API_CONFIG_LOAD`, применяйте этот alias в соответствии с её deployment/config map. После изменения дождитесь перезапуска и готовности обоих сервисов.

REST и Kafka требуют разных настроек стенда, поэтому их запускают отдельными командами с переключением между ними. Gradle выбирает тестовый способ загрузки конфигурации; настройку deployment меняет оператор.

Для DEV используется splitter Kafka-профиль `splitter_dev` и monitoring topic `omon_explab_splitter_log`, для ИФТ — `splitter_ift` и `explab_sowa_log`. Общие Kafka-профили других сервисов сохраняются.

## Результаты

Каталог результатов находится внутри `build` и разделён по средам:

```text
build/regression-results/
  dev/
    experiment/
      latest.txt
      runs/<run-id>/allure-results/
      runs/<run-id>/summary.json
    splitter-rest/
      latest.txt
      runs/<run-id>/allure-results/
      runs/<run-id>/summary.json
    splitter-kafka/
      latest.txt
      runs/<run-id>/allure-results/
      runs/<run-id>/summary.json
    data-operator/
      latest.txt
      runs/<run-id>/allure-results/
      runs/<run-id>/summary.json
    testops/
    testops-manifest.json
  ift/
    ...
```

`latest.txt` содержит относительный путь вида `runs/<run-id>`. При повторном запуске этапа ссылка переключается на новый прогон. `summary.json` фиксирует среду, этап, завершённость и счётчики Gradle. Падения тестовых проверок также сохраняются в результатах; подготовка не превращает их в успешные.

Рабочие данные и манифесты владения фикстурами data-operator сохраняются отдельно:

```text
build/regression-fixtures/<env>/<run-id>/
```

Этот каталог сохраняется после `cleanRegressionResults`, но удаляется обычным `clean`. Он нужен для проверки очистки тестовых данных и восстановления после прерванного прогона. Перед `clean` завершите адресную очистку данных стенда либо сохраните необходимые манифесты за пределами `build`. Исходные JSON/SQL-фикстуры в `src/test/resources` являются входными данными и не переносятся в каталог сгенерированных результатов.

## Подготовка и загрузка TestOps

Уже имеющиеся результаты перед первой отправкой не очищайте. После завершения тестов выполняйте подготовку, загрузку и последующую очистку последовательно, дожидаясь завершения каждой операции.

Подготовить последние результаты без загрузки:

```powershell
.\gradlew.bat prepareRegressionTestOpsResults --offline --console=plain
```

Подготовка проверяет завершённость этапов, соответствие среды, JSON, UUID, ссылки контейнеров и вложенные attachments. При отсутствии этапа выводится **PARTIAL REGRESSION REPORT** со списком недостающих этапов; доступные завершённые результаты можно подготовить. Существующий незавершённый этап, отсутствующий attachment или конфликт файлов блокирует замену подготовленного каталога.

В `testops` сохраняются исходные статусы. `testops-manifest.json` находится рядом и перечисляет выбранные прогоны, количество результатов и отсутствующие этапы. Если количество Allure результатов отличается от суммы тестов в Gradle summary, выводится отдельная диагностика; исходные результаты сохраняются полностью.

Загрузить результаты после проверки:

```powershell
.\gradlew.bat regressionTestOpsUpload --offline --console=plain
```

Команда сначала обновляет подготовленный каталог выбранной среды и не запускает тесты. Для отправки необходимы корпоративная сеть, доступный `allurectl` и заполненные настройки TestOps. `--offline` относится к разрешению Gradle dependencies; сама загрузка обращается к TestOps.

Проверить подготовку и параметры загрузки без отправки:

```powershell
.\gradlew.bat regressionTestOpsUpload --offline --console=plain '-PallureDryRun=true'
```

После завершённого прогона с упавшими проверками загрузку выполняйте отдельной командой: сохранённые failed/broken результаты нужны для отчёта. Между прогоном и подготовкой не запускайте `clean` или `cleanRegressionResults`. Для произвольного `test --tests` или **Run Tests** в IDEA используется `testOpsUpload` из группы **testops**, согласно отдельной инструкции.

## Очистка после завершения работы

Удалить исходные и подготовленные результаты только текущей среды:

```powershell
.\gradlew.bat cleanRegressionResults --offline --console=plain
```

При `env=dev` удаляется `build/regression-results/dev`; при `env=ift` — `build/regression-results/ift`. Каталог `build/regression-fixtures` сохраняется этой задачей. Обычный `clean` удаляет все сгенерированные результаты и манифесты внутри `build` для всех сред. Очистку выполняйте после сохранения или отправки нужных отчётов и восстановления данных стенда.
