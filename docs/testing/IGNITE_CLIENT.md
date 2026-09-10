# Общий клиент Ignite для тестов

Подключение к Ignite вынесено из настроек отдельной задачи в общий профиль `ignite.<env>.*`. Его используют диагностический тест инфраструктуры и адаптер фикстур data-operator. Новые тесты могут использовать тот же профиль, TLS и изолированный запуск Java, добавляя собственные операции с кэшем.

## Состав механизма

| Компонент | Назначение |
|---|---|
| `src/main/java/config/services/core/TestEnvironment.java` | Единый выбор среды для REST и Ignite |
| `src/test/java/util/ignite/EnvironmentProperties.java` | Чтение параметров с приоритетами источников |
| `src/test/java/util/ignite/IgniteConfiguration.java` | Параметры подключения выбранной среды и совместимость со старыми ключами |
| `src/test/java/util/ignite/IgniteClientRuntime.java` | Проверка библиотек по SHA256, отдельная компиляция helper-классов и запуск дочерней JVM |
| `tools/ignite-client/IgniteClientSupport.java` | Общие настройки Ignite и TLS для helper-классов |
| `tools/ignite-client/IgniteConnectionProbe.java` | Вход и получение количества кэшей без чтения строк и записи данных |
| `tools/ignite-client/corporate-client.init.gradle` | Подготовка корпоративного комплекта и передача параметров `-P`/`-D` в JVM тестов |
| `tools/ignite-client/pom-corporate.xml` | Корпоративные зависимости и управление их версиями |
| `tools/ignite-client/ignite-profiles.properties.example` | Шаблон профилей без действующих реквизитов |

Библиотеки Ignite не добавляются в основной classpath тестового фреймворка. Общая задача использует репозитории и учётные данные существующего проекта. Она не добавляет репозитории, не скачивает приложение data-operator и не подключается к стенду.

## Выбор среды

Один `env` выбирает и REST, и Ignite. Порядок выбора: JVM property `env`, затем переменная `ENV`, затем `env` из `src/test/resources/test.properties`. При запуске через новый init-скрипт явный `-Penv` передаётся в дочерние JVM и имеет приоритет перед `-Denv`.

Пустой явно переданный `env` и неизвестная среда приводят к ошибке. Перехода к DEV при таком ошибочном значении нет.

| Значения `env`, без учёта регистра | Профиль |
|---|---|
| `dev` | `ignite.dev.*` |
| `ift`, `eift`, `ift-ds`, `eift-ds` | `ignite.ift.*` |
| `ift-dm`, `eift-dm` | `ignite.ift-dm.*` |
| `lt` | `ignite.lt.*` |
| `local`, `localhost` | `ignite.local.*` |

Подчёркивание в имени среды эквивалентно дефису: например, `eift_ds` выбирает `ift`. Профиль `ift-dm` остаётся отдельным от `ift`. Настройки DEV не используются как резервные для ИФТ; отсутствие параметров выбранной среды останавливает запуск.

## Имена параметров и миграция

В таблице `<env>` — нормализованная среда из предыдущего раздела.

| Старый ключ | Новый ключ |
|---|---|
| `links.fixture.<env>.ignite.addresses` | `ignite.<env>.addresses` |
| `links.fixture.<env>.ignite.username` | `ignite.<env>.username` |
| `links.fixture.<env>.ignite.password` | `ignite.<env>.password` |
| `links.fixture.<env>.ignite.ssl.enabled` | `ignite.<env>.ssl.enabled` |
| `links.fixture.<env>.ignite.ssl.keyStore` | `ignite.<env>.ssl.key-store.path` |
| `links.fixture.<env>.ignite.ssl.keyStoreType` | `ignite.<env>.ssl.key-store.type` |
| `links.fixture.<env>.ignite.ssl.keyStorePassword` | `ignite.<env>.ssl.key-store.password` |
| `links.fixture.<env>.ignite.ssl.trustStore` | `ignite.<env>.ssl.trust-store.path` |
| `links.fixture.<env>.ignite.ssl.trustStoreType` | `ignite.<env>.ssl.trust-store.type` |
| `links.fixture.<env>.ignite.ssl.trustStorePassword` | `ignite.<env>.ssl.trust-store.password` |
| `links.fixture.<env>.runtime.directory` | `ignite.<env>.runtime.directory` |
| `links.fixture.<env>.enabled` | `data-operator.fixture.<env>.enabled` |
| `links.fixture.<env>.output.directory` | `data-operator.fixture.<env>.output.directory` |

