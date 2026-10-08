h1. EXLAB-2891. План тестирования исправлений предрасчёта MAPPER

h2. Основание и цель

Проверить четыре изменения задачи:
# Валидация запроса предрасчёта через @Valid, включая вложенные объекты.
# Обработка одинаковых uniqueConfigurationId без ошибки Duplicate key.
# Валидация автоконфигурации SDK при создании контекста.
# Сохранение понятной цепочки причин SplitterException.

Основание: «Спецификации Сплиттера-v16-20261003_130355.pdf», последняя доступная документация на 05.10.2026.
Правила входного предрасчёта: стр. 204/213/224; актуализация связей при загрузке конфигурации: стр. 250–251;
свойства SDK: стр. 423–425; автоконфигурация SDK: §6.3, стр. 428.
Тестируемая точка — *MAPPER*. Папка сценариев: {{src/test/java/ru/sber/qa/splitter/EXLAB_2891}}.
Общий регресс алгоритмов сплиттования и сценарии REACTIONS в объём задачи не входят.

h2. Предусловия

# Выделенный тестовый MAPPER и сборка с проверяемым исправлением. Версия SDK фиксируется в Splitter runtime metadata.
# Общий Fabric8 имеет доступ к Deployment, ConfigMap и логам сервиса. Имена по умолчанию: Deployment/Service/container — splitter-mapper-service; ConfigMap правил — splitter-mapper-service-lib; ключ — splitter-rules-mapper.yml.
# Перед каждым сценарием прочитать реальные ссылки Deployment → ConfigMap. Проверить preliminary-calculation-enabled=true, нужный api-config-load, ALL=true, empty-objects-response-enabled=true, allow-result-without-main=false. При необходимости изменить значения и пересоздать pod через общий Fabric8, дождаться готовности.
# Для REST api-config-load=true; для Kafka api-config-load=false. В обоих профилях запросы предрасчёта и split выполняются через REST; меняется способ загрузки бизнес-конфигурации.
# Конфигурация MAPPER: эксперимент 101, слой 101, условие 10 по segment=gold, группа A на весь диапазон 0–10000, salt=ICP4GROUPD, параметры результата marker=101-A-10 и actionType=0 типа INTEGER.
# Каждый сценарий использует собственное пространство uniqueConfigurationId. Перед проверками таблица очищается валидным предрасчётом с splittingObjects=[].
# Для компонентных проверок подготовить обычный jar SDK из тестируемой сборки и его runtime-зависимости. Несовместимый API или отсутствующая зависимость — ошибка подготовки теста, а не ожидаемый отказ валидации.

После прогона общий механизм восстанавливает управляемые значения ConfigMap. Бизнес-конфигурация тестов автоматически не восстанавливается.

h2. Общие ожидаемые результаты

* Валидный предрасчёт: HTTP 200, тело JSON со счётчиками и UUID responseId; soConfigVersion соответствует запросу; errorCode отсутствует либо null. Счётчики — неотрицательные целые числа без преобразования строк/дробей.
* Невалидный запрос: HTTP 400. Для ошибки SDK — VALIDATION_FAILED и сохранённый корректный requestId; для раннего отказа MVC — status=400, error=Bad Request, точный path MAPPER. Успешный результат, EXCEPTION, NullPointerException и Duplicate key не допускаются.
* errorDetails необязателен; отсутствие подробностей стандартного MVC envelope не считается дефектом. Эта проверка не доказывает, что в теле перечислены все нарушенные поля.
* Split: сохранены requestId/splittingId, актуальная версия конфигурации, UUID responseId, полный набор запрошенных objectId без дублей. При сохранённой связи MAIN и ALL содержат по одному ожидаемому эксперименту с правильными группой, условием, salt, слоем и параметрами marker/actionType.
* Для доказательства использования предрасчёта текущие параметры противоречат сохранённым: сохранённый gold даёт связь при split с silver; сохранённый silver остаётся без связей при split с gold. Пустой результат представлен присутствующим объектом с objectResults=[].
* Перед негативным запросом контрольные связи подтверждаются. После отказа проверяются старые связи и отсутствие публикации нового ключа; повтор исходного набора требует copiedObjects=2, objectsAdded=0, objectsDeleted=0. Так обнаруживается и частичное добавление объектов без связей.

