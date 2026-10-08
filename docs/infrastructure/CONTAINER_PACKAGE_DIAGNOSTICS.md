# Диагностика ContainerService 1.10.3-beta

## Назначение и границы

Один класс `SchedulerContainerPackageDiagnosticFlowTest`, 17 сценариев, общие шаги в
`steps.container`, flow в `flow`, изолированный environment без БД, Kafka, REST и туннелей.
Реальные операции выполняет пакет `ru.sber.qa.containers`; отдельной реализации HTTP API нет.
Профиль использует существующий kubeconfig с CA, точный context, namespace и ожидаемый API server.
Никакого входа в браузер, `oc login`, обновления токена, обхода TLS или изменения ArgoCD тесты не выполняют.

Встроенный `DefaultContainerServiceConfiguration` версии 1.10.3-beta принимает токены,
но сам не выполняет вход по логину/паролю и устанавливает `trustCerts=true`.
Поэтому здесь используется предусмотренная фреймворком точка расширения
`ContainerServiceConfiguration` и проектный `ManagedContainerServiceClient`.
Проверка сертификата и имени сервера сохраняется.

## Матрица публичных операций

Это трассировка реализации сценариев, НЕ результат прогона и НЕ процент покрытия строк.
Все 13 публичных операционных сигнатур клиента представлены в сценариях.
Четыре публичных метода flow-steps также представлены.
Инициализация ContainerService/Flow и владение клиентом проверяются CSP-002.
Внутренние исключения проверяются через отсутствующие ресурсы в CSP-012.

| API клиента | Сценарии | Режим |
|---|---|---|
| getClients() | CSP-002, CSP-003 | Чтение |
| checkPodsStatus(String) | CSP-005, CSP-012 | Чтение |
| getConfigMap(String) | CSP-006, CSP-012 | Чтение |
| getDataFromConfigMap(String) | CSP-007, CSP-012 | Чтение |
| getApplicationYml(String) | CSP-008, CSP-012, CSP-013 | Чтение / временная fixture |
| getLogsFromPod(String, Duration) | CSP-009, CSP-012 | Чтение |
| getLogsFromPod(String, String, Duration) | CSP-010 | Чтение |
| getLogsFromPod(String, String, Duration, List) | CSP-011 | Чтение |
| updateValuesInApplicationYml(String, String, Object) | CSP-013 | Временная ConfigMap |
| updateValuesInApplicationYml(String, Map) | CSP-013 | Временная ConfigMap |
| updateValuesInDataInConfigMap(String, Map) | CSP-017 | ConfigMap scheduler |
| restartPods(String) | CSP-014, CSP-017 | Удаление одного pod |
| restartPodsWithOutUnavailability(String) | CSP-016 | Удаление одного pod |

| API ContainerServiceFlow.applicationPlatform(...), ContainerServiceSteps | Сценарии |
|---|---|
| checkPodsStatus | CSP-005 |
| getApplicationYml | CSP-008, CSP-013 |
| updateValuesInApplicationYml | CSP-013 |
| restartPods | CSP-015 |

Дополнительно CSP-001 фиксирует локальную конфигурацию и fingerprint kubeconfig;
CSP-003 проверяет доступ к Deployment, Service и pod; CSP-004 независимо выполняет
read-only `oc get deployment -o name` с теми же kubeconfig/context/namespace.

По умолчанию разрешены 12 сценариев без изменения стенда. CSP-008 будет пропущен,
если реальная ConfigMap не содержит ровно одного YAML/YML-ключа.
Пять изменяющих сценариев CSP-013..017 выключены.
Фактическое число passed/failed/skipped определяется только корпоративным прогоном.

Профиль намеренно однокластерный. Multi-cluster/DC1/DC2, непрерывная доступность,
все варианты RBAC/IdP и все ветви YAML-парсера этим прогоном не подтверждаются.
Не надо выдавать наличие теста для каждой сигнатуры за полное функциональное покрытие пакета.

