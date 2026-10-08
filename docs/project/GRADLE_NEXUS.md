# Gradle из Nexus: единая конфигурация и сборка без тестов

Дата: 2026-09-15. Проект: integration-test. Gradle 8.4, JDK 17.

## Результат локальной попытки

Выполнялась команда:

```powershell
.\gradlew.bat testClasses --console=plain --no-daemon
```

Первый запуск wrapper не смог создать lock-файл в C:/.gradle/wrapper/dists.
После явного задания GRADLE_USER_HOME=<user-home>/.gradle повторный запуск
перешёл к загрузке дистрибутива и завершился:

```text
Downloading https://nexus-ci.delta.sbrf.ru/repository/raw-public-cache/gradle_distr_proxy/gradle-8.4-bin.zip
java.net.UnknownHostException: nexus-ci.delta.sbrf.ru
```

Обе попытки завершились с кодом 1. Gradle 8.4 не был запущен:
конфигурация проекта, компиляция Java и тесты не выполнялись.
Это блокировка доступом к корпоративному DNS/Nexus, а не ошибка компилятора.
Новый compileWithoutTests пока не выполнен. Совместимость плагинов и исходников
с 8.4, включая ранее обсуждавшийся DictionariesV1Steps, остаётся неподтверждённой.

## Где должны находиться настройки

| Назначение | Единственное место для изменения |
| --- | --- |
| URL ZIP, версия в имени ZIP, SHA-256, расположение дистрибутива в кеше | gradle/wrapper/gradle-wrapper.properties |
| Maven URL корпоративных репозиториев и существующие версии параметризованных зависимостей | корневой gradle.properties |
| Основные задачи, зависимости, JDK и настройки основной сборки | корневой build.gradle.kts |
| Подключение плагинов и проверка версии до конфигурации проекта | корневой settings.gradle.kts |
| Существующие секреты для Maven-репозиториев | игнорируемый secure.local.override.properties; не включать в патчи |
| Машинный путь общего кеша | переменная окружения пользователя GRADLE_USER_HOME, вне проекта |

Проверка версии в settings и задача wrapper теперь читают одни и те же
gradle-wrapper.properties. В них больше нет самостоятельных копий номера версии,
адреса дистрибутива и его SHA-256. Значения Maven URL остаются в gradle.properties;
их не нужно дублировать в test.properties, настройках scheduler или командах запуска.

Это единая конфигурация основной сборки, но не один физический файл.
Wrapper должен узнать URL ещё ДО загрузки Gradle и выполнения build.gradle.kts,
а pluginManagement выполняется на стадии settings. Перенос этих параметров
целиком в build.gradle.kts нарушит стандартный bootstrap.

Автономный local-services/scheduler-regression/container-runner.gradle и
локальные *.init.gradle оставлены без объединения. Это отдельная офлайн-обвязка,
использующая экспортированный runtime. Все Gradle-файлы проекта в один файл
в этой доработке НЕ сведены; новые задачи основной сборки добавляйте только в
корневой build.gradle.kts. Объединение локального runner требует отдельной
проверки контейнерного запуска, которая здесь не выполнялась.

Существующий mavenCentral() для зависимостей не удалён. Загрузка самого Gradle
из Nexus и политика «все зависимости только через Nexus» не одно и то же.
Прежде чем убирать Maven Central, необходимо подтвердить наличие всех внешних
зависимостей в корпоративной группе Nexus; сейчас её доступность не подтверждена.

## В проекте остаётся только загрузчик, а не дистрибутив

Сохраняются gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar и
gradle/wrapper/gradle-wrapper.properties. Маленький wrapper JAR не является
дистрибутивом Gradle. ZIP и распакованный Gradle хранятся в общем пользовательском
кеше GRADLE_USER_HOME/wrapper/dists, зависимости в GRADLE_USER_HOME/caches.

Файл wrapper уже настроен на корпоративный Nexus и содержит:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://nexus-ci.delta.sbrf.ru/repository/raw-public-cache/gradle_distr_proxy/gradle-8.4-bin.zip
distributionSha256Sum=3e1af3ae886920c3ac87f7a91f816c0c7c436f276a6eefdb3da152100fef72ae
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Nexus должен возвращать оригинальный gradle-8.4-bin.zip с указанной SHA-256.
После первой успешной загрузки wrapper повторно использует кеш для того же URL.
Не храните распакованный Gradle, caches или wrapper/dists в архиве проекта.