Два дополнительных общих параметра:

| Ключ | Значение по умолчанию |
|---|---|
| `ignite.<env>.output.directory` | `build/ignite/<env>` для общей диагностики и её компиляции |
| `ignite.<env>.operation-timeout-seconds` | `60`; допустимы целые значения от `1` до `3600` |

Общий `output.directory` не заменяет каталог фикстур data-operator. Их каталог по умолчанию сохранён: `build/explab-2974-fixtures/<env>`. Это позволяет находить прежние манифесты восстановления. Идентификаторы сценариев, контракт хранения `explab-2974-storage-v1` и префиксы принадлежащих тестам точек также сохранены.

### Перенос профиля целиком

Пока в выбранной среде не задан ни один поддерживаемый ключ `ignite.<env>.*`, соединение использует прежний профиль `links.fixture.<env>.*` этой же среды. При появлении любого нового ключа выбирается новый профиль целиком. Отсутствующие реквизиты нового профиля не дополняются старыми.

Поэтому нельзя переносить только `runtime.directory`, оставив адреса или пароль исключительно в старых ключах. Даже новый `output.directory`, таймаут или пустое значение из шаблона переключает профиль. Перенесите все используемые параметры соединения одной среды, сохранив их действующие значения. Затем отдельно перенесите флаг и каталог фикстур. При отсутствии обязательных параметров запуск завершится ошибкой до обращения к Ignite.

Для DEV и ИФТ выполняется отдельный перенос. Готовность профиля DEV не подтверждает правильность сертификатов, адресов или разрешений ИФТ. Значения старых ключей можно оставить на время проверки: после полной миграции они не участвуют в выборе соединения. Не помещайте незаполненный шаблон поверх рабочего защищённого файла.

Переименование параметров не требует переименования файлов сертификатов, keystore или truststore. Можно сохранить существующие имена и пути. Можно также сохранить существующее имя секретного placeholder, если его разрешение в защищённых настройках уже работает; перенос ключа подключения сам по себе не меняет значение секрета.

### Приоритет источников

Для каждого полного ключа `EnvironmentProperties` использует:

1. JVM property с полным именем.
2. Переменную окружения: все точки и дефисы заменяются на `_`, имя переводится в верхний регистр.
3. Настройки проекта через `SecureLocalConfigScope`, включая `secure.local.override.properties` перед `secure.local.properties`.
4. `src/test/resources/test.properties`.

Пример соответствия: `ignite.ift.ssl.key-store.path` → `IGNITE_IFT_SSL_KEY_STORE_PATH`; `data-operator.fixture.dev.enabled` → `DATA_OPERATOR_FIXTURE_DEV_ENABLED`.

Пустое присутствующее значение блокирует источники ниже него. Обязательный параметр в этом случае приводит к ошибке; незаполненные placeholders тоже отклоняются. `addresses` и `ssl.enabled` обязательны, адреса разделяются запятыми без пустых элементов. `username` и `password` задаются вместе либо оба отсутствуют, если стенд допускает соединение без этих реквизитов.

Для гарантированной передачи командных параметров используйте `-I tools/ignite-client/corporate-client.init.gradle` и при подготовке клиента, и при запуске тестов. Скрипт передаёт `env`, `ignite.*`, `data-operator.fixture.*` и совместимые `links.fixture.*` в задачи `Test` и `JavaExec`, даже если такого ключа ещё нет в защищённом файле. Явные `-P` имеют приоритет перед `-D`; скрипт не выводит их значения. Не полагайтесь на передачу новых ключей штатным build-файлом без этого init-скрипта: корпоративные версии build-файла могут различаться.