h2. Сценарии валидации запроса

Подготовка негативных случаев: сохранить U1=gold и U2=silver; проверить totalObjects=2, notLinkedObjects=1, linkedExps=1, totalExps=1 и контрольные split.
Каждая строка матрицы параметризации выполняется отдельно с собственными setup, телом теста, teardown и результатом Allure.

|| ID || Действия и данные || Ожидаемый результат || Автотест / число исполнений ||
| T01 | Отправить пустой набор splittingObjects=[], затем U1=gold и U2=silver; выполнить split с противоположными параметрами | Пустой набор допустим. Для двух объектов copied=0, added=2, deleted=0, notLinked=1, total=2, linkedExps=1, totalExps=1. Сохранённые связи используются | validRequest / 1 |
| T02 | requestId отсутствует, null или пустая строка | Общий отказ валидации; прежняя таблица сохранена | invalidField / 3 |
| T03 | splittingObjects отсутствует или null | Общий отказ валидации; прежняя таблица сохранена. Пустой массив проверяется положительно в T01 | invalidField / 2 |
| T04 | uniqueConfigurationId отсутствует, null или пустая строка | Общий отказ валидации; новый объект не опубликован | invalidField / 3 |
| T05 | objectParams отсутствует, null или [] | Общий отказ валидации, включая требование непустого массива | invalidField / 3 |
| T06 | paramCode отсутствует, null или пустая строка; отдельно paramValues отсутствует или null | Вложенный параметр валидируется; общий отказ, сохранность таблицы | invalidField / 5 |
| T07 | dataType отсутствует или null | Общий отказ валидации; прежняя таблица сохранена | invalidField / 2 |
| T08 | Первый объект валиден; во втором null uniqueConfigurationId. Отдельно null paramCode или dataType в последнем параметре второго объекта | Валидируются все элементы коллекций; весь запрос отклонён, включая первый новый объект; таблица сохранена | cascadeThroughAllElements / 3 |
| T09, T25 | Одновременно null uniqueConfigurationId и вложенный paramCode | Ошибка валидации без EXCEPTION/NPE; прежняя таблица сохранена. Перечисление всех нарушений не требуется | multipleViolations / 1 |
| T10 | После каждого из 18 вариантов invalidField отправить новый валидный набор RECOVERED=gold и выполнить split с silver | Сервис продолжает работать: copied=0, added=1, deleted=2, notLinked=0, total=1, linkedExps=1, totalExps=1; MAIN/ALL корректны | Включён в 18 исполнений invalidField |

Итого: *23 исполнения валидации* на режим. T10 и T25 не добавляют отдельные исполнения.

h2. Сценарии дубликатов

|| ID || Действия и данные || Ожидаемый результат || Автотест / число исполнений ||
| T11 | Дважды передать одинаковый объект uniqueConfigurationId=1646160 с десятью параметрами инцидента; условие эксперимента по configCommId=1646160. Выполнить split с тем же ключом и configCommId=999 | Нет Duplicate key; added=1, total=1, linkedExps=1, totalExps=1, copied/deleted/notLinked=0. Split использует сохранённую связь | incidentPayload / 1 |
| T12 | Среди трёх одинаковых DUP=gold передать OTHER=silver | В таблице два уникальных объекта: copied=0, added=2, deleted=0, notLinked=1, total=2, linkedExps=1, totalExps=1. DUP связан, OTHER без связей | duplicatesMixedWithUniqueObjects, совместно с T13/T14 |
| T13 | Разместить OTHER сначала после, затем перед тремя DUP | Оба порядка дают одинаковые счётчики и ожидаемые связи | Два параметризованных исполнения T12–T14 |
| T14 | Повторить тот же набор с новой soConfigVersion | copied=2, added=0, deleted=0; остальные счётчики и связи прежние; объекты не размножаются | Внутри обоих исполнений T12–T14 |
| T15, стендовый контроль T18 | Отдельный чистый рестарт MAPPER в REST-профиле. До бизнес-конфигурации отправить два одинаковых FRESH=gold и один CONTROL=gold. Затем загрузить подходящий эксперимент; выполнить динамический split нового ключа с gold и split сохранённых CONTROL/FRESH с silver | До загрузки totalExps=0, totalObjects=2, objectsAdded=2. После загрузки динамический контроль и оба сохранённых объекта имеют ожидаемые MAIN/ALL. Уникальный CONTROL отличает общий сбой актуализации от ошибки дубликатов | duplicateBeforeBusinessConfig / 1, отдельная задача fresh |

