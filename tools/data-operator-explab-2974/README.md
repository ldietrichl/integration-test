# Корпоративный клиент фикстур EXPLAB-2974

`LinksCacheTool.java` выполняет probe, подготовку метаданных, проверку принадлежности
созданных записей и очистку. `LinksCacheSchema.java` задаёт ожидаемый контракт BinaryObject.
Этот клиент не поднимает сервис и не является заглушкой.

В поставке находятся исходники, `pom-corporate.xml` и перенесённый корпоративный
runtime в каталоге `runtime`. Apache Ignite JAR не используются. Новые версии
корпоративных библиотек собираются с доступом к утверждённым репозиториям.

## Подготовка runtime

В Gradle UI задачи подготовки доступны в группе `service`, проверка соединения
`ignitePreflight` в группе `diagnostics`. Старые команды с `-I` остаются совместимыми.


Descriptor фиксирует клиентскую часть зависимостей data-operator `06fd6acb646`:
`com.sbt.ignite:ignite-core/ignite-indexing:17.6.0`,
`com.sbt.security.ignite:security-core/security-ldap:17.6.0` и
`javax.cache:cache-api:1.1.1`; ограничения Jackson задаются BOM сервиса.
Перед запуском сверьте версии с действующим стендом. Maven отдельно устанавливать не нужно.

```powershell
.\gradlew.bat prepareExplab2974CorporateClient
```

Задача работает на JDK 17, использует репозитории проекта и Nexus credentials из
`secure.local.override.properties`, разрешает отдельный dependency graph, проверяет
SHA-256 и компилирует helper. Стенд не вызывается. `org.apache.ignite` artifacts отклоняются.
Порядок direct dependencies сохраняется; допустимое перекрытие `BinaryObject` между
`com.sbt.ignite:ignite-core` и `ignite-binary-api` отражается в lock и diagnostics.

Успешная сборка создаёт `runtime/corporate-ignite-runtimes/data-operator-explab-2974/<runtime-sha256>`
и `runtime/corporate-ignite-runtimes/data-operator-explab-2974/latest-runtime.txt`.
Скопируйте опубликованный относительный путь в `ignite.<env>.runtime.directory` файла
`src/test/resources/ignite.properties`. Диагностика лежит в `build/explab-2974-client-compile`.
Для probe/фикстур используйте соответствующие задачи проекта; включение записи требует
`data-operator.fixture.<env>.enabled=true` в `test.properties`. Выходной каталог, например
`data-operator.fixture.<env>.output.directory=build/explab-2974-fixtures/<env>`, также
задаётся в `test.properties` и должен оставаться внутри build.

Сборка и проба с реальными корпоративными com.sbt библиотеками проверяются в корпоративной
сети. Локальная компиляция против Apache Ignite не подменяет такую проверку.

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