Некоторые корпоративные build-файлы поднимают все secure-настройки до JVM properties. Новый init удаляет неявно переданные параметры перечисленных профилей `dev`, `ift`, `ift-dm`, `lt`, `local` из задач и заново применяет явные `-P`/`-D`. Остальные значения Java читает из профильных переменных окружения, затем secure-файлов и ресурса. Поэтому отдельные task-specific `systemProperty` для этих пространств имён замените явными параметрами запуска или настройками профиля. Непрофильные настройки, включая `links.fixture.manifest`, сохраняются; существующий выбор `env` из build сохраняется при отсутствии явного `-Penv`/`-Denv`. Для проверок среды используйте команды с явным `-Penv` ниже.

## TLS и форматы хранилищ

`ignite.<env>.ssl.enabled` должен быть ровно `true` либо `false`. При `true` клиент использует обычную проверку сертификатов через JDK TrustManagerFactory. `trustAll` отключён. При отсутствии явно заданного truststore используются доверенные сертификаты JDK; это не отключает проверку.

Для клиентской аутентификации TLS задайте `ssl.key-store.path`, `ssl.key-store.type` и соответствующий пароль. Для отдельного набора доверенных сертификатов задайте аналогичные `ssl.trust-store.*`. Указывайте фактический тип явно — `JKS` или `PKCS12`. Расширение файла не определяет его формат: файл с именем `.jks` может содержать PKCS12. Если тип отсутствует, текущий helper использует `JKS`.

Общий helper загружает хранилища через JDK, создаёт KeyManagerFactory и TrustManagerFactory, затем инициализирует SSLContext. PEM-пары напрямую этим интерфейсом не принимаются; для них заранее нужен корректный JKS/PKCS12. Рабочее хранилище не нужно конвертировать только из-за изменения имён параметров. В `.properties` удобно указывать Windows-пути с `/`, чтобы обратные слеши не обрабатывались как escape-последовательности.

Значения подключения передаются helper-процессу через внутренние `IGNITE_CLIENT_*` после выбора одной среды. Унаследованные переменные этого протокола и `LINKS_IGNITE_*` удаляются перед установкой выбранных значений. В аргументный файл пароль не записывается. Для старых неизменяемых helper-комплектов адаптер data-operator формирует прежние имена `LINKS_IGNITE_*` из того же выбранного профиля.

## Подготовка и три уровня проверки

Команды ниже выполняются отдельно в PowerShell на корпоративном компьютере, из `C:\Work\IdeaProjects\integration-test`. Локальная сборка или доступ к стенду для подготовки документа не выполнялись.

Для русскоязычного вывода терминала:

```powershell
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc
```

Подготовить общий комплект из зависимостей, уже доступных в корпоративном Gradle-кэше:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle prepareCorporateIgniteClient -x testOpsUpload --console=plain --offline
```

Требуется JDK 17 или новее. Задача разрешает отдельную конфигурацию `corporateIgniteClientLibraries`, проверяет библиотеки и компилирует только два общих helper-класса. В POM сохранены корпоративные Ignite и security-модули версии `17.6.0` и прежние BOM. При отсутствии зависимостей в кэше подготовку на корпоративном компьютере можно повторить без `--offline`; используются только репозитории, уже настроенные в проекте.

Готовый каталог находится в `tools/ignite-client/corporate-runtimes/<sha256>`. Путь публикуется в `tools/ignite-client/corporate-runtimes/latest-runtime.txt` только после успешной проверки и компиляции:

```powershell
Get-Content -LiteralPath '.\tools\ignite-client\corporate-runtimes\latest-runtime.txt'
```

Запишите полученный путь в `ignite.dev.runtime.directory` в составе полностью перенесённого профиля DEV. Для ИФТ выберите свой `ignite.ift.runtime.directory` явно. Один совместимый библиотечный комплект допустимо использовать в нескольких профилях, но его выбор и реквизиты задаются отдельно. Указатель `latest-runtime.txt` не выбирается автоматически для всех сред.

| Проверка | Что подтверждает | Что записывает |
|---|---|---|
| `ru.sber.qa.infrastructure.ignite.IgniteConnectionTest` | Соединение, вход и получение количества кэшей; пустой кластер допустим | Только локальные диагностические файлы |
| `ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksConnectionTest` | Вход, чтение пяти кэшей data-operator и совместимость схемы хранения | Только локальные диагностические файлы |
| Два класса FunctionalFlowTest и ValidationFlowTest | 70 функциональных и 103 валидационных варианта endpoint | Функциональные сценарии создают собственные данные и выполняют их очистку |

Общая диагностика DEV, без зависимости от data-operator и флага фикстур:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle test -Penv=dev --tests 'ru.sber.qa.infrastructure.ignite.IgniteConnectionTest' --rerun-tasks -x testOpsUpload --console=plain --offline
```

