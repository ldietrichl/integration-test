# EXPLAB_2885 — запуск сценариев, поставка v6

Все сценарии и вспомогательные классы находятся в `src/test/java/ru/sber/qa/splitter/EXPLAB_2885`.
Подготовка использует корпоративные `WorkloadRunScopeExtension`, `KubernetesWorkloadService`, `KubernetesWorkloadSteps` и `KubernetesWorkloadControl`, как REACTIONS-сценарии EXPLAB_2690. Собственных Kubernetes-клиентов и CLI-команд нет.

## Привязка стенда как в EXPLAB_2690

`Precalc2885StandBindings` воспроизводит подготовку из `gradle/build-logic/tickets.gradle.kts` EXPLAB_2690 (блок REACTIONS): Deployment `splitter-reactions-service`, ConfigMap правил `splitter-reactions-service-lib`, readiness timeout 120 секунд / poll 2000 мс. Ранее эти параметры передавала только специальная Gradle-задача 2690; обычный splitterRestRegression их не получал.

В v5 недостающие `stand.<env>.workloads.<workload>.*` параметры добавляются в тестовую JVM перед запуском общего механизма. Файлы stand.properties/test.properties не меняются. Явные настройки, включая secure overlay, не заменяются. Значения остаются до завершения тестовой JVM, чтобы восстановление ConfigMap в root-store выполнялось с той же адресацией. Разрешения мутаций и подключения автоматически не включаются.

Это defaults из реализации 2690, а не результат нового чтения кластера. При отличающейся адресации используются существующие настройки стенда или `explab2885.reactions.stand.deployment` / `explab2690.reactions.stand.deployment`. Все Kubernetes-операции по-прежнему выполняет общий Fabric8 с проверкой Deployment и его ссылок на ConfigMap.

## Подготовка перед каждым сценарием

По ссылкам Deployment определяется ConfigMap с флагом предрасчёта. Общий механизм читает текущие значения, применяет требуемые и при изменении конфигурации заменяет под, ждёт новый UID, Ready и доступность REACTIONS version endpoint. Уже применённая конфигурация не вызывает лишнего перезапуска. При следующем сценарии состояние проверяется снова; ошибка подготовки останавливает бизнес-запросы.

Базовые значения: `preliminary-calculation-enabled=true`, `allow-result-without-main=false`, `all-rule-code-exp-enabled=true`, `empty-objects-response-enabled=true`. `api-config-load=true` для REST, `false` для Kafka. Применяется MAIN с максимальным layerPriority и минимальным expId. Имена env по умолчанию имеют вид `SPLITTER_PRELIMINARY_CALCULATION_ENABLED`; реальные ConfigMap и ключи определяет общий механизм. Настройки и переопределения описаны в DIAGNOSTICS.md.

OFF и MONITOR_OFF намеренно применяют предрасчёт false. MATRIX применяет выбранную комбинацию. FRESH, NO_TABLE, MONITOR_FRESH и MONITOR_NO_CONFIG дополнительно перезапускают процесс даже при неизменной конфигурации.

Исходные значения ConfigMap восстанавливает общий JUnit root-store в конце прогона. Это не восстановление после каждого теста. Эксперименты и таблица предрасчёта заменяются сценариями; их исходное содержимое через используемый API не восстанавливается. Нужен выделенный REACTIONS-инстанс и существующие разрешения общего механизма на изменения. Не запускать одновременно с другим набором, меняющим этот инстанс.

## Покрытие

| Класс | Содержание |
|---|---|
| SplitterReactionsPrecalc2885FlowTest | T02–T14, T16–T30, T40 |
| SplitterReactionsPrecalc2885ContractFlowTest | T31–T37, включая пустые параметры T34 |
| SplitterReactionsPrecalc2885ProfileFlowTest | T01, T15, матрица T12/T13, OFF T32, T38 и исследование дублей T34 |
| SplitterReactionsPrecalc2885MonitoringFlowTest | T39/T40: события и счётчики |
| Precalc2885OracleTest | 11 офлайн-проверок оракула |
| Precalc2885DiagnosticsTest | 18 офлайн-проверок подготовки, жизненного цикла и вложений |

