# Ускорение Ignite-фикстур EXPLAB-2974 на DEV и ИФТ

Патч сокращает служебную работу подготовки и очистки данных. REST-запросы, наборы данных, точные проверки связей и проверки нулевого остатка сохраняются. Корпоративная совместимость новой версии должна быть проверена командами ниже: при подготовке патча JVM, Gradle и стенды не запускались.

## Три изменения

1. Для `param_cache` и `data_source_cache` чтение собственных записей выполняется адресно через `getAll(points)`. Ключ этих двух кэшей — строка `splittingPoint`. Состав запрашиваемых ключей ограничен двумя точками текущего manifest. Удаление по-прежнему проверяет соответствие ожидаемого значения.
2. Подсчёт `splitting_object_cache` и проверка ключей/полей объектов объединены в один проход. Проверки неожиданных объектов, дублей, полного состава полей и значений сохраняются.
3. `prepare → REST-загрузка → verify → REST links → cleanup` одного сценария использует одну дочернюю JVM и один `IgniteClient`. Между сценариями клиент не разделяется. Хеширование библиотек, fingerprint runtime и правила компиляции не заменены кэшем.

Для полного штатного lifecycle число полных обходов кэшей меняется так:

| Работа | До патча | После патча |
|---|---:|---:|
| `prepare`: отсутствие старых строк + итоговое состояние | 10 | 6 |
| `verify`: количества + полная проверка объектов | 6 | 3 |
| `cleanup`: удаление + нулевой остаток | 10 | 6 |
| Один сценарий | 26 | 15 |
| 70 функциональных сценариев | 1820 | 1050 |

Убраны 10 обходов двух metadata-кэшей и один повторный object-обход на сценарий. **Это примерно 42,3% сокращения числа полных обходов, а не обещание сокращения времени на 42,3%.** Адресные операции, проверки метаданных, REST, TLS, работа сервиса и компиляция тоже занимают время. Таблица не включает отдельный class-level probe, повторные запуски и аварийное восстановление. Для падения бизнес-assertion с нормально завершённым fixture lifecycle расчёт тот же.

## Протокол и восстановление

Новый helper запускается в режиме `session`; версия протокола — `1`, префикс сообщений — `IGNITE_SESSION=`. Приветствие и ответы команд содержат protocol/id, результаты — `pid` и `sessionId`. Для успешно выполненной операции выводится `operationMillis`, а приветствие содержит `connectionMillis`. Обычный вывод Ignite остаётся в журнале helper.

Ошибка команды `prepare` или `verify` не закрывает живой клиент: следующая команда `cleanup` может удалить частично подготовленные данные. При таймауте, нарушении протокола или смерти процесса runtime сначала подтверждает завершение child, затем допускает отдельный one-shot `cleanup`. Одновременная работа двух helper с одной фикстурой не допускается. Таймаут операции не является ограничением общей длительности сценария или паузы между командами.

Старые команды `probe`, `prepare`, `verify`, `state`, `cleanup` и префикс `FIXTURE_RESULT=` сохранены. `LinksFixtureRecovery` продолжает использовать one-shot cleanup. Для него обязательны исходный `fixtureRuntimeSha256`, совпадение окружения/адресов/точек и `ownership.json`. Новый runtime не выдаётся за старый: изменения исходников helper меняют fingerprint.

## До замены файлов и переключения runtime

Завершите текущие тесты. До установки сохраните резервную копию заменяемых файлов по инструкции поставки. Сохраните существующие immutable каталоги `tools/data-operator-explab-2974/corporate-runtimes/<hash>` и каталоги fixture: они нужны для восстановления. Не заменяйте файлы внутри старого `<hash>`.

Проверьте manifest и claim **до `clean`, установки новых файлов и смены выбранного runtime**. Для стандартного каталога выводятся только состояние и несекретный fingerprint:

