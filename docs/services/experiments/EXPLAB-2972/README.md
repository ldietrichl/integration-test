# EXPLAB-2972 — Platform V AT

Дополнение по Excel «сценарии запуск.xlsx» и полной PDF v17: 20 сценариев UC в отдельной папке `EXPLAB_2972/launch_plan`. Запуск `explab2972LaunchPlanTest`, подготовка пользователей и предусловий — в LAUNCH-PLAN.md. Существующие 60 TP сохраняются; их номера и история Allure не смешиваются с UC. Четыре дополнительных профиля пользователей задаются через test.properties. UI-проверки и комментарий UC-05 не объявлены полностью автоматизированными.

Исполняемые HTTP/SQL-проверки находятся в стандартном sourceSet `test` проекта integration-test. Используется Platform V AT 1.10.3-beta и жизненный цикл Perfeccionista. Отдельный Maven runner, прямые JDBC/Java HttpClient и загрузка классов сервисов в JVM теста не используются.

## Архитектура

| Слой | Реализация | Ответственность |
| --- | --- | --- |
| Тесты | `src/test/java/ru/sber/qa/experiments/EXPLAB-2972` | JUnit 5 parameterized, `@Regression`, PerfeccionistaExtension, единый resource lock |
| Flow | `AbstractStatusChange2972FlowTest extends Flows` | Предусловия, регистрация фикстуры и именованный сценарий через `getFlowWithDbRest().step().run()` |
| Environment | `EnvironmentConfigWithStatusChange2972` | Наследует штатный `EnvironmentConfigWIthRestDbV2`, сохраняет Configuration/DataSource/Timeout/AllureInvocation services |
| Фикстура | `StatusChange2972Fixture` | Регистрация через FixtureService; teardown после каждого параметризованного сценария |
| REST steps | `StatusChange2972Steps` | Environment RestService/CustomRestService, RestClient, RestMatchers, HTTP evidence без заголовков |
| DB steps | `StatusChange2972DbSteps` | Environment DatabaseService/DatabaseClient, SELECT-наблюдения и экранирование скалярных значений |
| Kafka steps | `StatusChange2972KafkaSteps` | Environment KafkaService, отдельный consumer для каждого наблюдения, назначение всех партиций и фиксация offsets до бизнес-действия |
| Управление стендом | `StatusChange2972ControlSteps` | Отдельный RestService, проверка возможностей, сессия управления jobs/отказами и восстановление через close |
| Асинхронность | Awaitility | Опрос в потоке теста, чтобы сохранить контекст Environment |
| Отчётность | Allure и Gradle JUnit XML | Шаги фреймворка, HTTP/SQL attachments, исходные assertions |

## Запуск на стенде

Единственный селектор среды — `env` в `src/test/resources/test.properties`: dev, ift, ift-dm, lt. `-Denv`, `-Penv` и `ENV` среду не переключают. Endpoint выбирает штатный RestEndpointResolver, секреты — SecurePropertyResolver/ConfigurationService только из игнорируемого `secure.local.override.properties`. `secure.local.properties` остаётся версионируемым шаблоном; переменные окружения, JVM properties и `gradle.local.properties` не являются источниками секретов/подключений. Адреса: `rest.<env>.experiments.base-uri`, `rest.<env>.configuration-service.base-uri`; стандартный gateway fallback проекта сохраняется. Общий порядок — в [Конфигурации проекта](../../../project/CONFIGURATION.md).

Добавить необходимые ключи из `test-properties.example.txt`. Проверить JSON-фикстуру `src/test/resources/explab2972/experiment-template.json` по словарям и тестовым данным стенда. В `auth.primary.user-id` указать пользователя токена. Для TP-02 и TP-03 нужны отдельные реальные роли и соответствующие им токены.

Положительные TP требуют у primary прав на проверяемые действия, включая создание и согласование. В UC primary имеет строго заданные роли инициатора expCreator/spMAPPER. Запускать TP и UC следует отдельными этапами с соответствующими токеном primary и user-id; настройки sub/login UC должны соответствовать его токену. Добавлять административные роли в UC-профиль инициатора нельзя.

Для SQL-наблюдений настроить собственные ключи `db.<env>.<name>.*` в database.properties; имена клиентов задаются `explab2972.<env>.db.experiment.name` и `.db.configuration.name`. Для ift-dm используется именно ift-dm, без перехода на другую среду. Тестовый DB helper выполняет только SELECT; read-only права пользователя должны быть выданы на стороне БД.