35 базовых методов, 10 методов специальных профилей/исследований, 42 офлайн-проверки. Параметризация увеличивает число исполнений. DUPLICATES завершается ABORTED после сохранения данных: критерий конфликтующих дублей Q3 не согласован. Бизнес-ожидания сохранены; D9/T37 выполняется только как отдельное исследование до уточнения контракта конкурирующих обновлений. Пустой HTTP 200 от pre-calculate по-прежнему не считается доказательством успешного предрасчёта.

T02 проверяет пустую таблицу; NO_TABLE — отсутствие таблицы после запуска процесса. T38 теперь одним методом создаёт доказуемый кеш, перезапускает под через общий механизм, проверяет потерю кеша и его повторное создание. Прямой SDK `clearPreliminaryHolder()` через REST недоступен; полная пустая загрузка проверяется T18. Исходный анализ и вопросы контракта сохранены в TEST_PLAN.md.

## Компиляция и запуск

```powershell
Set-Location 'C:\Work\IdeaProjects\integration-test'
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc
.\gradlew.bat testClasses --console=plain
.\gradlew.bat test --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885OracleTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885DiagnosticsTest' --rerun-tasks
.\gradlew.bat splitterRestRegression --tests 'ru.sber.qa.splitter.EXPLAB_2885.SplitterReactionsPrecalc2885FlowTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.SplitterReactionsPrecalc2885ContractFlowTest' --rerun-tasks
.\gradlew.bat splitterKafkaRegression --tests 'ru.sber.qa.splitter.EXPLAB_2885.SplitterReactionsPrecalc2885FlowTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.SplitterReactionsPrecalc2885ContractFlowTest' --rerun-tasks
.\gradlew.bat prepareRegressionTestOpsResults
```

REST и Kafka запускать отдельными командами. EXPLAB_2885 автоматически выставляет `api-config-load` для каждой стадии. Не выполнять `clean` между ними. `prepareRegressionTestOpsResults` проверяет и объединяет последние завершённые стадии; проверить manifest на отсутствие посторонних стадий. Для обычных `test`/IDE в `build/allure-results`: `prepareTestOpsResults`, затем `allureReport`. Эти команды не отправляют результаты в TestOps.

## Специальные профили

Нужны `-PincludeManualTests=true`, EXPLAB_2885_PROFILE и точный фильтр метода. Остальные методы с несовпадающим профилем будут ABORTED. Профиль задаёт автоматически применяемые флаги.

| Профиль | Метод | Дополнительные условия |
|---|---|---|
| OFF | t01Off | Предрасчёт отключается автоматически |
| FRESH | t15BeforeFirstConfig | Оператор должен отключить автозагрузку конфигурации; отсутствие конфигурации проверяется тестом |
| NO_TABLE | t02NoTable | Новый процесс создаётся автоматически |
| MATRIX | t13ProfileMatrix | Задать EXPLAB_2885_ALLOW_WITHOUT_MAIN, EXPLAB_2885_EMPTY_OBJECTS, EXPLAB_2885_ALL значениями true/false; повторить все 8 комбинаций |
| RESTART | t38Restart | Перезапуск выполняется внутри сценария; внешний token не нужен |
| DUPLICATES | t34DuplicateKeysEvidence | Исследовательский результат, не приёмочный |
| MONITOR_FRESH | t39FirstReplaceValidation | Доступна Kafka мониторинга |
| MONITOR_NO_CONFIG | t39BeforeConfig | Автозагрузка конфигурации отключена оператором; Kafka доступна |
| MONITOR_OFF | t39Disabled | Kafka доступна |

```powershell
$env:EXPLAB_2885_PROFILE = 'RESTART'
.\gradlew.bat test '-PincludeManualTests=true' --tests 'ru.sber.qa.splitter.EXPLAB_2885.SplitterReactionsPrecalc2885ProfileFlowTest.t38Restart' --rerun-tasks
Remove-Item Env:EXPLAB_2885_PROFILE
```

Для мониторинга используются существующие `splitter.precalc.monitoring.kafka.env/topic`. Consumer подписывается до действия; события фильтруются по requestId, времени и REACTIONS. Автозагрузка бизнес-конфигурации не управляется этим патчем: FRESH/MONITOR_NO_CONFIG требуют отдельной настройки стенда и явно проверяют NO_SPLIT_CONFIG.

