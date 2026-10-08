# EXPLAB_2885 — ConfigMap, поды и Allure в v5

## Общий корпоративный механизм

Используется тот же жизненный цикл, что в корпоративном EXPLAB_2690: `WorkloadRunScopeExtension.ensure` → `KubernetesWorkloadService` → `KubernetesWorkloadSteps.prepare/ensureState` → `KubernetesWorkloadControl`. Это штатный Fabric8. Подготовка обязательна перед каждым выполняемым сценарием. Упрощённого продолжения без настроенного управления стендом нет.

`environmentState` определяет ConfigMap и ключи по env/configMapKeyRef или envFrom Deployment. Литеральный env, Secret или неоднозначная ссылка не заменяются вслепую: общий механизм сообщает ошибку. Затем `applyState` сверяет текущие значения и меняет только заданные ключи. Изменение вызывает замену пода, ожидание готовности и проверку маршрута `/version`. При неизменном состоянии перезапуска нет, кроме профилей, требующих свежего процесса.

Подключение, namespace, TLS, контекст, lease, допуск выделенного стенда и разрешения мутаций берутся из существующей корпоративной инфраструктуры. Патч не заменяет эти настройки и не обходит её ограничения. Восстановление исходных ConfigMap выполняется общим механизмом в конце JUnit-прогона, включая ошибку сценария; принудительное завершение JVM не гарантирует выполнение cleanup.

## Адресация REACTIONS

Приоритет: свойства `explab2885.reactions.*`, затем существующие `explab2690.reactions.*`, затем stand.properties и значения по умолчанию. Свойства задачи читаются из test.properties, системные свойства имеют приоритет.

| Суффикс свойства | По умолчанию / fallback |
|---|---|
| stand.workload | splitter-reactions |
| stand.deployment | существующий workloads.<workload>.deployment / splitter-reactions-service (или выбранный service) |
| stand.service | services.splitter-reactions.name / splitter-reactions-service |
| stand.service-port | 8080 |
| stand.container | workloads.<workload>.container / имя service |
| configmap.rules.name | workloads.<workload>.configmap / <service>-lib |
| configmap.rules.key | splitter-rules-reactions.yml |
| application-env.preliminary-calculation-enabled | SPLITTER_PRELIMINARY_CALCULATION_ENABLED |
| application-env.api-config-load | SPLITTER_API_CONFIG_LOAD |
| application-env.allow-result-without-main | SPLITTER_ALLOW_RESULT_WITHOUT_MAIN |
| application-env.all-rule-code-exp-enabled | SPLITTER_ALL_RULE_CODE_EXP_ENABLED |
| application-env.empty-objects-response-enabled | SPLITTER_EMPTY_OBJECTS_RESPONSE_ENABLED |

Если Deployment использует, например, `SPLITTER_CONFIG_PRELIMINARY_CALCULATION_ENABLED`, задать `explab2885.reactions.application-env.preliminary-calculation-enabled` этим именем. Это имя env в Deployment, а не догадка об имени ConfigMap. Нужный ключ определит инфраструктура. Профиль из test.properties не подменяет значения EXPLAB_2885_PROFILE: специальный профиль выбирается переменной окружения.

## Вложения Allure

После всего тестового метода, включая проверки Kafka, вызывается общий `captureEvidence`. Он прикладывает доступные сведения о Deployment/подах/ConfigMap и логи штатным `WorkloadEvidence`/`OcServiceLogs.captureNative`. Для логов должен быть включён существующий параметр `kubernetes.<env>.logs.enabled=true` в используемой конфигурации Kubernetes проекта, например `kubernetes.dev.logs.enabled=true`. RBAC должен разрешать чтение pods/log. Контейнер выбирается из WorkloadTarget; отдельный клиент не создаётся.

Дополнительно всегда предпринимается прикрепление `EXPLAB-2885 scenario-log` (вызовы LOAD_CONFIG/PRE_CALCULATE/SPLIT, requestId, длительность, тип тела, класс исключения) и `diagnostics-status`. Это журнал действий сценария; полный stdout JVM в него не дублируется. Общий сбор логов ограничен штатными window/tail/size настройками и может охватывать несколько сценариев общей сессии. Перед явным перезапуском T38 также снимается evidence старого пода.

Ошибка сбора evidence отмечается в журнале и не подменяет основную ошибку теста. MANAGED_STATE_READY означает успешную подготовку; это не доказательство наличия pod logs. При отключённом logs.enabled или запрете RBAC полноту вложений подтвердить нельзя: проверить общий evidence и фактические вложения корпоративного прогона.

Старые `splitter.precalc.diagnostics.*` настройки v3 больше не используются. Два вспомогательных общих класса v3 удаляются установщиком только при точном совпадении с поставкой v3, с резервной копией. Общие корпоративные классы не заменяются.

## Defaults v5 и исправление запуска 17:14