Проектная .gradle может оставаться: Gradle хранит там собственные данные проекта.
Это не повод направлять GRADLE_USER_HOME внутрь проекта. Уже существующие старые
дистрибутивы и кеши не перемещались и не удалялись. Не удаляйте их во время сборок.

## Настройка на ВАРМ

В PowerShell под тем же пользователем, который запускает IDEA:

```powershell
$gradleCache = Join-Path $env:USERPROFILE '.gradle'
$env:GRADLE_USER_HOME = $gradleCache
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME', $gradleCache, 'User')
```

Это изменит переменную пользователя для всех его Gradle-проектов.
Текущая PowerShell-сессия получает значение сразу. После установки перезапустите
IDEA, чтобы она унаследовала окружение. Путь автоматически относится к пользователю
ВАРМ, а не к локальному пользователю Dietrich. На этой машине постоянная
переменная пользователя данной доработкой не менялась.

В IDEA в Build Tools / Gradle выберите Wrapper и Gradle JVM: JDK 17.
Если Gradle user home задан явно, укажите тот же внешний пользовательский кеш.
Не выбирайте дистрибутив из каталога внутри проекта. Для CI используйте такие же
wrapper-файлы и внешний рабочей копии кеш с правами учётной записи агента.

Не задавайте GRADLE_USER_HOME в test.properties: wrapper не читает настройки тестов.
Не используйте -g .gradle и устаревшие launch-скрипты, направляющие кеш в проект.
Не требуется GRADLE_HOME с установленным дистрибутивом для работы через wrapper.

## Сборка без тестов

Из корня корпоративного проекта:

```powershell
Set-Location 'C:/Work/IdeaProjects/integration-test'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
.\gradlew.bat compileWithoutTests --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Сборка без тестов завершилась ошибкой' }
```

Задача зависит от assemble, testClasses и bypassToolClasses.
Она компилирует main/test/служебные исходники reporting и собирает основной JAR,
но не запускает test, сервисные регрессы или bypassTests.
Генерация registration-only bypassTests через сканер не включена: она не требуется
для проверки компиляции обычных тестов и может зависеть от доступности стенда.
Покрытие требований и работоспособность сценариев такой сборкой не проверяются.

Защита на графе задач отклоняет совместный запуск compileWithoutTests с задачами
типа Test, включая скрытые транзитивные зависимости. Не добавляйте build/check
или сервисные задачи к этой команде. Не используйте build -x test как гарантию,
что пользовательские тестовые задачи не выполнятся.

Дистрибутив и Maven-зависимости при необходимости будут скачаны: первый запуск
требует доступа к Nexus. Gradle-компиляция всё равно выполняет build-логику и
annotation processors; это не изолированная проверка недоверенного проекта.

## Если Nexus недоступен

- UnknownHostException: проверьте доступ к корпоративной сети и DNS с ВАРМ.
- HTTP 404: согласуйте фактическое размещение оригинального ZIP в Nexus.
- HTTP 401/403: согласуйте права и аутентификацию загрузчика для этого репозитория.
- TLS/PKIX: используйте утверждённую корпоративную CA-цепочку для JDK загрузчика;
  не отключайте проверку сертификата или имени сервера.
- Несовпадение SHA-256: остановитесь; не удаляйте проверку для обхода ошибки.

Секреты Maven из secure.local.override.properties читаются только после старта Gradle.
Они не авторизуют первоначальную загрузку ZIP автоматически. При закрытом raw-репозитории
потребуется отдельная согласованная bootstrap-аутентификация wrapper через пользовательскую
Gradle-конфигурацию. Не помещайте пароль в distributionUrl, репозиторий или архив патча.
OpenShift-токен не является учётными данными Nexus.

## Источники

Порядок загрузки, bootstrap-файлы и аутентификация описаны в
[официальной документации Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).
Разделение пользовательского и проектного кеша описано в
[Gradle-managed Directories](https://docs.gradle.org/current/userguide/directory_layout.html).
Контрольная сумма взята из
[официального списка Gradle](https://gradle.org/release-checksums/).
