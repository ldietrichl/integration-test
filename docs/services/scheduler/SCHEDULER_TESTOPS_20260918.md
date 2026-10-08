# Scheduler regression: выгрузка в Allure TestOps

## Задачи в группе testops

| Задача | Действие |
| --- | --- |
| `prepareSchedulerRegressionTestOpsResults` | Подготавливает только scheduler, без тестов и отправки |
| `schedulerRegressionTestOpsUpload` | Подготавливает и отправляет только scheduler, без тестов |
| `prepareRegressionTestOpsResults` | Объединяет доступные последние прогоны сервисов, теперь включая scheduler |
| `regressionTestOpsUpload` | Отправляет общий регрессионный пакет нескольких сервисов |
| `testOpsUpload` | Прежний экспорт selected-test/IDE raw Allure; не выбирает scheduler автоматически |

В группе `regression` остается одна поддерживаемая задача запуска scheduler: `schedulerRegression`. Диагностические прогоны scheduler не включаются в регрессионную выгрузку.

## Источник и защита от смешивания

По умолчанию scheduler-задачи читают `build/regression-results/<env>/scheduler-regression/latest.txt` и только указанный им каталог `runs/<runId>/allure-results`. Не используется общий `build/allure-results` и не выбирается другая папка по времени изменения.

У выбранного прогона должен быть `summary.json` с `completed=true`, тем же окружением и стадией `scheduler-regression`. Завершенный прогон с падениями допустим. Если последний начатый прогон не завершился, выгрузка останавливается без отката к более старому успешному прогону.

Подготовленный пакет: `build/regression-results/<env>/scheduler-testops`. Его манифест: `scheduler-testops-manifest.json` рядом с пакетом. В манифесте сохраняются runId, исходная summary, количество и статусы результатов, сравнение счетчиков JUnit/Allure, пути и SHA-256 исходных файлов.

Ссылки контейнеров на результаты и ссылки на вложения проверяются во время подготовки. Изменение исходных файлов/указателя во время копирования прерывает подготовку. Исходный прогон не очищается. `failed`, `broken`, `skipped`, `unknown` не заменяются на `passed`; повторные результаты не удаляются для улучшения статистики.

## Команды на ВАРМ

Из корня корпоративного проекта после завершения тестового процесса:

```powershell
.\gradlew.bat prepareSchedulerRegressionTestOpsResults --console=plain
```

Примерная отправка без вызова allurectl и без сетевой загрузки:

```powershell
.\gradlew.bat schedulerRegressionTestOpsUpload -PallureDryRun=true --console=plain
```

Реальная отправка существующих результатов:

```powershell
.\gradlew.bat schedulerRegressionTestOpsUpload --console=plain
```

Для конкретного сохраненного прогона передается только имя каталога runId, не абсолютный путь. Пример использует ID из присланного прогона:

```powershell
.\gradlew.bat schedulerRegressionTestOpsUpload -PschedulerTestOpsRun=20260918-133828-358-76ab296f --console=plain
```

Архивный каталог должен существовать в `build/regression-results/<env>/scheduler-regression/runs`. Этот параметр также поддерживает `prepareSchedulerRegressionTestOpsResults`. Он не предназначен для общей многосервисной выгрузки.

Тесты и выгрузка намеренно разделены. Если `schedulerRegression` завершился с ошибками тестов, отдельная команда отправки сохранит их результаты. Нет автоматической публикации через finalizer и нет зависимости от `bypassTests`.

Повторная команда реальной отправки повторно вызывает allurectl и может создать новый launch. Пакет не обещает идемпотентность удаленного сервиса. После сетевой ошибки с неизвестным итогом сначала выясните состояние launch в TestOps.

## Параметры TestOps

Используется существующий корпоративный механизм: `allureEndpoint`, `allureProjectId`, `allurectlPath`, `allureLaunchName`, `allureLaunchTags` и настройки TLS. Их текущие значения не заменены. Имя scheduler-launch дополнительно содержит `Scheduler regression [<env>]`.

`allureToken` остается в локальном `secure.local.override.properties`, как в исходном build.gradle.kts. Передавать его через `-P`, коммитить или добавлять в архив не нужно. Исходный secret-resolver читает `ALLURE_TOKEN` как альтернативное имя в этом же secure-файле, а не как переменную окружения; этот контракт в патче не изменен.

Для пути к allurectl сохраняются существующие варианты: настроенный `allurectlPath`/`ALLURECTL_PATH`, `allure/bin/allurectl.exe` либо PATH. Никакой бинарник allurectl в этот патч не добавлен.

## Результаты запуска кнопкой IDE

Если IDE запускает JUnit напрямую или Gradle `test --tests`, такой запуск обычно пишет в общий raw-каталог, а не создает scheduler `latest.txt`. Не используйте scheduler-export для угадывания такого прогона.

1. До отдельного IDE-прогона выполните `./gradlew.bat cleanTestOpsResults` и убедитесь, что другие тесты не работают. Эта команда предназначена для стандартного selected-test raw-каталога; регрессионные `runs` она сохраняет.
2. Запустите только нужную папку/классы scheduler из IDE и дождитесь завершения.
3. Выполните `./gradlew.bat testOpsUpload --console=plain`. Будут отправлены все selected-test raw-результаты из этого источника, не только scheduler по имени пакета.

Для нестандартного IDE raw-пути существующий параметр `-PtestOpsSourceResultsDir=<полный-путь>` позволяет указать отдельный источник. Он должен принадлежать выбранному окружению и не содержать результаты других запусков, если нужен один launch без смешивания.

## Ограничения

- Не совмещайте `cleanRegressionResults` с тестами или экспортом в одной Gradle-команде: новый guard это запрещает.
- Не запускайте два процесса подготовки/загрузки одного и того же bundle одновременно. Манифест и проверки источника не являются межпроцессной блокировкой всей удаленной загрузки.
- Несовпадение числа JUnit и raw Allure выводится предупреждением и сохраняется в манифесте; raw-результаты не отбрасываются.
- Ошибку восстановления jobs в конце JUnit следует разбирать по root/Gradle результату и workload-артефактам. Статусы тестов в TestOps не заменяют проверку успешного завершения восстановления.
- Подготовка, dry-run, компиляция и отправка при создании этого патча не выполнялись.
