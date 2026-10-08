# Корпоративный клиент Ignite

`IgniteClientSupport.java` и `IgniteConnectionProbe.java` — изолированный клиент для
проверки подключения. Библиотеки сервиса и тестового проекта не смешиваются.
Подготовленная рабочая директория содержит корпоративный runtime в `runtime`.
Для другой версии клиента соберите пакет из утверждённых зависимостей `com.sbt`
в корпоративной сети. Локальная сборка Apache Ignite его не заменяет.

## Подготовка runtime

В Gradle UI задачи подготовки доступны в группе `service`, проверка соединения
`ignitePreflight` в группе `diagnostics`. Старые команды с `-I` остаются совместимыми.


Используйте JDK 17 и существующий Gradle Wrapper из корня проекта. Заполните Nexus credentials
в `secure.local.override.properties`, проверьте корпоративные repository URL в `gradle.properties`
и версии в `tools/ignite-client/pom-corporate.xml` относительно целевого стенда.

```powershell
.\gradlew.bat prepareCorporateIgniteClient
```

Задача использует репозитории проекта и отдельную конфигурацию зависимостей, проверяет
SHA-256, поставщиков обязательных классов и компилирует helper. Обращений к стенду нет.
Apache Ignite artifacts с group `org.apache.ignite` отклоняются. Наличие одинаковых имён
Java package у Apache и корпоративной сборки не доказывает совместимость.

Успешная сборка создаёт `runtime/corporate-ignite-runtimes/ignite-client/<runtime-sha256>`
и selector `runtime/corporate-ignite-runtimes/ignite-client/latest-runtime.txt`.
Запишите опубликованный относительный путь в `ignite.<env>.runtime.directory` файла
`src/test/resources/ignite.properties`. Selector сам по себе не выбирает runtime.
Диагностика компиляции находится в `build/ignite-client-compile`.

Проверку соединения запускайте после проверки настроек стенда:

```powershell
.\gradlew.bat ignitePreflight
```

Подготовленный generic probe runtime не содержит `LinksCacheTool`; для data-operator
фикстур нужен runtime из `tools/data-operator-explab-2974/README.md`.

## Файлы конфигурации

Среда выбирается только через `env` в `src/test/resources/test.properties`. Подключение
выбранной среды хранится только в `src/test/resources/ignite.properties` под префиксом
`ignite.<env>.`. Там находятся адреса, SSL, пути сертификатов, runtime directory и таймауты.
Имена пользователей, пароли и пароли хранилищ задаются ссылками `${SECURE_...}`;
их значения хранятся только в корневом `secure.local.override.properties`, исключённом из Git.
Пример разделения находится в `tools/ignite-client/ignite-profiles.properties.example`.

Параметры `-Penv`, `-Denv`, `-Pignite.*`, `-Dignite.*` и переменные окружения не выбирают
подключение и не заменяют секреты. Init script не пересылает их в JVM тестов.
Несекретные разрешения/выходные каталоги фикстур указываются в `test.properties`.
Внутренние переменные окружения helper-процесса заполняются Java-адаптером после выбора
профиля и не являются дополнительным публичным источником конфигурации.

## Артефакты, совместимость и clean

Новые сборки используют lock schemaVersion=2. Библиотеки находятся в общем каталоге
`runtime/corporate-ignite-libraries/<library-sha256>/lib`, а lock, helper-исходники и
описание сборки в `runtime/corporate-ignite-runtimes/<client>/<runtime-sha256>`.
Одинаковые упорядоченные наборы библиотек повторно не копируются для каждого helper.
Старые schemaVersion=1 с собственным `lib` поддерживаются; существующие каталоги
`tools/**/corporate-runtimes` патч не удаляет и настройки runtime не переключает.

Компиляция helper выполняется задачами `prepareIgniteHelpers` (data-operator) или
`prepareIgniteProbe` (диагностика). Кэш находится в
`build/ignite-helper-classes/<runtime-sha256>`. Конструктор во время теста проверяет
готовность кэша, но не компилирует исходники. Каталог результатов каждого сценария
создаётся отдельно. `dataOperatorRegression` и `ignitePreflight` сами включают
соответствующую задачу подготовки в зависимости.

`clean` блокируется при известных незавершённых или нечитаемых recovery-манифестах
Scheduler/data-operator в build. Нельзя объединять clean с тестами или подготовкой
Ignite в одном вызове. Завершите работающие прогоны перед clean: блокировка не является
межпроцессной блокировкой всех тестов и не защищает от ручного удаления файлов.

До очистки сохраните за пределами проекта runtime и общий каталог библиотек,
выбранные настройки и каталоги запусков с journal, manifest, ownership и recovery.
Для schemaVersion=1 сохраните runtime вместе с его lib. Не удаляйте манифесты ради
обхода защиты. При незавершённом восстановлении clean запускать нельзя даже после бэкапа.
После разрешённого clean установленный runtime сохраняется. Кэш helper в build
пересоздаётся задачами prepareIgniteHelpers/prepareIgniteProbe; повторно скачивать
корпоративные библиотеки из-за clean не нужно.

Наличие старого runtime в проекте не подтверждает его совместимость с текущим стендом.
Не заменяйте runtime, связанный с незавершённой фикстурой, новым без процедуры recovery.

## Исправление v22.1: подготовка без подключения

prepareIgniteHelpers и prepareIgniteProbe выбирают профиль среды и runtime, проверяют
lock, JAR и исходники, затем компилируют helper. Они не создают IgniteConfiguration
и не разрешают адреса, пароли, SSL-параметры или таймаут подключения. Значения
SET_ME в этих параметрах не мешают подготовке. Корректные env и runtime.directory
по-прежнему обязательны; отсутствующий runtime не заменяется автоматически.

Перед реальными вызовами остаётся строгая проверка IgniteConfiguration.
Успешная подготовка не означает готовности подключения и не разрешает запись.
Незаполненный профиль подключения должен быть исправлен до реального прогона.

## Перенос готовой рабочей директории

Каталог `runtime` является обязательной частью поставки, хотя исключён из Git.
Передавайте обе его подпапки: `corporate-ignite-runtimes` и `corporate-ignite-libraries`.
Текущая подготовленная рабочая директория уже содержит перенесённые корпоративные
пакеты. SHA-256 lock и helper-исходников при переносе не изменяется.
Старые пути внутри `build` поддерживаются только для явно выбранных legacy-пакетов;
они не используются как автоматическая замена отсутствующего нового runtime.