## Исправления v6 после стендового прогона

V5 подтвердил подготовку ConfigMap, перезапуск, восстановление и реальные pod logs для всех 56 сценариев. Итог REST: 28 PASS / 28 FAIL. В v6 исправлены тестовые ошибки T33 (различие REST Bean Validation и ошибки SDK) и T05 (пустой objectParams запрещён DTO split, поэтому используется непустой нерелевантный параметр). Проверки сохранности таблицы после валидации остаются обязательными. T34 успешную обработку пустых параметров по-прежнему отклоняет. Бизнес-расхождения T21/T23/T25/T27/T28/T29/T34/T35 и наблюдение T37 не скрыты; подробная классификация — CORPORATE_RUN_20261004.md.
## Уточнение v7
Аудит ожиданий: ASSERTION_AUDIT_V7.md. T37 теперь только ManualTest при EXPLAB_2885_PROFILE=CONCURRENT_UPDATES (и включении manual-тестов); автоматический базовый набор — 35 методов / 55 исполнений. T37 сохранён для исследования до уточнения контракта конкурирующих изменений. Общие префиксы Fabric8 — workload-*. REST/Kafka автоматически собираются в общий build/allure-results (либо настроенный testOpsSourceResultsDir); после них можно вызвать testOpsUpload. Для старых архивов сначала выполнить splitterRestRegressionAllureResults splitterKafkaRegressionAllureResults. Другие обычные результаты в общем каталоге сохраняются. Отправка автоматически не выполняется.


## Вложения и структура Allure — v9

Сохраняется структура setup → test body → teardown как у EXPLAB_2690. Именованные бизнес-шаги и бизнес-ожидания не меняются.

- Setup: `Splitter runtime metadata` для REACTIONS через общий SplitterRuntimeMetadata; текстовые снимки ConfigMap до подготовки и перед операцией. Снимки содержат фактические GET-значения управляемых флагов и ключа правил, имя, namespace, UID и resourceVersion. Посторонние ключи ConfigMap не публикуются.
- Teardown: `ConfigMap — после операции`, `Лог pod сервиса — <pod>` (`text/plain`, `.txt`) и короткий журнал сценария. Данные о сборе (pod/UID/container, начало/конец, статус, лимиты) находятся в заголовке того же текстового лога. Отдельные JSON log-capture/diagnostics-status убраны.
- Лог ограничен временем тела сценария: BeforeTestExecution → AfterTestExecution. Fabric8 запрашивает Kubernetes timestamps и sinceTime; строки до начала и после конца отсекаются локально. Настройка допуска часов для этого режима не расширяет окно. Для точных границ часы тестового агента и узлов кластера должны быть синхронизированы. При сбое setup используется интервал подготовки.
- При штатном перезапуске лог старого UID читается перед удалением, хранится до teardown и прикладывается рядом с логом нового pod. Обычный сценарий собирает лог один раз. Если сервер применил лимит, окно пусто, доступ запрещён или timestamps не разобраны, причина указана в текстовом вложении. Полнота лога при ротации или неожиданном перезапуске контейнера не гарантируется; существующие ограничения 2000 строк/262144 байт сохраняются, если они не переопределены настройками стенда.
- Состояние pod и события прикладываются только при ошибке. Квота и readiness timeline в отчёт EXPLAB_2885 не прикладываются даже при FAIL. Внутренние проверки готовности и восстановление стенда продолжают работать.

Все обращения к Kubernetes выполняются общим Fabric8. Другие потребители без включения сценарного режима сохраняют прежний сбор. Общая папка build/allure-results и testOpsUpload работают как в v7/v8. Старые архивы патч не переписывает.

Офлайн-проверки (42) можно выполнить отдельно:

```powershell
.\gradlew.bat test --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885OracleTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885DiagnosticsTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885ReportingTest' --tests 'ru.sber.qa.splitter.EXPLAB_2885.Precalc2885EvidenceScopeTest' --rerun-tasks
```

Это проверки тестового кода, а не подтверждение функциональности стенда. Специальные профили отсеиваются до setup, если EXPLAB_2885_PROFILE не выбран.