## Настройки

Полный отдельный файл: `src/test/resources/container-diagnostics.properties`.
Общий переключатель окружения остаётся `env=dev` в существующем `test.properties`.
Все адреса и разрешения привязаны к `stand.dev`, `stand.ift`, `stand.lt`.
Файл диагностического профиля является единственным источником его параметров подключения:
старые значения `stand.*` в других файлах не переопределяют этот изолированный прогон.

DEV заполнен по предоставленной корпоративной выгрузке. Проверьте, что kubeconfig
по указанному пути существует на ВАРМ; файл/токен в патч не включены.
IFT/LT выключены: их реальные API/context/namespace/kubeconfig не были предоставлены.
Значения DEV не используются как неявная замена IFT/LT.

Не заменяйте общие `test.properties`, `stand.properties`, `container-service.properties`
или рабочий kubeconfig шаблонами из других патчей. Не вставляйте токен в properties.

## Запуск

Команды предназначены для ВАРМ, из корня установленного integration-test, JDK 17 и Gradle 8.4.

```powershell
.\gradlew.bat compileWithoutTests --console=plain --no-daemon
.\gradlew.bat schedulerInfrastructureDiagnostics --tests "ru.sber.qa.scheduler.infrastructure.SchedulerContainerPackageDiagnosticFlowTest" --console=plain --no-daemon
```

Новая Gradle-задача не добавлена: используется существующая задача диагностики,
регрессионные задачи и их фильтры не меняются.
Класс также запускается из IDEA; рабочая директория должна быть корнем integration-test.
Теги Regression/CriticalRegression не добавлялись.

## Эксперимент с входом в браузер

1. Сразу после перезапуска ВАРМ, без браузерного входа, установите
   `stand.dev.container-diagnostics.phase=before-browser` и запустите только новый класс.
2. Не меняя kubeconfig, токен, свойства подключения и не выполняя `oc login`,
   авторизуйтесь в веб-консоли обычным способом.
3. Измените только `phase=after-browser` и повторите тот же запуск.
4. Сравните CSP-001 fingerprint/mtime и результаты CSP-003/CSP-004.
5. Отдельный запуск после согласованного входа CLI помечайте `phase=after-cli-login`.
   Если CLI-login понадобился, не смешивайте этот результат с эффектом веб-входа.

| Java / oc | Что проверять |
|---|---|
| Оба 401 / AUTH_REQUIRED | Срок действия токена и выбранный context; веб-пароль сам по себе не обновляет kubeconfig |
| Java 403 / oc Forbidden | RBAC на конкретный ресурс; это не доказательство неверного пароля |
| Java PKIX / oc успешно | CA и Java TLS-конфигурация; не отключать проверку TLS |
| Оба DNS/TCP/timeout | Сеть ВАРМ, API endpoint, прокси и корпоративная сессия |
| До входа ошибка, после успех, fingerprint тот же | Гипотеза влияния SSO/сети; нужна корреляция, это ещё не доказанный механизм |
| Fingerprint изменился | Сравнение уже включает изменение kubeconfig; браузер не является единственной переменной |

По таймаутам учитывайте возможные внутренние повторы Fabric8. Последний
`[CONTAINER-DIAGNOSTIC] ... stage=...` показывает, на каком вызове возникло ожидание.
Логи не содержат сырые exception messages/causes. HTTP-коды берутся из цепочки
KubernetesClientException; если библиотека потеряла исходную причину, категория может
быть UNCLASSIFIED. Независимый oc-контроль всё равно выполняется.

## Явное включение операций изменения

Только выделенный стенд и согласованное окно без параллельных операторов/прогонов.
Сначала выполните read-only часть. Не меняйте настройки ArgoCD из теста:
если другой контроллер возвращает значения или меняет Deployment, сценарий обязан упасть,
а не бесконечно повторять запись.

