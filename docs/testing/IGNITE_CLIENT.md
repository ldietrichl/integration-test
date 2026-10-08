# Общий клиент Ignite для тестов

Подключение к Ignite вынесено из настроек отдельной задачи в общий профиль `ignite.<env>.*`. Его используют диагностический тест инфраструктуры и адаптер фикстур data-operator. Новые тесты могут использовать тот же профиль, TLS и изолированный запуск Java, добавляя собственные операции с кэшем.

## Состав механизма

| Компонент | Назначение |
|---|---|
| `src/main/java/config/services/core/TestEnvironment.java` | Единый выбор среды для REST и Ignite |
| `src/test/java/util/ignite/EnvironmentProperties.java` | Чтение параметров выбранного профиля из `ignite.properties` |
| `src/test/java/util/ignite/IgniteConfiguration.java` | Параметры подключения выбранной среды и совместимость со старыми ключами |
| `src/test/java/util/ignite/IgniteClientRuntime.java` | Проверка библиотек по SHA256, отдельная компиляция helper-классов и запуск дочерней JVM |
| `tools/ignite-client/IgniteClientSupport.java` | Общие настройки Ignite и TLS для helper-классов |
| `tools/ignite-client/IgniteConnectionProbe.java` | Вход и получение количества кэшей без чтения строк и записи данных |
| `tools/ignite-client/corporate-client.init.gradle` | Подготовка корпоративного комплекта из репозиториев проекта |
| `tools/ignite-client/pom-corporate.xml` | Корпоративные зависимости и управление их версиями |
| `tools/ignite-client/ignite-profiles.properties.example` | Шаблон профилей без действующих реквизитов |

Библиотеки Ignite не добавляются в основной classpath тестового фреймворка. Общая задача использует репозитории и учётные данные существующего проекта. Она не добавляет репозитории, не скачивает приложение data-operator и не подключается к стенду.

## Выбор среды

Один `env` выбирает REST, Kafka, БД и Ignite. Его единственный источник — `src/test/resources/test.properties`. Аргументы JVM/Gradle и переменные окружения не переключают среду. Подключение выбранной среды находится в `src/test/resources/ignite.properties`.

Пустой `env` в файле и неизвестная среда приводят к ошибке. Перехода к DEV при таком ошибочном значении нет.

| Значения `env`, без учёта регистра | Профиль |
|---|---|
| `dev` | `ignite.dev.*` |
| `ift`, `eift`, `ift-ds`, `eift-ds` | `ignite.ift.*` |
| `ift-dm`, `eift-dm` | `ignite.ift-dm.*` |
| `lt` | `ignite.lt.*` |
| `local`, `localhost` | `ignite.local.*` |

Подчёркивание в имени среды эквивалентно дефису: например, `eift_ds` выбирает `ift`. Профиль `ift-dm` остаётся отдельным от `ift`. Настройки DEV не используются как резервные для ИФТ; отсутствие параметров выбранной среды останавливает запуск. В корпоративной поставке IFT-DM намеренно оставлен пустым: перед запуском его заполняют фактическими параметрами этого стенда, без подстановки IFT.

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

Совместимость со старыми ключами относится к содержимому профильного файла `ignite.properties`: пока в выбранной среде не задан ни один поддерживаемый ключ `ignite.<env>.*`, соединение использует прежний профиль `links.fixture.<env>.*` этой же среды из этого файла. При появлении любого нового ключа выбирается новый профиль целиком. Отсутствующие реквизиты нового профиля не дополняются старыми.

Поэтому нельзя переносить только `runtime.directory`, оставив адреса или пароль исключительно в старых ключах. Даже новый `output.directory`, таймаут или пустое значение из шаблона переключает профиль. Перенесите все используемые параметры соединения одной среды, сохранив их действующие значения. Затем отдельно перенесите флаг и каталог фикстур. При отсутствии обязательных параметров запуск завершится ошибкой до обращения к Ignite.

Для DEV и ИФТ выполняется отдельный перенос. Готовность профиля DEV не подтверждает правильность сертификатов, адресов или разрешений ИФТ. Значения старых ключей можно оставить на время проверки: после полной миграции они не участвуют в выборе соединения. Не помещайте незаполненный шаблон поверх рабочих файлов конфигурации или существующего override.