Kafka bootstrap и TLS/SASL задаются в `kafka-consumers.properties` с префиксом `kafka_consumer.<profile>.`; EXPLAB-2972 выбирает профиль из `env`, заменяя `-` на `_`. Для `ift-dm` нужен профиль `ift_dm`. Темы наблюдения остаются в `explab2972.<env>.kafka.topics` в `test.properties`. Старые `explab2972.<env>.kafka.bootstrap.servers` и `.kafka.property.*` больше не настраивают подключение. Producer-профили хранятся отдельно в `kafka-producers.properties`.

```powershell
.\gradlew.bat propertyLayoutTest
.\gradlew.bat explab2972Test
.\gradlew.bat explab2972ManagedTest
```

`explab2972StandTest` оставлен как совместимый псевдоним обычной задачи. Управляемый набор исключён из всех остальных Gradle Test-задач, включая experimentServiceRegression. В IDEA обычные классы доступны как штатные JUnit-тесты. Для ручного запуска управляемого класса предусловия тоже обязательны.

Обычный набор — TP-01/04/05/07/16/18/19/20. Управляемый — 52 номера с исполняемой HTTP/SQL-частью; указать `managed.cases` и `case.NN.prepared=true` только после фактической подготовки. Несовместимые отказы и состояния jobs запускаются раздельно. TP-21 требует выделенную фикстуру старой версии и проверяет её через БД, поскольку V2 GET сам отвергает эту версию.

Матрица всех 60 номеров — `COVERAGE.jira.txt`, точные предусловия — `CONTROLLED-SCENARIOS.jira.txt`. Все 60 номеров имеют исполняемый код: 8 обычных и 52 управляемых. Для 16 сценариев используется контроллер окружения и прямое чтение Kafka через KafkaService; контракт и обязательные возможности описаны в CONTROL-API.md. PASS не означает проверку всех внешних эффектов исходной строки: scheduler/Kafka/audit и управляемое время требуют соответствующего наблюдения.

## Данные и результаты

Собственные данные имеют имя `EXPLAB-2972-<UUID>-...`. FixtureService удаляет только созданные данным тестом DRAFT после проверки полного UUID-префикса. Прочие статусы и предоставленные `fixture-id` сохраняются для анализа; `cleanup=retain` отключает удаление. Манифест показывает оставленные id. Неудача удаления отражается в evidence и не подменяет исходную проверку. Очереди конфигураций управляемого набора адресно очищает оператор по UUID.

* JUnit XML: `build/test-results/<task>`.
* HTML: `build/reports/tests/<task>`.
* Allure raw: `build/allure-results/<task>`.
* HTTP и манифест данных: `build/explab2972-stand/<env>/<UUID>/evidence.jsonl`.

Обычный `clean` удаляет все сгенерированные результаты и рабочие манифесты в `build`, включая регрессионные и TestOps-каталоги. До очистки сохраните нужные доказательства и завершите адресную очистку данных стенда. Не запускайте `clean` между этапами одного цикла. Статический `src/test/resources/explab2972/experiment-template.json` сохраняется.

TP-19 сохраняет строгую проверку пробелов вокруг статуса; при текущей нормализации API этот вариант падает и требует решения по контракту. TP-13 использует алгоритм v2.5, стр. 2243: просроченный autoStop отменяется и при валидном scheduleParam. Исключение на стр. 2276 относится к старому v2.2. Эти падения не переименованы в успешные тесты.

## Проверенная разметка Allure

Общий RequiredAllureLabelsExtension добавляет штатные метки TestOps: testFramework, system, layer, team, appType, functionalArea, serviceUnderTest, testStage, testEnvironment, regress и проектные теги. Код сценария дополнительно задаёт Epic/Feature, Story по названию из плана, severity (P0→critical, P1→normal, P2→minor), scenarioId, параметры набора и ссылку EXPLAB-2972 через штатный allure.link.tms.pattern. Числовые AllureId не выдумываются.

testCaseId стабилен для TP; historyId дополнительно учитывает среду. Выбор поднабора не меняет историю из-за порядкового номера параметризованного вызова. В отчёте три верхних Flow-шагa: проверка предусловий, регистрация фикстуры и очистки, именованный бизнес-сценарий. Внутри — фактические REST/DB-вызовы, проверки и HTTP/SQL-вложения; FixtureService добавляет шаги teardown к результату сценария.

Истечение ожидания бизнес-условия преобразуется в assertion failure (Allure failed); исключения подготовки/соединения остаются broken. Флаги prepared проверяются внутри первого Flow-шагa каждого TP, поэтому сбой предусловия не теряет идентичность сценария.

Добавленные TP-06/09/12/17/30/35/39–45/54/55/60 выполняются StatusChange2972ControlledScenarios. Полный перечень теперь совпадает с 60 строками исходного плана; runtime PASS/BLOCKED учитываются отдельно от наличия реализации.