Общая диагностика ИФТ после заполнения отдельного профиля:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle test -Penv=ift --tests 'ru.sber.qa.infrastructure.ignite.IgniteConnectionTest' --rerun-tasks -x testOpsUpload --console=plain --offline
```

Проверка схемы data-operator использует адаптер фикстур, поэтому требует `data-operator.fixture.dev.enabled=true` либо действующего legacy-флага этой среды. Сам диагностический тест не пишет данные в Ignite:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle test -Penv=dev --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksConnectionTest' --rerun-tasks -x testOpsUpload --console=plain --offline
```

Прогон endpoint-сценариев после готовности нужного обработчика в развёрнутом сервисе:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle test -Penv=dev --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest' --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksValidationFlowTest' --rerun-tasks -x testOpsUpload --console=plain --offline
```

Вход в Ignite не доказывает доступность REST endpoint, права записи и успешность очистки. Смена имён параметров не исправляет отсутствующий обработчик API. Функциональные тесты сохраняют исходные ожидания ответа и проверку нулевого остатка собственных данных.

Диагностика общей сборки: `build/ignite-client-compile/dependency-diagnostics.json` и `build/ignite-client-compile/<sha256>/compile.log`. Для результатов запуска используются `build/ignite/<env>/probe-*`, для data-operator — `build/explab-2974-fixtures/<env>`. JUnit и Allure остаются в каталогах, выбранных существующей конфигурацией проекта.

## Использование в других тестах

Тест создаёт `new IgniteConfiguration()` и передаёт его в `IgniteClientRuntime`. Не требуется ссылка на `LinksFixtureConfiguration`, флаг data-operator или номер задачи. Логику предметной области реализуйте отдельным helper-классом в каталоге инструментов соответствующего сервиса. Он компилируется вместе с `IgniteClientSupport.java` и использует:

```java
try (var client = org.apache.ignite.Ignition.startClient(
        IgniteClientSupport.configuration(addressesFromManifest))) {
    // Операции и проверки, требуемые конкретному тесту.
}
```

`addressesFromManifest` в этом фрагменте — значение `configuration.required("addresses")`, которое тест заранее записал в свой манифест. Helper не выбирает среду самостоятельно. Приватный ключ, пароль и другие секреты в манифест не записываются.

Пример создания runtime для будущего helper `ServiceCacheProbe`:

```java
var configuration = new IgniteConfiguration();
Path output = configuration.outputDirectory().resolve("service-cache-check");
var runtime = new IgniteClientRuntime(
        configuration,
        List.of(
                configuration.runtimeDirectory().resolve("IgniteClientSupport.java"),
                Path.of("tools/service-cache/ServiceCacheProbe.java")),
        "ServiceCacheProbe",
        output,
        "SERVICE_CACHE_RESULT=");