Для DEV общие допуски:
```properties
stand.dev.container-diagnostics.dedicated-stand=true
stand.dev.container-diagnostics.mutations.enabled=true
stand.dev.container-diagnostics.mutations.approval=dev/ci07963639-dev-terra000003-abtm-back/scheduler-service
```

После согласования включайте только требуемые операции:
```properties
stand.dev.container-diagnostics.mutations.yaml-fixture.enabled=true
stand.dev.container-diagnostics.mutations.restart.enabled=true
stand.dev.container-diagnostics.mutations.restart-without-unavailability.enabled=true
stand.dev.container-diagnostics.mutations.configmap.enabled=true
```

Для CSP-017 одновременно нужен допуск restart. Выбранный ключ
`SCHEDULER_SERVICE_CLEAR_HUNG_TASKS_JOB_ENABLED` временно меняется на `false`,
затем возвращается фактическое исходное значение.
Сценарий запрещает отсутствующий ключ, неboolean-значение, секретоподобный ключ и no-op.
Отключение очистки зависших задач может влиять на фоновые задания: согласуйте окно.

CSP-013 создаёт собственную уникальную ConfigMap, проверяет YAML и flow-обёртки,
восстанавливает data с контрольным чтением и удаляет только свою fixture.
Эта ConfigMap не подключена к scheduler, поэтому рестарт scheduler для неё не требуется.

CSP-014..016 удаляют один точный pod с проверенной цепочкой владельцев
Pod -> ReplicaSet -> разрешённый Deployment. Затем проверяются исчезновение старого
pod, новый UID, тот же контроллер и Ready. Replica count и Deployment не меняются.
CSP-016 проверяет именно доступный метод библиотеки; его имя не считается доказательством
нулевого простоя. На единственной реплике возможен перерыв в обслуживании.

CSP-017 сохраняет оригинальную ConfigMap, меняет data, перечитывает и проверяет
точное ожидаемое содержимое, пересоздаёт pod и повторно читает ConfigMap.
В finally восстанавливает оригинал, перечитывает, снова пересоздаёт pod и проверяет Ready.
При конфликте UID/data откат не затирает чужие изменения: тест падает с указанием
необходимости ручного восстановления. При жёстком завершении JVM finally не гарантирован.

Доказательство применения в CSP-017: ссылка Deployment на ConfigMap + новый Ready pod
+ сохранённое значение в API. Значение внутри Spring runtime/поведение фонового задания
этим тестом НЕ проверяется. Для этого нужна отдельная бизнес-проверка scheduler.

## RBAC

Read-only: get deployments/services/configmaps, list configmaps/pods, get pods/log.
Для мутаций дополнительно: get replicasets, delete pods; create/patch/update/delete
configmaps для fixture; patch/update ConfigMap scheduler для CSP-017.
При отсутствии прав запросите минимальный доступ у владельца стенда, а не обход проверки.

## Артефакты и безопасность

Обычные JSON: `build/diagnostics/container-package/<env>/<phase>/<run-id>/`.
Санитизированный JSON каждого теста также прикладывается к Allure.
Сохраняются этапы, duration, classnames, HTTP-коды, идентификаторы ресурсов,
версии и число строк логов. Тела логов/ConfigMap и kubeconfig в Allure не отправляются.

Полные исходные ConfigMap сохраняются только в `private-recovery-*/` внутри каталога прогона.
До записи каталог получает ACL текущего владельца (Windows) либо 0700 (POSIX).
Если ACL установить нельзя, выполнение останавливается; рабочая ConfigMap не меняется.
Эти файлы могут содержать чувствительные данные. Не загружайте их в TestOps,
не прикладывайте весь build к чату; передавайте только обычные JSON и стандартный отчёт.
Исходные снимки сохраняются для ручного восстановления после аварии.

Не включайте HTTP wire logging, Gradle --debug, trace команд и дампы Config.toString().
Диагностические JSON не являются подтверждением успешного входа до фактического прогона.