Переименование параметров не требует переименования файлов сертификатов, keystore или truststore. Можно сохранить существующие имена и пути. Можно также сохранить существующее имя секретного placeholder, если его разрешение в защищённых настройках уже работает; перенос ключа подключения сам по себе не меняет значение секрета.

### Источники конфигурации

`EnvironmentProperties` выбирает только профиль нужной среды из `src/test/resources/ignite.properties`. В нём находятся адреса, SSL, пути и типы хранилищ, runtime directory и таймауты. Пользователь, пароль и пароли хранилищ задаются ссылками `${SECURE_...}`. Значения этих ссылок читаются только из корневого игнорируемого `secure.local.override.properties`; `secure.local.properties` — версионируемый шаблон.

Пустые обязательные параметры и незаполненные placeholders отклоняются до подключения. `addresses` и `ssl.enabled` обязательны, адреса разделяются запятыми без пустых элементов. `username` и `password` задаются вместе либо оба отсутствуют, если стенд допускает соединение без этих реквизитов.

Флаги `data-operator.fixture.<env>.enabled`, выходной каталог фикстур и параметры сценариев находятся в `test.properties`. Runtime connection settings не берутся из этого файла, `gradle.local.properties`, JVM properties или переменных окружения. Init script используется для подготовки библиотек и не пересылает подключения/секреты в JVM тестов. При обычном запуске тестов подключать его повторно не требуется.

## TLS и форматы хранилищ

`ignite.<env>.ssl.enabled` должен быть ровно `true` либо `false`. При `true` клиент использует обычную проверку сертификатов через JDK TrustManagerFactory. `trustAll` отключён. При отсутствии явно заданного truststore используются доверенные сертификаты JDK; это не отключает проверку.

Для клиентской аутентификации TLS задайте `ssl.key-store.path`, `ssl.key-store.type` и соответствующий пароль. Для отдельного набора доверенных сертификатов задайте аналогичные `ssl.trust-store.*`. Указывайте фактический тип явно — `JKS` или `PKCS12`. Расширение файла не определяет его формат: файл с именем `.jks` может содержать PKCS12. Если тип отсутствует, текущий helper использует `JKS`.

Общий helper загружает хранилища через JDK, создаёт KeyManagerFactory и TrustManagerFactory, затем инициализирует SSLContext. PEM-пары напрямую этим интерфейсом не принимаются; для них заранее нужен корректный JKS/PKCS12. Рабочее хранилище не нужно конвертировать только из-за изменения имён параметров. В `.properties` удобно указывать Windows-пути с `/`, чтобы обратные слеши не обрабатывались как escape-последовательности.

Значения подключения передаются helper-процессу через внутренние `IGNITE_CLIENT_*` после выбора одной среды. Унаследованные переменные этого протокола и `LINKS_IGNITE_*` удаляются перед установкой выбранных значений. В аргументный файл пароль не записывается. Для старых неизменяемых helper-комплектов адаптер data-operator формирует прежние имена `LINKS_IGNITE_*` из того же выбранного профиля.

## Подготовка и три уровня проверки

Команды выполняются в PowerShell из корня проекта с JDK 17. Подготовка библиотек использует корпоративные репозитории из `gradle.properties` и Nexus credentials из `secure.local.override.properties`; обращений к стенду нет. Версии в `pom-corporate.xml` необходимо сверить с целевым сервисом. Артефакты Apache Ignite не заменяют корпоративные зависимости `com.sbt`.

