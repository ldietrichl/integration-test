# Scheduler regression v8

## Что входит

137 сценариев исходного плана сохранены с ID SCH-001..137. Сценарии в `src/test/java/ru/sber/qa/scheduler/` содержат только вызовы шагов. Реализация REST/DB-проверок находится в `src/main/java/steps/flow/scheduler/`; общие операции стенда в `steps/container/` и `infrastructure/kubernetes/`. Используются существующие Platform V AT Environment, REST, explab DB, KubernetesTunnelService и тот же oc/kubeconfig. Отдельных HTTP/DB клиентов в сценариях нет.

JUnit lifecycle и условия вынесены из тестовых папок. Очистка собственных DB fixtures использует общий контекст текущего потока. Туннели закрываются даже при ошибке очистки. Общие шаги можно вызывать из других сервисных flow; workload выбирается по имени профиля.

## Gradle 8.4

- `schedulerRegression`: единственная видимая задача полного регресса scheduler в группе `regression`.
- `schedulerInfrastructureDiagnostics`: существующие 14 проверок и 4 новых SCH-OPS в группе `diagnostics`.
- `schedulerPreflight`: проверка схемы/справочников, группа `diagnostics`.
- Диагностические, preflight и debug-задачи других сервисов тоже группируются в `diagnostics`.
- Старые `schedulerServiceRegression`, `schedulerReadOnlyRegression`, `schedulerManagedRegression` больше не запускают тесты и выводят указание перейти на новую задачу. Read-only имя НЕ перенаправляется на изменяющий стенд полный регресс.
- Все Gradle-задачи и их группировка остаются в корневом `build.gradle.kts`. Stand-настройки не задаются через задачи.
- Подготовленные сценарии включаются через `scheduler.<env>.prepared.enabled=true`; `includeManualTests` больше не является вторым независимым переключателем scheduler.
- Запуск пакета в IDEA использует те же условия и ресурсы. Не отключать JUnit conditions.

```powershell
.\gradlew.bat compileWithoutTests --console=plain --no-daemon
.\gradlew.bat schedulerInfrastructureDiagnostics --console=plain --no-daemon
.\gradlew.bat schedulerRegression --console=plain --no-daemon
.\gradlew.bat schedulerRegression --tests '*SchedulerReadOnlyRegressionFlowTest' --console=plain --no-daemon
```

## Единые настройки стенда

Среда выбирается ТОЛЬКО `env=dev`, `env=ift` или `env=lt` в существующем `test.properties`.

`stand.properties`: `stand.<env>.kubernetes.*`, `fixtures.*`, `user-id`, `other-user-id`, `dedicated`, разрешения `mutations.*`, привязка workloads к Deployment/ConfigMap. Параметры jobs.paused/schema/splitting-point и prepared-сценариев остаются scheduler-специфичными: это свойства поведения сервиса.

Приоритет: новое имя в test.properties > старое совместимое имя в test.properties > новое имя в stand.properties > прежнее значение по умолчанию. Старые kubernetes.* и scheduler.* подключения из test.properties не удаляются автоматически; переносите их в stand.<env>.* и удаляйте дубли только после переноса значения. Секреты по-прежнему в защищённом kubeconfig/secure resolver, не в патче.

У DEV указан ранее использованный корпоративный контекст и путь к kubeconfig. Его доступность/токен на ВАРМ здесь не проверялись. Для IFT/LT адреса, контекст и namespace оставлены пустыми: подменять их DEV запрещено. Настройки существующих REST/DB/Kafka клиентов DEV/IFT/LT сохраняются в ресурсах фреймворка.

## Допуск на изменения стенда

По умолчанию ВСЕ изменения выключены, включая DEV. Для выделенного стенда оператор указывает его реальные context/API/namespace/kubeconfig, target Deployment и подтверждает namespace. Нельзя просто пометить общий DEV как выделенный.

```properties
stand.dev.dedicated=true
stand.dev.mutations.enabled=true
stand.dev.mutations.namespace-confirmation=<EXACT_DEDICATED_NAMESPACE>
stand.dev.mutations.restart.enabled=true
stand.dev.mutations.configmap.enabled=false
stand.dev.mutations.scale.enabled=false
stand.dev.workloads.scheduler.deployment=<EXACT_DEPLOYMENT_NAME>
```

Для ConfigMap дополнительно:

```properties
stand.dev.mutations.configmap.enabled=true
stand.dev.workloads.scheduler.configmap=<EXACT_REFERENCED_CONFIGMAP>
stand.dev.workloads.scheduler.configmap.key=<APPROVED_EXISTING_NONSECRET_DATA_KEY>
stand.dev.workloads.scheduler.configmap.probe-value=<APPROVED_DIFFERENT_VALUE>
```

