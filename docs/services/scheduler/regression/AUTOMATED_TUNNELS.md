# Регресс scheduler с управляемыми туннелями

## Что изменено

Общий lifecycle `AbstractSchedulerFlowTest` обслуживает API-, managed- и read-only-сценарии.
Туннель scheduler открывается лениво при первом REST-вызове, переиспользуется внутри сценария
и закрывается после сценария, включая падение проверки или очистки фикстур.
Для V2-метаданных используется отдельный принадлежащий сценарию туннель dictionary-service
через тот же выбранный DEV/IFT context. DB использует прежний framework-клиент `explab`.

Маршрут хранится только в контексте текущего потока сценария. Файлы properties,
глобальные REST-настройки и чужие процессы не меняются. Потеря туннеля завершает сценарий:
автоматического повторения запросов записи и переключения на ingress нет.
Запуски остаются последовательными, с общим resource lock scheduler.

CA для OpenShift API берётся из выбранного kubeconfig. Проверка TLS/hostname не отключается.
HTTP используется только на локальном участке к scheduler через port-forward.
Это не исправление глобального пустого JVM truststore и не проверка mTLS ingress.

## Настройка ВАРМ

Патч устанавливается поверх проекта с диагностической инфраструктурой v4.2 и уже
работающим DEV kubeconfig с согласованной CA-цепочкой. Копии kubeconfig и сертификаты
с закрытыми ключами в этот патч не входят.

В существующем `src/test/resources/test.properties` добавьте или замените только
указанные ключи, без дубликатов:

```properties
env=dev
scheduler.dev.tunnel.enabled=true
kubernetes.oc.executable=C:/Work/utils/oc.exe
kubernetes.dev.transport=oc
kubernetes.dev.local-port=0
kubernetes.dev.logs.enabled=true
kubernetes.dev.logs.container=scheduler-service
kubernetes.dev.logs.tail.lines=500
kubernetes.dev.logs.max.bytes=131072
kubernetes.dev.logs.timeout.seconds=15
kubernetes.dev.logs.clock-skew.seconds=3
```

Сохраните ранее проверенные значения `kubernetes.dev.kubeconfig`,
`kubernetes.dev.context`, `kubernetes.dev.api-server`, `kubernetes.dev.namespace`.
Для dictionary по умолчанию используются тот же namespace, `dictionary-service`, порт 8080.
Если реальные значения другие, задайте `kubernetes.dev.dictionary.namespace`,
`kubernetes.dev.dictionary.service`, `kubernetes.dev.dictionary.service-port`.
Не подставляйте значения IFT по аналогии: нужен его собственный проверенный context и CA.

После подтверждённого native-прогона можно явно выбрать `kubernetes.dev.transport=fabric8`.
Сбор логов и dictionary-туннель всё равно требуют `oc.exe`.
RBAC: чтение Service/Pod, список Pod, создание pods/portforward; для логов также чтение pods/log.
Токен OpenShift остаётся в локальном kubeconfig, не в REST-заголовках scheduler и не в отчёте.

Общий ключ для обоих окружений: `scheduler.tunnel.enabled=true`.
Приоритет: system property `scheduler.tunnel.enabled`, затем env-specific значение,
затем общий ключ, по умолчанию false.
В Gradle переопределение на один запуск: `-PschedulerTunnel=true` или `false`.
Для обычного JUnit из IDEA используйте test.properties или VM option
`-Dscheduler.tunnel.enabled=true`.
`scheduler.infrastructure.enabled` управляет старым диагностическим классом,
а не новым механизмом регресса.

При автоматическом туннеле ручной listener 28089 не нужен.
Существующие `rest.dev.scheduler.base-uri` и `rest.ift.scheduler.base-uri` сохраняются
для прямого режима; автоматические scheduler REST-вызовы их не используют.
Для прямого режима без управляемого туннеля установите `scheduler.<env>.tunnel.enabled=false`;
scheduler и dictionary должны иметь доступные URI в обычных настройках проекта.

## Запуск

Используйте wrapper проекта: Gradle 8.4 и JDK 17.
Проект отклоняет другую версию Gradle, включая запуск из IDEA или CI.
Команды ниже не выполнялись при подготовке миграции.

```powershell
.\gradlew.bat --version
.\gradlew.bat testClasses
.\gradlew.bat schedulerReadOnlyRegression -PschedulerTunnel=true
```

Задачи:

| Задача | Состав |
| --- | --- |
| schedulerReadOnlyRegression | DB preflight и 7 новых read-only сценариев; без INSERT/UPDATE/DELETE и управления стендом |
| schedulerServiceRegression | 7 read-only сценариев и существующий API-регресс |
| schedulerRegression | DB preflight, 7 read-only сценариев и существующий API-регресс |
| schedulerPreflight | Только прежняя read-only проверка БД |
| schedulerManagedRegression | Сценарии вручную подготовленного стенда; отдельный opt-in |
| schedulerInfrastructureDiagnostics | Прежние инфраструктурные гипотезы; отдельный диагностический запуск |