В EXPLAB_2690 `gradle/build-logic/tickets.gradle.kts` (REACTIONS-блок, строки 122–136 в изученной копии) передаёт в тестовую JVM:

```properties
stand.dev.workloads.splitter-reactions.deployment=splitter-reactions-service
stand.dev.workloads.splitter-reactions.configmap=splitter-reactions-service-lib
stand.dev.workloads.splitter-reactions.readiness.timeout.seconds=120
stand.dev.workloads.splitter-reactions.readiness.poll.millis=2000
```

Это объясняет отсутствие ключей в build/resources/test. Обычная задача splitterRestRegression такую подготовку 2690 не выполняет. `Precalc2885StandBindings` добавляет только отсутствующие значения на срок жизни тестовой JVM (env и workload вычисляются из настроек). Явные системные/secure/project/stand значения сохраняются. Привязка живёт до завершения JVM, включая восстановление ConfigMap общим root-store. Никаких изменений прав, namespace, файлов конфигурации или новых клиентов нет.

В предоставленном прогоне stand.dev.kubernetes.logs.enabled уже true. Отсутствие pod logs вызвано остановкой до чтения Deployment, а не выключенным логированием. Полный testClasses и 22 проверки v4 в корпоративной среде прошли; v5 исправляет пропущенные параметры запуска и добавляет 4 офлайн-проверки.
## Аудит размера и исправление v8

Предоставленный архив 20261004-202606-688-c3a93312 — Kafka, 55 тестов (46 passed, 9 failed). Build: 383,02 МиБ. Allure: 181,07 МиБ и 2745 файлов; полный набор побайтово повторён в архиве стадии и общем каталоге. Внутри Allure: 278 readiness timeline (132,959 МиБ), 165 pod logs (39,669 МиБ), 278 events (5,230 МиБ), 590 HTTP вложений (1,050 МиБ), 55 журналов сценария (0,112 МиБ). Потерянных ссылок и бесхозных attachment-файлов нет.

Все 168 попыток сбора pod logs имели одинаковый scenarioStartedUtc, от начала общей сессии; 109 отмечены PARTIAL_LIMIT. Один фрагмент лога повторён 109 раз; в 38 послесценарных вложениях не найдено requestId собственного сценария. Это подтверждает необходимость исправления окна сбора, а не только снижения байтового лимита.

V8 устраняет повторные routine-вложения, начинает отдельное окно для каждого сценария и помещает диагностику в JUnit after-fixture. Полный dump статуса заменён компактными полями UID, readiness, replicas, conditions и состояния контейнеров. История сбрасывается на границе сценария и ограничена 128 наблюдениями. На FAIL и при замене pod доступны подробности текущего сценария и события. Успешные сценарии также сохраняют журнал и лог pod. Управление, проверки готовности и root rollback продолжают выполняться через общий Fabric8.

В исходных результатах ничего не удалялось и не переписывалось. Старые архивы останутся прежнего размера; новая политика применяется только к последующим запускам. testOpsUpload отправляет подготовленный raw-пакет с вложениями; весь build и приватные резервные копии не отправляются.


## Уточнение состава вложений — v9

В последнем переданном build проверены общий raw Allure и build/testops-results/dev/allure-results: 145 результатов = 110 стендовых + 35 офлайн. В каждом из 110 стендовых сценариев есть text/plain service log в after-fixture. Всего текстовых service log вложений 112 (ещё 2 при подготовке/перезапуске); содержимое в общем и подготовленном наборах совпадает по SHA256. Отсутствующих ссылок на вложения нет. 114 попыток сбора: 112 COLLECTED и 2 EMPTY_WINDOW при подготовке. Из файлов нельзя подтвердить, как конкретная вкладка корпоративного TestOps показывает fixture-вложения.

Размер общего raw Allure уже снизился до 8 163 283 байт (около 7,79 MiB) в переданном v8. Это измерение v8, не прогноз размера v9. В v8 оставались 220 Managed ticket workload readiness, 22 timeline, 22 quota и 114 JSON log-capture вложений. V9 убирает эти вложения для EXPLAB_2885 и даёт явное имя `Лог pod сервиса — <pod>`. Статус и границы окна теперь в заголовке .txt; даже пустой/недоступный лог представлен текстовым вложением с причиной.

ConfigMap передаётся текстом с фактическими значениями выбранных сценарием ключей до подготовки, перед операцией и после неё. Логи собираются в интервале тела сценария, без ±3 секунд; лишние строки отсекаются по Kubernetes timestamps, а не по произвольному времени в сообщении приложения. Runtime metadata — общий метод для точки REACTIONS. Успешные сценарии не содержат событий и состояния pod. При FAIL остаются события и текущее состояние, без квоты и timeline.

Предыдущие разделы описывают историю диагностики; актуальная политика вложений — этот раздел и README v9.