Данные инцидента T11:
{code:json}
{
  "uniqueConfigurationId": "1646160",
  "objectParams": [
    {"paramCode":"cjId","paramValues":["105901421"],"dataType":"INTEGER"},
    {"paramCode":"bbCjId","paramValues":["123456"],"dataType":"INTEGER"},
    {"paramCode":"needCode","paramValues":["refuel"],"dataType":"STRING"},
    {"paramCode":"modelId","paramValues":["16421"],"dataType":"INTEGER"},
    {"paramCode":"modelTemplate","paramValues":["2"],"dataType":"INTEGER"},
    {"paramCode":"modelSource","paramValues":["PIM"],"dataType":"STRING"},
    {"paramCode":"sellingProductId","paramValues":["9-XRJSFRQX"],"dataType":"STRING"},
    {"paramCode":"channel","paramValues":["50"],"dataType":"INTEGER"},
    {"paramCode":"templateId","paramValues":["232673821"],"dataType":"STRING"},
    {"paramCode":"configCommId","paramValues":["1646160"],"dataType":"INTEGER"}
  ]
}
{code}

h2. Компонентные сценарии автоконфигурации SDK

Используется настоящий SDK: изолированный Spring binding и фабрика splittingExecutor для MAPPER.
Это не полный старт приложения со всеми Kafka/БД-зависимостями. Перед отрицательной проверкой и после исправления выполняется валидный контроль.

|| ID || Действия и данные || Ожидаемый результат || Автотест / число исполнений ||
| T18, T24 | Создать контекст с полным валидным набором свойств MAPPER | Контекст создан; свойства привязаны к MAPPER | validProperties / 1 |
| T19, T21, T24 | По одному задать INVALID_2891 для quantum, api-config-load, rules-config-code, preliminary-calculation-enabled, all-rule-code-exp-enabled, empty-objects-response-enabled, allow-result-without-main | Refresh отклоняет неверный тип. Цепочка содержит отказ binding/validation и имя поля. Исправленный набор успешно создаёт контекст | invalidProperty / 7 |
| T20, T21, T24 | Правила MAPPER: вложенный param-code=null; повреждённый YAML; неизвестный enum value-type; отсутствующий файл | Фабрика не создаёт executor. Для вложенного null — ConstraintViolationException и paramCode; для остальных — ошибка загрузки/конфигурации без NPE. После исправления executor создаётся | invalidRules / 4 |
| T20, T24 | По одному явно задать пустую строку для splitting-config, splitting-config-request, splitter-monitoring, splitter-reporting, splitter-config-info, splitting-request-converted | Контекст отклоняет пустое вложенное поле Kafka topic; причина указывает настройку. После исправления контекст создаётся | invalidKafkaTopic / 6 |

Отсутствие свойства с документированным значением по умолчанию не используется как негативный случай.
Итого: *18 компонентных исполнений автоконфигурации*.

h2. Компонентные сценарии SplitterException

Через контролируемый отказ зависимости вызывается настоящий calculatePreliminary SDK. Исключение и место возникновения фиксируются до вызова.

|| ID || Действия || Ожидаемый результат || Автотест / число исполнений ||
| T26 | Исходное IllegalStateException без cause | Возвращён SplitterException с EXCEPTION и исходным requestId. В причинах сохранены тип, сообщение и место возникновения исходного исключения | originalCauseChain, вариант T26 / 1 |
| T27 | IllegalStateException с вложенным IllegalArgumentException | Сохранены обе причины в исходном порядке, их сообщения и места возникновения. Дополнительные обёртки допустимы | originalCauseChain, вариант T27 / 1 |
| T28 | Зависимость выбрасывает готовый SplitterException с VALIDATION_FAILED, сообщением, деталями и причиной | Сохранены код, requestId, errorMessage, уже заданные errorDetails и исходная причина. Тот же экземпляр Java-объекта не требуется | existingSplitterException / 1 |