Полный API-регресс по-прежнему требует реальной подготовки:
`fixtures.enabled=true`, `fixtures.isolated=true`, `jobs.paused=true`, пользователей
и согласованных тестовых объектов. Эти флаги не включаются патчем.
Доступ через oc не доказывает изоляцию фикстур и не останавливает jobs.
Отсутствие условий означает skipped, а не passed.

```powershell
.\gradlew.bat schedulerRegression -PschedulerTunnel=true
# Только после подготовки managed-сценариев и свежих наблюдений:
.\gradlew.bat schedulerManagedRegression -PschedulerTunnel=true -PincludeManualTests=true
```

Запуск пакета из IDEA также использует общий lifecycle. Manual-ограничения,
отключённые mTLS-кейсы и ограничения legacy/dev-v2 сохранены.
Подготовленные managed-манифесты по-прежнему должны соответствовать реальному стенду.
Новые инструменты не заменяют контроль времени, отказов, перезапусков и ротации БД.

## Read-only сценарии и трассировка

| Сценарий | Проверка | Связь с исходными проверками |
| --- | --- | --- |
| SCH-RO-001 | Health, DB health, Prometheus | SCH-135; SCH-INF-004/008/010 |
| SCH-RO-002 | V2 AUTOSTART_TASK_LIST и ограниченная страница реестра | V2-контракт; SCH-INF-005/006 |
| SCH-RO-003 | Доступный equal-фильтр по существующему значению | Фильтрация V2; SCH-INF-006 |
| SCH-RO-004 | ASC по реально различающимся значениям | SCH-019; SCH-INF-013 |
| SCH-RO-005 | DESC независимо от результата ASC | SCH-020; SCH-INF-013 |
| SCH-RO-006 | planDt/createdAt/updatedAt как string/date-time | Экспортированный OpenAPI; SCH-INF-007 |
| SCH-RO-007 | taskNumber из API соответствует БД, включая NULL | TaskContentConverter/постоянный ноль; SCH-INF-014 |

Это дополнительные автоматизированные проверки на наблюдаемых данных, не семь
новых требований. Исходный каталог и знаменатель расчёта покрытия не изменены.
Недостаток данных/различающихся значений даёт skipped. Эти сценарии не заменяют
изолированные проверки полноты выборки, пагинации и всех граничных условий.
Процент прохождения не равен проценту покрытия; 85% этим патчем не объявляется.

Сохранены TestOps-аннотации проекта: regression/critical/manual по назначению.
Общий listener формирует канонические теги. Старые SCH-идентификаторы, historyId
по окружению и трассировка требований сохранены. Метаданные переносятся в
`beforeTestWrite`, чтобы не обновлять TestResult по UUID before/after-фикстуры.
Для read-only применяется отдельная стабильная идентичность SCH-RO и признак
`coverageRole=supplementary-read-only`; чужие Allure TestOps ID не назначаются.

## Логи и известные расхождения

После каждого сценария при `logs.enabled=true` прикладывается ограниченный
снимок логов выбранного Pod/контейнера за окно сценария с поправкой на часы.
Сбор read-only, с редактированием чувствительных строк и лимитами.
Статусы COLLECTED/EMPTY_WINDOW/NO_TARGET_NO_SERVICE_REQUEST или ошибка сбора
видны отдельно. Ошибка получения логов не подменяет бизнес-результат.
При DB-only preflight туннель не открывается.
Окно времени может включать соседние запросы; это не строгая корреляция по requestId.
Перед выгрузкой Allure за корпоративный контур просмотрите REST/DB/лог-вложения.

В последнем предоставленном диагностическом прогоне остались три расхождения:
числовые даты вместо строк OpenAPI, ASC-сортировка и taskNumber (NULL в БД, 0 в API).
Они не подавлены. Новый прогон может оставаться красным до исправления сервиса
или согласованного изменения контракта.
Сортировка теперь сравнивается без молчаливого преобразования строковых дат в 0;
отдельная проверка формата дат остаётся строгой.
Фабрика запросов и негативные кейсы используют канонические wire-поля
`code/operator/values`, сортировки `code/direction`, а не имена полей метаданных dictionary.

Артефакты новой задачи:
`build/regression-results/<env>/scheduler-readonly/runs/<runId>/`.
Существующая Gradle-инфраструктура сохраняет JUnit, raw Allure, console и summary.
Передавайте артефакты одного запуска, не объединяя несколько allure-results.
Локальная компиляция, прогон и построение HTML Allure для этого патча не выполнялись.
