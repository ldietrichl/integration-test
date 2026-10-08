# Scheduler v9: диагностика пересоздания pod и ConfigMap

## Назначение

Основание: корпоративный вывод 2026-09-15 20:09:30. В нём подтверждены Ready 4/4 нового pod, отказы FailedCreate/exceeded quota и временный HTTP 500 startup probe. Это не доказательство устранения дефицита квоты и не результат запуска сценариев v9.

Новая версия использует общие infrastructure.kubernetes и steps.container инструменты, существующие flow/rest-steps, DatabaseService проекта и тот же stand DEV/IFT/LT. Новых Gradle Test-задач нет.

## Сценарии

| ID | Проверка | Изменения стенда |
| --- | --- | --- |
| SCH-OPS-001 | Один выбранный pod удалён, появился другой UID того же Deployment/ReplicaSet, Ready всех контейнеров, новый туннель и HTTP health с DB UP | Удаление одного pod |
| SCH-OPS-002 | Чтение ConfigMap, связь с Deployment, существование согласованного ключа, приватный снимок | Нет |
| SCH-OPS-003 | Снимок ConfigMap; CAS patch ключа; отдельный GET assert; удаление pod; новый UID/Ready/health; возврат исходного ключа; GET assert; повторное пересоздание и health | Один ключ ConfigMap и два последовательных пересоздания pod |
| SCH-OPS-004 | Контролируемое изменение числа реплик и восстановление | По умолчанию отключён |
| SCH-POD-001 | Сохранение выбранных записей scheduler.task после пересоздания pod | Только при отдельно подготовленном стенде с остановленными jobs |

OPS-002 намеренно стал read-only. Полная проверка изменения ConfigMap находится в OPS-003: не остаётся сценария, который изменяет настройку envFrom и ошибочно считает GET достаточным для применения в процессе.

Диагностические OPS-сценарии не имеют Regres/CriticalRegres. Покрытие бизнес-требований не увеличено искусственно. SCH-POD-001 не доказывает перехват незавершённой задачи другим экземпляром. При jobs.paused=false он пропускается.

## Безопасная последовательность

1. Проверить явные разрешения stand, точный namespace, API, context и TLS kubeconfig.
2. Подтвердить один готовый pod и завершённый rollout, принадлежность pod -> ReplicaSet -> Deployment по UID. До ConfigMap patch проверить право delete выбранного pod.
3. Сохранить приватные pre-images до изменений.
4. Удалить только выбранное имя pod с серверным UID precondition. Deployment template/strategy/replicas не изменяются. Нет --force и переопределения grace period.
5. Дождаться исчезновения старого UID, нового UID, Ready всех обычных контейнеров и согласованного статуса Deployment.
6. Переоткрыть только принадлежащий тесту туннель и проверить /actuator/health, включая components.db.status=UP.
7. При изменении ConfigMap сначала восстановить только исходный ключ с CAS и GET assert, затем пересоздать pod ещё раз. Cleanup допускает удаление зависшего Pending pod, но только того же владельца и без чужого rollout.

Операция удаления выполняется через oc delete --raw с локальным DeleteOptions JSON. API URI строится внутри кода для одного pod выбранного namespace. Применяются тот же kubeconfig, context и проверка CA; это не обход RBAC и не отдельное подключение.

При одной реплике неизбежен перерыв доступности. Не запускайте параллельно второй экземпляр диагностики, ручное удаление pod или rollout restart. При изменении Deployment другим участником тест останавливает дальнейшие изменения, сохраняет артефакты и сообщает о конфликте.

## Таймауты и квота

- stand.<env>.mutations.timeout.seconds=600: бюджет ожидания замены/восстановления.
- stand.<env>.mutations.quota.fail-fast.seconds=60: ранняя ошибка только при новом FailedCreate/exceeded quota и отсутствии принадлежащих Deployment pod. Во время удаления старого pod кратковременная ошибка квоты не прерывает ожидание.
- stand.dev.kubernetes.request-timeout.ms=15000: отдельный короткий вызов API, не общий таймаут запуска приложения.
- stand.dev.kubernetes.startup-timeout.seconds=60: открытие туннеля после готовности pod.
- Весь OPS-тест ограничен 40 минутами с запасом для безопасного восстановления после основного таймаута.

Данные ResourceQuota limits.memory относятся к сумме заявленных лимитов, а не к текущему потреблению памяти. Тест не меняет квоту, HPA, Deployment strategy или другие сервисы. Отсутствие прав на events/resourcequota/logs отражается в отчёте, а не маскируется под успех сбора.