errorDetails для нового неожиданного исключения необязателен по спецификации. Читаемость проверяется по сохранённой цепочке, а не по обязательному наличию деталей в JSON.
Итого: *3 компонентных исполнения исключений*, вместе с автоконфигурацией — *21*.

h2. Ограничения и исключения

|| ID / область || Причина ||
| T16: одинаковый ключ с разными параметрами | Документация не определяет выбор первого/последнего значения либо отказ. Приёмочное ожидание не выдумывается; случай не заявлен автоматизированным |
| T17: исходный путь cjmapper / kafka-mapping-config | Тестирование на MAPPER воспроизводит вход SDK. Kafka-загрузка бизнес-конфигурации не равнозначна исходному consumer другого приложения; этот путь не заявлен покрытым |
| T22–T23: начальная бизнес-конфигурация | Исключены из данной трактовки автоконфигурации: документация относит её к Spring-конфигурации SDK. Fresh проверяет отдельный явно описанный жизненный цикл |
| Полный startup всего сервиса | Компонентные проверки binding/factory не заменяют интеграционный старт приложения со всеми внешними зависимостями |
| Независимость responseId от requestId | В этой задаче проверяется формат UUID; отдельное требование независимости не проверяется |

h2. Запуск, отчётность и критерии завершения

{code:powershell}
.\gradlew.bat testClasses
.\gradlew.bat -I gradle/exlab2891.init.gradle exlab2891OfflineChecks
.\gradlew.bat -I gradle/exlab2891.init.gradle exlab2891MapperRest
.\gradlew.bat -I gradle/exlab2891.init.gradle exlab2891MapperKafka
.\gradlew.bat -I gradle/exlab2891.init.gradle exlab2891MapperFresh
.\gradlew.bat -I gradle/exlab2891.init.gradle exlab2891SdkChecks "-Psdk2891Classpath=C:\Work\splitter-sdk-runtime"
.\gradlew.bat allureReport --clean
.\gradlew.bat testOpsUpload
{code}

* Перед независимым прогоном сохранить старые результаты отдельно. Не выполнять clean между REST, Kafka, fresh и SDK, если нужен общий отчёт.
* Параметр --clean у allureReport разрешает перезаписать только HTML-отчёт; исходные результаты сохраняются. Не заменять команду на отдельную задачу clean.
* Основные задачи: 26 REST + 26 Kafka + 1 fresh. SDK: 21 отдельно. Отсутствие SDK-прогона фиксируется как непроверенная часть покрытия, а не успешный результат.
* Стендовые и SDK-результаты поступают в общий build/allure-results. Автоматической отправки нет; testOpsUpload использует общий механизм подготовки проекта.
* 49 OfflineChecks проверяют инфраструктуру/ассершены и находятся отдельно в build/reports/exlab2891-offline/allure-results. Они не подтверждают исправление сервиса.
* Allure: setup → test body → teardown; параметры варианта; Splitter runtime metadata; состояние управляемых ключей ConfigMap; plain text лог pod за интервал начала/конца операции. События pod добавляются при сбое; квота и readiness timeline не прикладываются.
* Критерий завершения: пройдены применимые сценарии на целевой исправленной сборке, версия зафиксирована, результаты доступны в TestOps, падения разобраны и ограничения явно отмечены.

*Текущая фактура, не итог приёмки v2:* в присланном прогоне v1 из 53 стендовых исполнений 50 прошли и 3 упали:
два отказа проверки objectParams=[] (REST/Kafka) и один отказ fresh при актуализации связей. Усиленные ассершены на сохранённых ответах подтверждают те же нарушения.
Новые контрольные операции v2 требуют нового стендового прогона. Компонентный SDK-профиль локально не выполнялся.

h2. Уточнение АК от 05.10.2026

Комментарии АК приоритетны. T05: objectParams=[] должен отклоняться, требование сохранено. T15/T18: при загрузке нового конфига таблица предрасчёта обязательно пересчитывается. Сценарий duplicateBeforeBusinessConfig уже проверяет неизвестный DYNAMIC/gold, сохранённый CONTROL/silver и FRESH/silver, поэтому ожидания оставлены без изменений. Успех динамического контроля при отсутствии сохранённых связей указывает на проблему актуализации, а не на неподходящий эксперимент. Прежние результаты остаются исторической фактурой, нового стендового прогона нет.