```powershell
Set-Location -LiteralPath 'C:\Work\IdeaProjects\integration-test'
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

$igniteFixtureRoot = Join-Path (Get-Location).Path 'build\explab-2974-fixtures'
if (Test-Path -LiteralPath $igniteFixtureRoot) {
    Get-ChildItem -LiteralPath $igniteFixtureRoot -Filter 'fixture-manifest.json' -File -Recurse |
        ForEach-Object {
            $fixtureState = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
            [pscustomobject]@{
                Manifest = $_.FullName
                Environment = $fixtureState.environment
                Status = $fixtureState.status
                Claim = Test-Path -LiteralPath (Join-Path $_.DirectoryName 'ownership.json')
                RuntimeSha256 = $fixtureState.fixtureRuntimeSha256
            }
        } | Format-Table -AutoSize
}
```

Если `data-operator.fixture.<env>.output.directory` переопределён, проверьте соответствующий каталог вместо стандартного. Файл claim после успешного cleanup может сохраняться как доказательство владения: ориентируйтесь также на `status=cleaned` и все нулевые `remaining` в `fixture-cleanup.json`.

Для незавершённого manifest с claim сначала выберите его прежнее окружение и runtime. В IDEA запустите `util.dataoperator.LinksFixtureRecovery.main` с test classpath проекта, рабочим каталогом `C:\Work\IdeaProjects\integration-test` и одним аргументом — абсолютным путём к этому `fixture-manifest.json`. Требуется `Fixture recovery completed` и нулевой остаток во всех пяти кэшах. Не исправляйте hash или claim вручную. Если recovery не завершился, сохраните данные и устраните причину до установки; `clean` удаляет стандартные fixture-доказательства.

## Пересоздание корпоративного immutable runtime

После восстановления старых fixture и копирования файлов патча выполните из того же PowerShell:

```powershell
.\gradlew.bat -I tools/data-operator-explab-2974/corporate-client.init.gradle prepareExplab2974CorporateClient -x testOpsUpload --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Corporate runtime preparation failed; do not use an old latest-runtime.txt' }

$igniteRuntimeRelative = (Get-Content -LiteralPath '.\tools\data-operator-explab-2974\corporate-runtimes\latest-runtime.txt' -Raw).Trim()
if ($igniteRuntimeRelative -notmatch '^tools/data-operator-explab-2974/corporate-runtimes/[0-9a-f]{64}$') {
    throw 'Unexpected corporate runtime selector'
}
$igniteRuntimeDirectory = Join-Path (Get-Location).Path $igniteRuntimeRelative
foreach ($helperName in 'LinksCacheSchema.java', 'LinksCacheTool.java') {
    $sourceHash = (Get-FileHash -LiteralPath (Join-Path '.\tools\data-operator-explab-2974' $helperName) -Algorithm SHA256).Hash
    $bundleHash = (Get-FileHash -LiteralPath (Join-Path $igniteRuntimeDirectory $helperName) -Algorithm SHA256).Hash
    if ($sourceHash -ne $bundleHash) { throw "Prepared runtime contains another source version: $helperName" }
}
if (-not (Test-Path -LiteralPath (Join-Path $igniteRuntimeDirectory 'client-libraries.lock.json'))) {
    throw 'Prepared runtime lock is missing'
}
Write-Output "Runtime: $igniteRuntimeRelative"
```

Задача читает `pom-corporate.xml`, использует репозитории/доступы уже настроенного проекта, проверяет библиотеки и компилирует **новые** helper sources с `--release 17`. Она не обращается к Ignite/REST и не добавляет эти библиотеки в test classpath. Каталог `<hash>` и `latest-runtime.txt` публикуются только после успешной проверки; при неизменном составе допустимо переиспользование того же каталога.

Само обновление `tools/LinksCacheTool.java` недостаточно: если выбранный runtime содержит свою пару helper sources, `LinksFixtureRuntime` использует именно её. Поэтому пересоздайте bundle и явно выберите новый путь. Старые каталоги и незавершённые manifest задача не изменяет.

## Выбор DEV или ИФТ

Рабочая среда задаётся одной строкой `env=dev` либо `env=ift` в **существующем** `src/test/resources/test.properties`. `-Penv` для этих запусков не нужен. Адреса, аутентификация и TLS каждой среды остаются в её собственном профиле.

Выведите готовые несекретные строки выбора runtime:

```powershell
Write-Output "ignite.dev.runtime.directory=$igniteRuntimeRelative"
Write-Output "ignite.ift.runtime.directory=$igniteRuntimeRelative"
```