Общий комплект для диагностики соединения:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle prepareCorporateIgniteClient
Get-Content -LiteralPath '.\build\corporate-ignite-runtimes\ignite-client\latest-runtime.txt'
```

Он создаётся в `build/corporate-ignite-runtimes/ignite-client/<runtime-sha256>`. Для data-operator подготовьте отдельный комплект, содержащий `LinksCacheTool` и `LinksCacheSchema`:

```powershell
.\gradlew.bat -I tools/data-operator-explab-2974/corporate-client.init.gradle prepareExplab2974CorporateClient
Get-Content -LiteralPath '.\build\corporate-ignite-runtimes\data-operator-explab-2974\latest-runtime.txt'
```

Его путь — `build/corporate-ignite-runtimes/data-operator-explab-2974/<runtime-sha256>`. Задачи проверяют SHA-256, состав корпоративных библиотек и компиляцию helper. Подробности в [README общего клиента](../../tools/ignite-client/README.md) и [README клиента фикстур](../../tools/data-operator-explab-2974/README.md).

Запишите подходящий относительный путь в `ignite.<env>.runtime.directory` файла `ignite.properties`. Selector `latest-runtime.txt` сам по себе не выбирает runtime. Среда уже должна быть выбрана ключом `env` в `test.properties`. Для каждой среды используются её отдельные адреса, сертификаты и credentials. Отсутствующие исторические runtime необходимо восстановить из корпоративной резервной копии; новая сборка не подтверждает совместимость со старым lease.

| Проверка | Что подтверждает | Что записывает |
|---|---|---|
| `ru.sber.qa.infrastructure.ignite.IgniteConnectionTest` | Соединение, вход и получение количества кэшей; пустой кластер допустим | Диагностику внутри `build` |
| `ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksConnectionTest` | Вход, чтение кэшей data-operator и совместимость схемы хранения | Диагностику внутри `build` |
| `DataOperatorLinksFunctionalFlowTest` / `DataOperatorLinksValidationFlowTest` | Функциональные и валидационные варианты endpoint | Собственные данные сценариев на стенде и их очистку |

После настройки выбранного стенда общая диагностика запускается так:

```powershell
.\gradlew.bat test --tests 'ru.sber.qa.infrastructure.ignite.IgniteConnectionTest' --console=plain
```

Проверка схемы data-operator использует адаптер фикстур и его отдельный runtime. Задайте `data-operator.fixture.<env>.enabled=true` в `test.properties`. Сам диагностический тест не пишет данные в Ignite:

```powershell
.\gradlew.bat test --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksConnectionTest' --console=plain
```

Прогон endpoint-сценариев после выполнения предусловий:

```powershell
.\gradlew.bat test --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest' --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksValidationFlowTest' --console=plain
```

Вход в Ignite не доказывает доступность REST endpoint, права записи или успешность очистки. Функциональные тесты проверяют ответы сервиса и нулевой остаток собственных данных.

Диагностика сборки находится в `build/ignite-client-compile` и `build/explab-2974-client-compile`. Probe пишет в `build/ignite/<env>`, фикстуры — в настроенный каталог внутри `build`; исторические корпоративные JSON сохранены в `build/regression-fixtures`. JUnit, Allure, regression и TestOps также создаются внутри `build`.

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
- Новый комплект data-operator создаётся отдельной задачей и содержит собственные helper-исходники. Generic probe runtime не считается готовым runtime фикстур. При использовании других helper-исходников fingerprint их выполнения отличается; это требует отдельной проверки, а не изменения старого манифеста.

Перед заменой комплекта, helper-исходников или выполнением `clean` завершите очистку незавершённых фикстур. Если остаются активные lease, сохраните во внутреннем хранилище вне проекта полный каталог конкретного запуска с manifest/ownership/recovery и точный runtime: lock, библиотеки и исходники helper. Сохраните соответствующие настройки среды. Одного отчёта Allure для восстановления недостаточно.

Обычный `clean` удаляет весь `build`, включая runtime и исторические JSON фикстур. После него для нового запуска подготовьте библиотеки повторно и проверьте `ignite.<env>.runtime.directory`. При восстановлении старого запуска верните сохранённые каталоги в прежние относительные пути; не подменяйте исходный runtime новым fingerprint. Старые внешние корпоративные runtime не удаляются и не переписываются поставкой.

Восстановление data-operator проверяет, что манифест находится в каталоге выбранной среды, а среда, URI сервиса, адреса Ignite, schema 3 и fingerprint совпадают. При расхождении оно останавливается. Не исправляйте fingerprint, адреса или владельцев в манифесте для обхода проверки. Чтобы восстановить старую фикстуру после миграции имён, в полном новом профиле этой же среды можно явно выбрать прежний runtime-каталог, сохранив исходные параметры соединения и каталог фикстур.

## Границы проверки

Компиляция и проверки файлов конфигурации не подтверждают доступ к корпоративному Ignite. После восстановления недостающих runtime и сертификатов выполните в корпоративной сети подготовку клиента, диагностику соединения каждой настроенной среды, затем проверку схемы и функциональные сценарии. Результаты прошлых запусков относятся к соответствующим версиям и стендам.