## Артефакты

Allure получает таймлайн готовности, статусы контейнеров, события выбранного Deployment/ReplicaSet/pod, снимок квоты, старый и новый UID, санитизированные логи сервиса. Сбор не требует Ready pod или открытого HTTP-туннеля и выполняется также до удаления и до восстановления.

Полные исходные ConfigMap/Deployment/pod и тела patch/DeleteOptions сохраняются в приватной stand-artifacts/<env>/<uuid> внутри каталога запуска. В Allure попадают только путь и SHA256 приватного файла. Не загружайте stand-artifacts или kubeconfig в TestOps. Санитизация произвольного текста приложения не даёт абсолютной гарантии отсутствия персональных данных.

Если исходный сценарий падает, восстановление всё равно выполняется. При подтверждённой готовности после восстановления выполняется отдельная HTTP-проверка; её ошибка добавляется к исходной, а не заменяет её. RESTORED означает восстановление конфигурации и готовности, а не возвращение старого UID.

## Заполненный DEV

Источник инфраструктурных параметров: src/test/resources/stand.properties. Выбор env: src/test/resources/test.properties.

- DEV выделен и явно разрешён пользователем для этих операций.
- namespace: ci07963639-dev-terra000003-abtm-back.
- oc: C:/Work/utils/oc.exe.
- kubeconfig: C:/Users/23209772/.kube/config.scheduler-dev-ca-20260914-215807-fb5f650e1843498d8e808df2d837c1bf.
- API: https://api.dev-terra000003-ids.ocp.delta.sbrf.ru:6443.
- Deployment/Service/ConfigMap: scheduler-service.
- Ключ: SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_V2_ENABLED.
- Пробное значение: false. Исходное значение читается непосредственно перед тестом и восстанавливается из pre-image. Если оно уже false, тест не выполняет бессмысленную подмену.
- mutation restart/pod-delete/configmap включены; scale выключен.
- Туннель: динамический свободный localhost-порт, порт сервиса 8080; контейнер логов scheduler-service.
- Подготовленные бизнес-fixtures выключены, jobs.paused=false. Тест не объявляет jobs остановленными без фактической подготовки.

IFT/LT не имеют подтверждённых context/API/kubeconfig для управления pod, поэтому оставлены выключенными. Пользовательские ID для бизнес-fixtures не выдуманы. Их отсутствие не мешает OPS-001/002/003. Известное подключение IFT PostgreSQL сохранено в database.properties, отсутствующее LT не подменяется DEV.

OpenShift token остаётся только в существующем корпоративном kubeconfig. Пароли БД/Nexus и общих TLS-хранилищ читаются из существующих secure.*.local.override.properties. Общие сертификаты других сервисов патч не удаляет.

## Запуск на ВАРМ

Из C:\Work\IdeaProjects\integration-test, без одновременного ручного управления pod:

~~~powershell
.\gradlew.bat compileWithoutTests --console=plain --no-daemon
.\gradlew.bat schedulerInfrastructureDiagnostics --tests '*SchedulerWorkloadDiagnosticFlowTest.ops001' --console=plain --no-daemon
.\gradlew.bat schedulerInfrastructureDiagnostics --tests '*SchedulerWorkloadDiagnosticFlowTest.ops003' --console=plain --no-daemon
~~~

Полная диагностика и регресс запускаются отдельно:

~~~powershell
.\gradlew.bat schedulerInfrastructureDiagnostics --console=plain --no-daemon
.\gradlew.bat schedulerRegression --console=plain --no-daemon
~~~

Не запускайте следующую операцию при ROLLBACK_FAILED_MANUAL_RECOVERY_REQUIRED. Сначала владелец стенда восстанавливает согласованный ключ и готовность сервиса по приватным снимкам. Не применяйте полный сохранённый ресурс поверх посторонних изменений.

Gradle 8.4 подтягивается из корпоративного Nexus через wrapper. Distribution хранится в GRADLE_USER_HOME, а не в архиве проекта.

## Первичные источники

- [Удаление и --raw](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_delete/).
- [Тело DELETE в клиенте Kubernetes 1.21](https://github.com/kubernetes/kubectl/blob/v0.21.0/pkg/cmd/delete/delete.go).
- [DeleteOptions preconditions](https://kubernetes.io/docs/reference/kubernetes-api/definitions/delete-options-v1-meta/).
- [ConfigMap и обновление переменных окружения](https://kubernetes.io/docs/concepts/configuration/configmap/).