Вручную обновите соответствующий ключ в существующем `secure.local.override.properties`: DEV — `ignite.dev.runtime.directory`, ИФТ — `ignite.ift.runtime.directory`. Можно указать один и тот же проверенный bundle для обеих сред; это не объединяет их адреса и учётные данные. Не заменяйте защищённый файл целиком и не выводите его содержимое. Для IDEA и последующих терминалов это постоянный выбор. Старые JVM VM options с runtime-путём не должны его перекрывать.

Эти команды рассчитаны на уже настроенные канонические профили `ignite.dev.*` и `ignite.ift.*`. Если среда целиком использует прежние `links.fixture.<env>.*`, обновляйте её `links.fixture.<env>.runtime.directory` либо сначала переносите весь профиль: добавление единственного канонического runtime-ключа отключит legacy fallback для остальных параметров. Это предотвращает смешивание двух наборов доступов.

Временный выбор для групповой задачи `dataOperatorRegression` возможен в текущем PowerShell:

```powershell
$env:IGNITE_DEV_RUNTIME_DIRECTORY = $igniteRuntimeRelative
$env:IGNITE_IFT_RUNTIME_DIRECTORY = $igniteRuntimeRelative
```

Для generic `test --tests ...` ниже используйте постоянный выбор в файле: общий Gradle-блок может передать значение защищённого файла как JVM property, чей приоритет выше переменной PowerShell. Прямой IDEA Run также не наследует переменные, созданные только в уже открытом терминале IDEA. Для снятия временного выбора:

```powershell
Remove-Item -LiteralPath 'Env:\IGNITE_DEV_RUNTIME_DIRECTORY' -ErrorAction SilentlyContinue
Remove-Item -LiteralPath 'Env:\IGNITE_IFT_RUNTIME_DIRECTORY' -ErrorAction SilentlyContinue
```

## Проверка на корпоративном компьютере

Выполните последовательность отдельно для `env=dev` и для `env=ift`, каждый раз меняя только строку среды в `test.properties`. Для выбранной среды должны быть настроены REST/Ignite и `data-operator.fixture.<env>.enabled=true`. На этом этапе не нужен общий `clean`: он уничтожит fixture-диагностику предыдущего запуска.

Сначала компиляция, тест протокола с JDK-only fake child без подключения к Ignite, затем probe реального Ignite без записи:

```powershell
.\gradlew.bat testClasses -x testOpsUpload --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Project compilation failed' }

.\gradlew.bat test --tests 'util.ignite.IgniteHelperSessionTest' --rerun-tasks -x testOpsUpload --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Helper session protocol tests failed' }

.\gradlew.bat test --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksConnectionTest' --rerun-tasks -x testOpsUpload --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Ignite fixture probe failed' }
```

У нового `probe-result.json` должно быть `sessionProtocol=1`, а также `schemaCompatible=true`, все пять `cacheReadVerified` и `mutations=false`. Проверить последний probe стандартного каталога, не читая защищённые настройки:

```powershell
$igniteEnvMatches = @(Select-String -LiteralPath '.\src\test\resources\test.properties' -Pattern '^\s*env\s*=\s*(dev|ift)\s*$')
if ($igniteEnvMatches.Count -ne 1) { throw 'Keep exactly one env=dev or env=ift line in test.properties' }
$igniteSelectedEnvironment = $igniteEnvMatches[0].Matches[0].Groups[1].Value
$igniteSelectedFixtureRoot = Join-Path '.\build\explab-2974-fixtures' $igniteSelectedEnvironment
$igniteProbeFile = Get-ChildItem -LiteralPath $igniteSelectedFixtureRoot -Filter 'probe-result.json' -File -Recurse |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if ($null -eq $igniteProbeFile) { throw 'Fresh probe-result.json was not found' }
$igniteProbe = Get-Content -LiteralPath $igniteProbeFile.FullName -Raw | ConvertFrom-Json
if ($igniteProbe.sessionProtocol -ne 1) { throw 'Selected helper has no session protocol v1; rebuild and select the new corporate runtime' }
$igniteProbe | Select-Object sessionProtocol, schemaCompatible, mutations, storageContract
```