```

Этот пример предполагает новый общий комплект, содержащий `IgniteClientSupport.java`, и собственный helper, который ещё нужно реализовать. Helper получает два аргумента: имя операции и путь существующего манифеста. После успешных проверок он печатает одну строку `SERVICE_CACHE_RESULT=` с JSON. Тест вызывает `runtime.call("probe", manifestPath)` и отдельно проверяет поля возвращённого JSON. Если helper завершился с ненулевым кодом, истёк таймаут или строка результата отсутствует, runtime завершает вызов ошибкой и сохраняет диагностические файлы.

Список source-файлов должен содержать все необходимые helper-исходники. Для общего диагностического теста порядок — `IgniteClientSupport.java`, затем `IgniteConnectionProbe.java`. В этой поставке общий support находится в default package, поэтому новый helper, вызывающий его напрямую, также должен находиться в default package. Сам runtime поддерживает полное имя main-класса, но перенос общего support в Java-пакет потребует согласованного изменения исходников и нового комплекта: класс из именованного пакета не может импортировать класс из default package.

Общий runtime предоставляет соединение и запуск, но не определяет владение тестовыми данными. Тест, который изменяет кэш, обязан определить уникальное владение своими данными, безопасную очистку и проверку её результата. Пример готового такого адаптера — `LinksFixtureSession` для двух собственных splitting points; глобальная очистка кэшей в нём не используется.

## SHA256, старые комплекты и восстановление

Библиотеки проверяются по `client-libraries.lock.json`; порядок classpath берётся из lock. Прямые зависимости идут перед транзитивными. Сохранена проверенная политика корпоративной пары `ignite-core`/`ignite-binary-api`: core стоит первым, оба JAR сохраняются, провайдеры ключевых классов и хэши их байтов записываются в диагностику.

Fingerprint выполнения рассчитывается как SHA256 байтов lock-файла, за которыми следуют байты переданных helper-исходников в заданном порядке. Имя каталога нового общего комплекта рассчитывается по lock и двум общим исходникам; их список также записан в `helperSources`. Компиляция отделена по fingerprint и защищена блокировкой между потоками/JVM.

Для data-operator есть два случая:

- Старый комплект содержит `LinksCacheSchema.java` и `LinksCacheTool.java`: адаптер использует именно эти неизменяемые копии, в прежнем порядке. Исторический `fixtureRuntimeSha256`, schema 3 манифеста и путь фикстур сохраняют смысл.
- Новый общий комплект содержит общие helpers, а оба feature helper-файла берутся из `tools/data-operator-explab-2974` проекта. Fingerprint фикстуры включает эти feature-исходники и поэтому отличается от SHA каталога общего комплекта. Это ожидаемо: проверяются разные наборы исходников.

Перед заменой комплекта или feature helper-исходников завершите очистку незавершённых фикстур. Новый общий комплект сам по себе не архивирует версии feature helpers из проекта. Для восстановления после их последующего изменения потребуется сохранить или восстановить исходные байты этих файлов вместе с нужным lock и библиотеками. Старые каталоги `tools/data-operator-explab-2974/corporate-runtimes` не удаляйте и не переписывайте.

Восстановление data-operator проверяет, что манифест находится в каталоге выбранной среды, а среда, URI сервиса, адреса Ignite, schema 3 и fingerprint совпадают. При расхождении оно останавливается. Не исправляйте fingerprint, адреса или владельцев в манифесте для обхода проверки. Чтобы восстановить старую фикстуру после миграции имён, в полном новом профиле этой же среды можно явно выбрать прежний runtime-каталог, сохранив исходные параметры соединения и каталог фикстур.

## Границы подтверждённой проверки

Корпоративный комплект Ignite `17.6.0` и прежний адаптер до этого рефакторинга уже прошли вход и чтение кэшей на DEV; функциональный прогон подтвердил работу подготовки и очистки собственных фикстур. Это не является результатом запуска новой версии общего кода и не подтверждает ИФТ.

Для текущего изменения выполняется статическая проверка исходников, имён и состава поставки. Новую компиляцию и диагностические команды нужно выполнить на корпоративном компьютере. Порядок: подготовка общего клиента, общая диагностика каждой настроенной среды, затем проверка схемы сервиса и его функциональные сценарии.
