# Структура Gradle после разделения

Дата: 2026-09-29.

Рабочая директория: `<workspace>/BACK/00_projects/integration_test_ready_20260929`.
Корпоративный исходник `I:/share/integration-test` не изменялся.

## Что изменилось

Корневой `build.gradle.kts` сокращен с 2206 до 125 строк.
В нем остались плагины, координаты проекта, Java 17, wrapper, задача компиляции,
типизированная настройка плагина Allure и явный порядок подключения модулей.
Классы Allure оставлены в корне из-за области видимости classpath плагина.

В `gradle/build-logic` находятся 13 Kotlin DSL модулей:

| Файл | Ответственность |
| --- | --- |
| configuration.gradle.kts | Чтение настроек, выбор окружения, безопасные пути результатов, общие функции журналирования |
| dependencies.gradle.kts | Репозитории и зависимости приложения |
| eligibility.gradle.kts | Source sets служебных генераторов, аудит тегов, исключения сценариев |
| allure-artifacts.gradle.kts | Проверка, объединение и безопасная очистка файлов результатов |
| bypass.gradle.kts | Генерация и отдельный запуск регистрационных сценариев |
| diagnostics.gradle.kts | Диагностика splitter и локальная проверка расположения настроек |
| testops-upload.gradle.kts | Проверка параметров и явная загрузка в TestOps |
| test-conventions.gradle.kts | Общие настройки Java/JUnit, журналирования и Allure |
| regression.gradle.kts | Единые точки входа регресса, eligibility, результаты отдельных прогонов и диагностика scheduler |
| testops-results.gradle.kts | Подготовка результатов, очистка и ограничения совместного запуска |
| tickets.gradle.kts | Сценарии EXPLAB-2972 и их изоляция |
| ignite.gradle.kts | Подготовка корпоративных helpers и диагностический вход Ignite |
| task-guards.gradle.kts | Понятные ошибки для удаленных старых имен задач |

Существующие Groovy-модули `gradle/architecture` сохранены.
Разделение не вводит buildSrc, новые плагины, дополнительные зависимости или репозитории.

## Контракт между модулями

Настройки и функции передаются через именованный `platformBuildContext`.
Каждый потребитель объявляет типизированные адаптеры; ссылки на общие значения
получаются лениво. Это необходимо, поскольку локальные объявления Kotlin DSL
не видны между разными applied scripts.
Имена и порядок подключения модулей в корне являются частью сборки.
Не переносите только один `build.gradle.kts`: нужен также весь `gradle/build-logic`.

Значения секретов в отчеты проверки не выгружаются.
Профили подключения, секретные файлы, версии зависимостей, условия включения
сценариев и код тестов этим изменением не редактировались.

## Запуск через Gradle UI

После переноса нажмите Reload All Gradle Projects в IDEA.
В группе `regression` остаются ровно пять входов:

- `dataOperatorRegression`
- `experimentServiceRegression`
- `schedulerRegression`
- `splitterKafkaRegression`
- `splitterRestRegression`

Диагностика находится в `diagnostics`, задачи по заявкам в `tickets`,
bypass/TestOps и подготовка окружения в `service`.
Служебные зависимости не являются дополнительными пользовательскими регрессами.

Разделение не снимает требований к заполнению профилей и согласованию
изменяющих стенд сценариев. Параметр разрешения изменений scheduler
не включен автоматически. REST и Kafka splitter запускаются отдельно.

## Резервная копия и перенос

Резервная копия исходного корневого скрипта уже сохранена локально:
`<workspace>/BACK/05_outputs_results/project-backups/integration_test_ready_20260929/gradle-refactor/build.gradle.kts`.

Перед заменой файлов в корпоративной копии закройте запущенные Gradle-задачи
и сделайте резервную копию ее файлов сборки:

```powershell
$project = 'C:\Work\IdeaProjects\integration-test'
$backup = Join-Path (Split-Path $project -Parent) ('integration-test-gradle-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'build.gradle.kts') -Destination $backup -ErrorAction Stop
Copy-Item -LiteralPath (Join-Path $project 'gradle') -Destination $backup -Recurse -ErrorAction Stop
Write-Host "Backup: $backup"
```

Переносите подготовленную рабочую директорию целиком либо согласованную пару:
`build.gradle.kts` и `gradle/build-logic`.
Не перезаписывайте корпоративные секретные файлы и локальные профили из
другого окружения ради этого рефакторинга.

Для отката замените `build.gradle.kts` его корпоративной резервной копией
и верните сохраненную папку `gradle`, затем перезагрузите Gradle в IDEA.
Если оставшиеся новые файлы `gradle/build-logic` не удалены, старый корневой
скрипт их не подключает; сам факт наличия этих файлов ничего не запускает.

## Проверки и ограничения

- Kotlin DSL компилируется офлайн.
- Снимки 84 задач до и после совпадают: имена, группы, dependsOn,
  mustRunAfter, finalizedBy, фильтры Test и число параллельных процессов.
- Повторная компиляция основных, тестовых и служебных исходников успешна.
- Скомпилированы 613 сгенерированных bypass-методов; они не исполнялись.
- Оба Ignite helper подготовлены без подключения к Ignite.
- Сохранились 18 предупреждений Java об устаревшем API и предупреждение Gradle о совместимости с Gradle 9.
- Для локальной компиляции использованы существующие локальные библиотеки
  и временный offline init script, не включенный в конфигурацию проекта.
- Локальный кэш не содержит optional Fabric8 HTTP provider. Только при
  локальной проверке helpers временный init использовал compileClasspath;
  исходные корпоративные задачи по-прежнему используют runtimeClasspath.
- Корпоративные тесты, доступ к БД/стенду и отправка TestOps не выполнялись.
  Офлайн-компиляция не подтверждает доступность корпоративных сервисов.

Результаты проверок и журналы находятся в `docs/project/gradle-refactor-validation`.