Для нестандартного fixture output подставьте его в `$igniteSelectedFixtureRoot`. Probe проверяет подключение и чтение; права записи/удаления проверяются только следующим lifecycle.

Первый smoke удобно сделать в IDEA: `DataOperatorLinksFunctionalFlowTest.shouldCalculateExactLinks`, выбранная invocation `SL-01: Базовый расчёт; SP2 исключена`. Если дерево invocation ещё не сформировано, запустите весь функциональный класс следующей командой. Перед новым selected прогоном можно отдельно очистить стандартные raw Allure через `cleanTestOpsResults`; эта задача сохраняет fixture и `regression-results`:

```powershell
.\gradlew.bat cleanTestOpsResults -x testOpsUpload --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Allure raw cleanup failed' }

$igniteValidationStarted = Get-Date
.\gradlew.bat test --tests 'ru.sber.qa.dataoperator.EXPLAB_2974.DataOperatorLinksFunctionalFlowTest' --rerun-tasks -x testOpsUpload --console=plain
$igniteValidationExit = $LASTEXITCODE
$igniteValidationElapsed = (Get-Date) - $igniteValidationStarted
Write-Output "Functional run exit=$igniteValidationExit; wallSeconds=$($igniteValidationElapsed.TotalSeconds)"
```

Здесь запускаются 70 функциональных сценариев. Негативный validation-класс не нужен для измерения этой оптимизации: он не создаёт Ignite fixture. Существующие ошибки контракта сервиса могут оставить отдельные tests failed; их не считать исправленными ускорением и не ослаблять assertions. Любые новые ошибки lifecycle, protocol, schema, verify или cleanup требуют разбора.

После smoke и полного класса проверьте:

- У одной фикстуры `Fixture prepared: helper session`, `Fixture loaded and verified` и `Fixture cleanup: zero owned rows` содержат одинаковые `pid`/`sessionId` и `executionMode=session`. Между сценариями `sessionId` различается; PID операционная система впоследствии может переиспользовать.
- `fixture-loaded.json` содержит `objectsVerified=true` и прежние ожидаемые количества. `fixture-cleanup.json` — пять нулевых `remaining`, manifest — `status=cleaned`. Проверяйте также упавшие бизнес-сценарии.
- `session-*.log` содержит `IGNITE_SESSION=` с protocol `1`. `operationMillis` сравнивает собственно команды helper; `connectionMillis` — запуск подключения. Полная длительность JUnit/Allure дополнительно включает REST и остальной lifecycle.
- Если был аварийный fallback, cleanup имеет `executionMode=one-shot` и `recoveredAfterSessionFailure=true`. Такой запуск нужно анализировать отдельно, он не доказывает работу обычного пути с одним клиентом.

Для проверки полного набора дата-оператора после этой диагностики:

```powershell
.\gradlew.bat dataOperatorRegression -x testOpsUpload --console=plain
```

Среда по-прежнему берётся из `test.properties`. Эта команда выполняет весь настроенный regression scope дата-оператора и складывает raw результаты в `regression-results/<env>/data-operator/runs/<id>/allure-results`; время этого более широкого набора не следует сравнивать с одним функциональным классом.

## Что сохранить для сравнения и диагностики

Сохраните JUnit XML, raw Allure, журналы `session-*.log`, `fixture-manifest.json`, `ownership.json`, `fixture-loaded.json`, `fixture-cleanup.json`, новый runtime hash и diagnostics компиляции. Сравнивайте один и тот же класс, окружение, количество сценариев и состояние backend; отдельно учитывайте повторные попытки, fallback и первый прогрев/компиляцию. Для обсуждения достаточно выбранных несекретных metrics и санитизированных evidence.

Статическая проверка патча включает структуру `getAll` для двух String-key кэшей, сохранение ownership/zero-count проверок, единственный object scan при verify, one-shot совместимость, версии протокола и файлов доставки. Фактическая компиляция новым корпоративным клиентом, один PID/client на сценарий, время выполнения и восстановление при обрыве подтверждаются только корпоративным прогоном. Результаты прежней версии не подменяют эту проверку.
