# Регрессионный прогон experiment-service

Документ описывает ручной запуск experiment-service регресса в корпоративной среде после наката тестового пакета EXPLAB-2696/2928/2929 и исправлений ложных падений EXPLAB-2539/legacy-v2.

## Предусловия

- Открыт корень готового корпоративного `integration-test`; старые overlay-архивы повторно не применяются.
- Корпоративная ветка использует `platform-v-at-framework=1.10.3-beta`.
- В `src/test/resources/test.properties` настроен REST base-uri нужного стенда.
- В `src/test/resources/database.properties` настроено подключение к БД ExpLab.
- На стенде применены миграции experiment-service, включая `status_change_element` и `exp_status_change_request`.
- EXPLAB-2930 временно исключена через `config/reporting/outdated-tests.properties`, так как реализация заявки откатилась. Тесты остаются в коде, но не должны попадать в статистику и `build/allure-results`.

## Базовая подготовка PowerShell

```powershell
cd <path-to-corporate-integration-test>
```

Выберите `env` только в `src/test/resources/test.properties`. REST находится в этом же файле, БД — в `database.properties`, Kafka — в `kafka-consumers.properties` / `kafka-producers.properties`, Ignite — в `ignite.properties`. Ключ `encryption.password`, если нужен для `ENC(...)`, и рабочие секреты находятся только в `secure.local.override.properties`. Аргументы запуска и переменные окружения не переопределяют подключения, секреты и среду. Профиль `ift-dm` отдельный и оставлен пустым до заполнения фактических параметров этого стенда.

## Конфигурация секретов

Стендовые адреса и несекретные параметры остаются в `src/test/resources/*.properties`. Чувствительные значения в этих файлах задаются только через плейсхолдеры вида `${SECURE_*}`.

Единый справочник секретных переменных находится в `secure.local.properties`. Его можно пушить: внутри только имена переменных, комментарии и безопасные `<SET_ME_...>` заглушки. Реальные значения для запуска находятся только в `secure.local.override.properties`; этот файл игнорируется Git. В CI необходимо подготовить такой файл в рабочем каталоге проекта средствами корпоративного хранилища секретов, сохраняя его вне Git и публикуемых результатов. Переменные окружения и аргументы JVM/Gradle не являются источником секретов.

`tokenName` и `tokenPassword` из `secure.local.override.properties` подхватываются в `settings.gradle.kts` для Gradle pluginManagement и в `build.gradle.kts` для обычных зависимостей. Архитектурно runtime-секреты подключены так же, как в шаблоне `platform-v-at-gradle-draft-master`: `SecureLocalConfig` использует `Owner @Sources`, `CustomTestConfig` использует штатный `SecretPropertyConverter`, а `SecureAwareConfigurationService` раскрывает `${SECURE_*}` на уровне `ConfigurationService` для Kafka/DB/Container scope. Значения могут быть открытыми, `ENC(...)` или ссылками `vault.*`.

Перед каждым отдельным прогоном задавайте `$env:JAVA_TOOL_OPTIONS` заново: в этой переменной ниже передаются только состояние проверяемого toggle, кодировка и таймауты сценария. Параметры подключения и секреты в неё не добавляются.

Все результаты и фикстуры находятся в `build`. Не запускайте `clean` между этапами, результаты которых нужно объединить. Перед общей очисткой завершите восстановление данных на стенде; при активных lease сохраните каталог запуска с manifest/ownership и исходный runtime из `build` во внутреннем хранилище вне проекта. Исторические JSON перенесены в `build/regression-fixtures` и также удаляются обычным `clean`.

## Основной регресс, toggle=false

Перед запуском убедитесь, что на pod'ах experiment-service выключен toggle:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat test --tests "ru.sber.qa.experiments.*" --tests "ru.sber.qa.controllers.refBookController.*"

.\gradlew.bat allureReport
```

В этом прогоне EXPLAB-2930 не должна попасть в `build/allure-results`.

## Toggle-регресс, toggle=true

Перед запуском включите toggle на pod'ах experiment-service и перезапустите pod'ы:

```text
EXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true
```

Команда:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=true -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningV1CacheV2CjEnabled2696FlowTest"

.\gradlew.bat allureReport
```

## Проверка только новых задач

Для EXPLAB-2928/2929:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2928.*" --tests "ru.sber.qa.experiments.EXPLAB_2929.*"

.\gradlew.bat allureReport
```

Для EXPLAB-2696 при toggle=false:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -DEXPERIMENT_SERVICE_V2_CJ_EXPERIMENTS_ENABLED=false -Dexlab2696.running.cache.wait.timeout.ms=60000 -Dexlab2696.running.cache.wait.poll.ms=3000"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningExperimentsV1Cache2696FlowTest" --tests "ru.sber.qa.experiments.EXPLAB_2696.RunningSplitsV1Cache2696FlowTest"

.\gradlew.bat allureReport
```

Для EXPLAB-2539 после исправления DTO-ожиданий:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

.\gradlew.bat test --tests "ru.sber.qa.experiments.EXPLAB_2539.*"

.\gradlew.bat allureReport
```

## Allure

HTML-отчет после Gradle-запуска:

```text
build/reports/allure-report/allureReport/index.html
```

Raw results:

```text
build/allure-results
```

Архив отчета:

```powershell
Compress-Archive `
  -Path "build\reports\allure-report\allureReport\*" `
  -DestinationPath "build\reports\allure-report\experiment-service-regression-allure.zip" `
  -Force
```

## Ожидаемый результат после текущего patch-набора

- EXPLAB-2930 отсутствует в `build/allure-results`.
- Legacy/v2 broken по `EnvironmentConfigWithRestV2.getServiceConfigurations()` больше не воспроизводятся.
- EXPLAB-2539 проверяет актуальный v15 DTO: `splittingPointCode` и `splittingPointName`.
- EXPLAB-2928 может продолжать падать до исправления схемы `status_change_element` на сервисе.
- EXPLAB-2929 может продолжать падать до исправления десериализации `CompleteActionRequestDto` на сервисе.
- EXPLAB-2696 требует отдельные прогоны под `toggle=false` и `toggle=true`.
- EXPLAB-2559 нужно запускать пользователем со scope на `MAPPER` или тестовыми данными под доступный splitting point.
