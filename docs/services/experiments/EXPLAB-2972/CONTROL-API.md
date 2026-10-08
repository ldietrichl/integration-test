# EXPLAB-2972: контракт управления тестовым окружением

Набор содержит код всех 60 номеров: 8 обычных и 52 управляемых. Для 16 добавленных сценариев управление окружением и чтение внешних наблюдений вынесены в отдельный REST-клиент Platform V AT. Сам тест выполняет бизнес-запросы через RestService, читает БД через DatabaseService, Kafka — непосредственно через KafkaService и проверяет результат assertions. Контроллер не присылает PASS/FAIL.

## Подключение

Выбор окружения: env в test.properties. Параметры:

```properties
# <env> заменить выбранным env из test.properties.
explab2972.<env>.control.base-uri=https://<test-environment-controller>
explab2972.<env>.control.token=${SECURE_2972_CONTROL_TOKEN}
# По умолчанию — mTLS с общим keystore; отдельный сертификат задаётся при необходимости.
# explab2972.<env>.control.mtls.enabled=true
# explab2972.<env>.control.keystore=src/test/resources/controller-keystore.p12
# explab2972.<env>.control.keystore.pass=${SECURE_2972_CONTROLLER_KEYSTORE_PASSWORD}
explab2972.<env>.kafka.topics=<exp-topic>,<splitting-topic>,<legacy-topic>,<links-topic>
```

Kafka-подключение задаётся в `src/test/resources/kafka-consumers.properties` штатным профилем `kafka_consumer.<profile>.*`. Для EXPLAB-2972 профиль равен `env` с заменой дефиса на подчёркивание: `ift-dm` → `ift_dm`. Например, `kafka_consumer.ift.bootstrap.servers=<BROKERS>` и `kafka_consumer.ift.sasl.jaas.config=${SECURE_KAFKA_IFT_JAAS}`. Общие параметры `.all.` дополняются выбранным профилем; producer-подключения независимо задаются в `kafka-producers.properties`. В `test.properties` остаётся только выбор тем наблюдения `kafka.topics`; старые `kafka.bootstrap.servers` и `kafka.property.*` нужно перенести в нативный профиль.

Все значения секретов из `${...}` берутся только из игнорируемого `secure.local.override.properties`; `secure.local.properties` — версионируемый шаблон. Переменные окружения, JVM properties и `gradle.local.properties` не являются источниками секретов/подключений. Полный порядок — в [Конфигурации проекта](../../../project/CONFIGURATION.md).

Контроллер корпоративного стенда должен реализовать описанный протокол поверх его существующих средств управления и наблюдения; сервер контроллера в этот пакет не входит. Без настроенного контроллера соответствующие сценарии завершаются ошибкой предусловия, сохраняя номер TP в Allure. Одного prepared=true недостаточно.

REST-клиент контроллера скрывает Authorization в логах и по умолчанию использует общий PKCS12 из explab2972.<env>.keystore и keystore.pass. Для отдельного сертификата задать control.keystore и control.keystore.pass. Для контроллера, требующего только Bearer, явно задать control.mtls.enabled=false. Проверка сертификата HTTPS включена: используйте корпоративный JDK с доверенным CA. Ссылки на пароли разрешает SecurePropertyResolver из `secure.local.override.properties`; сертификаты и секреты в пакет не включаются.

## Проверка возможностей и жизненный цикл

GET /capabilities возвращает environment, точно совпадающий с env, и capabilities — объект boolean-полей:

* sessions и observations — изолированные сессии, коррелированные снимки scheduler/audit/monitoring;
* jobs — управление ConfigRequestsJob и ExperimentConfigRequestJob;
* clock — реальная заморозка времени внутри проверяемого процесса;
* read-barrier — серверный барьер после чтения исходного статуса до сохранения;
* enhance-faults — 5xx и пустой ответ реальной зависимости enhance;
* delivery-faults — синхронная ошибка отправки Kafka, асинхронная ошибка Future и 503 callback;
* scheduler-faults — ошибка создания задачи scheduler.

POST /sessions/{UUID}/open принимает case, runId=UUID, environment. Принимается только одна выделенная сессия; среда и полномочия должны быть проверены сервером. POST /sessions/{UUID}/close восстанавливает jobs, время, барьеры и отказы, возвращая restored=true только после фактического восстановления. close вызывается try-with-resources, включая падение assertion. Данные экспериментов очищает отдельная FixtureService по прежним правилам.

GET /sessions/{UUID}/observations возвращает complete=true только для полного снимка, observedAt, session и массивы:

* tasks — фактические scheduler-задачи с id/status/scheduleDateTime/createdBy/splittingPointCode/actions;
* requests — реальные исходящие обращения с path/body/time, включая delete-planned и complete-action;
* audit — исходные тела audit-запросов message/params/changedParams;
* monitoring — исходные JSON события EXP_STATUS_CHANGE;
* configLogs — коррелированные строки configuration-service.

Вложения содержат исходные данные; преобразование схемы корпоративного источника к этому представлению выполняется адаптером. complete не должно означать лишь успешный HTTP-ответ при недоступном источнике. Уровень изоляции и окно наблюдения задаются сессией.

## Управляющие операции

Все операции ниже — POST /sessions/{UUID}/{operation}, JSON ответ и HTTP 200 при успехе. Неподдерживаемая возможность должна отсутствовать в capabilities, а операция — возвращать ошибку.