Разрешается только существующий строковый ключ data, указанный явно. Не использовать пароль, токен, сертификат, ключ доступа или невалидную конфигурацию. ConfigMap должна быть связана с выбранным Deployment. SCH-OPS-002 доказывает сохранение и откат поля через API, а не hot reload приложения. SCH-OPS-003 дополнительно делает rollout и health-проверку; семантика конкретного параметра требует отдельного бизнес-оракула.

Для реплик: `stand.dev.mutations.scale.enabled=true` и `stand.dev.workloads.scheduler.scale.replicas=1` либо `2`, значение должно отличаться от текущего. При HPA, управляющем Deployment, изменение запрещено. Это проверка масштабирования, не доказательство отсутствия двойного бизнес-эффекта SCH-136.

RBAC: get Service/Deployment/ConfigMap, list pods; patch только согласованных Deployment/ConfigMap. Для проверки отсутствия HPA требуется list HPA. Туннели: get/list pods, create pods/portforward. Логи: get pods/log. Не нужны cluster-admin, Secrets, pod exec или удаление pod.

## Артефакты и восстановление

До первого изменения ресурса сохраняется полный JSON original-<kind>-<name>.json и SHA-256. Каталог: в папке текущего прогона stand-artifacts/<env>/<UUID>; для IDEA build/stand-artifacts/<env>/<UUID>. Он закрывается ACL для пользователя процесса, без смены владельца исходных файлов. Полные ConfigMap и Deployment могут содержать внутренние данные: НЕ отправляйте этот каталог в TestOps и не добавляйте в VCS.

Allure получает метаданные, результаты, новые pod UID и очищенные логи до/после операций. Сохранённый полный ресурс не вкладывается в отчёт автоматически.

Каждый patch проверяет UID и resourceVersion. Откат в close/finally сравнивает текущее поле с записанным тестом значением; чужие изменения не затираются. Сначала возвращается ConfigMap, затем исходный pod template и выполняется ожидание Ready. Поэтому перезапуск диагностического сценария обычно включает ДВА rollout: пробный и восстановительный. Ошибка восстановления делает тест неуспешным и пишет ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED. При остановке JVM/ВАРМ finally не гарантирован: восстановление выполняет владелец стенда по сохранённым исходным полям, без force replace всего ресурса.

## Покрытие

Файлы рядом: scheduler-regression-v8.xlsx, scheduler-regression-v8-jira.txt, scheduler-regression-v8-plan.json.

Плановое покрытие документации: 47/51 = 92,16%, выше запрошенных 83%. D19/D23/D24/D25 остаются в знаменателе. Исходные 20 кодовых рисков считаются отдельно.

Это НЕ 92,16% автономной автоматизации. В коде 69 AUTO, 65 PREPARED, 3 DISABLED mTLS; 16 исходных случаев требуют legacy-схему. PREPARED всё ещё требует ручной подготовки и свежих observations.json для текущего runId. Pod-инструменты не предоставляют фиксированные часы, сетевые сбои зависимостей, журналы их бизнес-эффектов или ротацию БД. SCH-135 частичен: сценарий не отключает БД. SCH-OPS/SCH-POD не повышают исходное покрытие.

Excel раздельно считает план, автономные случаи на dev-v2, наличие кода и фактическое подтверждение. Заполните PASS/FAIL/SKIP/BLOCKED и ссылку на артефакты именно нового прогона. Без доказательств подтверждённое покрытие не растёт. Новая версия изначально NOT RUN.

Известные расхождения сервиса не замаскированы: ASC, даты DTO против OpenAPI, taskNumber NULL против 0. Их проверки сохранены.

## Ограничения поставки

Локальный запуск корпоративных тестов и изменения Kubernetes не выполняются. Разрешена только компиляция Gradle 8.4 и исправление её ошибок. Результат компиляции приложен отдельно. Полный регресс на выделенном стенде ещё требуется.

## Метки корпоративного регресса

Метки regress/critical-regress отсутствуют у всех PREPARED, DISABLED mTLS и LEGACY_ONLY случаев. Они не подтверждены как выполнимые автономно на текущем корпоративном dev-v2 стенде. Диагностика, включая preflight, тоже не размечается как регресс. Ошибка самого сервиса не повод снимать метку с выполнимой автоматической проверки. SCH-POD-001 сохраняет regress: инструменты имеются, но нужен выделенный стенд и явное разрешение. При появлении недостающей инфраструктуры возвращать метки следует вместе с реализацией и подтверждением выполнения.