| operation | Запрос | Проверяемый ответ / действие |
| --- | --- | --- |
| jobs | mode=paused/general/experiment/both | Фактически применить и подтвердить расписания jobs; не возвращать до готовности сервиса |
| fault | mode=none/enhance-500/enhance-empty/kafka-sync/kafka-async/callback-503/scheduler-503 | Адресно внедрить отказ или полностью снять его |
| await-attempt | requestId, expId | observed=true и evidence только после фактической попытки через отказавшую зависимость |
| clock | epochMillis | frozen=true, serviceEpochMillis из процесса сервиса |
| clock-state | пустой объект | frozen=true, serviceEpochMillis и clockReads. Счётчик реальных обращений процесса к замороженным часам должен вырасти после бизнес-запроса |
| fixture | expId и startDt/endDt либо autoStopNull=true | Изменить только выделенный тестовый эксперимент; следующий GET проверяет результат |
| read-barrier | expId, participants=2 | armed=true: оба запроса остановятся после чтения исходного состояния, затем будут отпущены вместе |
| barrier-state | expId | arrivals=2, подтверждённые серверной точкой барьера |
| seed-legacy-config | expId, requestId, requestSource=UI, expAction=START | Создать только исходную запись V1_EXP_CONFIG с request_params.expIds; prepared=true, preconditionOnly=true, processingResultMutations=0, requestId, expId, legacyEnhance — массив реальных V1-данных из одного эксперимента |
| seed-tasks | expId, otherExpId | Создать PLANNED/IN_PROGRESS/COMPLETED для F и PLANNED для G с уникальными id |

TP-17 запускает два независимых Environment и двух пользователей. Проверяет как минимум один HTTP 200, отсутствие 5xx (остальные ответы допустимы 200 или 4xx), сохранённый AGREED, автора из этих пользователей и ровно одну START-задачу с правильными датой и автором. Обязательный отказ второго запроса документацией не задан. Клиентский CountDownLatch не заменяет серверный read-barrier.

## Требования к наблюдению и изоляции

Варианты TP-43/44/45 используют независимые сессии. Контроллер должен изменять только выделенные ресурсы и восстанавливать исходные настройки. Очистка общих очередей стенда недопустима; корреляция выполняется по UUID запросов и принадлежащим сценарию экспериментам.

TP-09/17 требуют реальных clock/read-barrier. Для TP-44 нужны доказательства синхронного отказа отправки, асинхронной ошибки Future и HTTP 503 callback. Один журнал брокера не подтверждает очередность завершения Future относительно возврата KafkaTemplate.send. Если стенд не обеспечивает требуемую точность наблюдения, соответствующая часть покрытия не считается подтверждённой.

TP-45 проверяет нормализацию Kafka messageInfo.requestSource в UI по PDF v17, стр. 3115; исходный requestSource сохраняется в очереди. Этот вопрос Q07 закрыт документацией. TP-41 требует отдельного подтверждения полного контракта публикаций. TP-09 проверяет именно T−1/T/T+1, а не приближение в несколько секунд. Наличие реализации всех 60 номеров не означает, что все комбинации исходного плана прошли на реальном корпоративном стенде.

TP-60 требует case.60.legacy-fixture-id: отдельный выделенный эксперимент version=4, status=IN_PROGRESS с валидными legacy-данными. Новая контрольная цепочка использует другой version=5 эксперимент. Контроллер подготавливает исходную legacy-запись очереди с requestParams.expIds; новый запрос передаёт splittingPointCode и expId. Приём V1_EXP_CONFIG старым REST endpoint не утверждается: проверяется обработчик и реальная публикация Kafka. Финальные статусы, результат обработки и callback контроллер не подменяет.

Контрольный эксперимент G из TP-35 сохраняется вместе с его scheduler-задачей для анализа; не удаляется как обычный DRAFT. После анализа контроллер/оператор адресно удаляет seed-задачи и соответствующие фикстуры.


## Дополнительные UC и корпоративный запуск

UC-06/13/14/16/20/23/24 требуют sessions/observations; кроме UC-06 также jobs.
Дополнительные возможности: UC-06 scheduler-faults; UC-13 mapper-switch;
UC-23 scheduler-delete-faults; UC-24 scheduler-execution.
Для UC-24 capabilities.schedulerImplementation должен быть real.

| operation | Запрос | Действие |
| --- | --- | --- |
| mapper | disabled=true/false | Фактическое переключение MAPPER для выделенного окружения; восстановление при close |
| fault | mode=scheduler-delete-503 | Реальный отказ удаления scheduler-задач; mode=none снимает отказ |
| scheduler-execute | taskId, expId | Обеспечить выполнение реальной задачи scheduler после scheduleDateTime; тест сам не посылает STOPPING |

UC-02/07/20/23/24/25/26 используют заранее подготовленные fixture-id; проверять владельца,
версию, статус, данные и запланированные задачи согласно коду предусловий.
UC-03/12/18 требуют фактически отключённого MAPPER; UC-17 — реального отказа configuration-service.
Наборы с противоположными состояниями MAPPER и разными отказами запускать отдельно,
после подготовки соответствующего состояния. Одна общая настройка launch-plan.fault
не может одновременно удовлетворить UC-17 и UC-23.

Патч содержит клиент протокола, но не корпоративный сервер управления. Наличие
case.NN.prepared=true само по себе не поднимает инфраструктуру. На общем стенде
останавливать jobs или внедрять отказы можно только в выделенном согласованном окне.
