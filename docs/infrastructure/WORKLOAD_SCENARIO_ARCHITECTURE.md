# Общие шаги стенда и сценарии сплиттера

Рефакторинг EXPLAB_2690, EXPLAB_2885 и EXLAB_2891 от 05.10.2026.

## Структура

```text
src/test/java/
├── ru/sber/qa/splitter/
│   ├── EXPLAB_2690/                 сценарии групп MAPPER / REACTIONS
│   ├── EXPLAB_2885/                 сценарии предрасчёта REACTIONS
│   └── EXLAB_2891/                  валидация, дубликаты, startup / SDK
├── support/splitter/
│   ├── cases/                      MethodSource: наборы параметров 2690
│   └── *Fixtures.java              заглушки и данные локальных проверок
└── infrastructure/kubernetes/
    └── WorkloadArchitectureTest    проверки общего механизма без стенда

src/main/java/
├── flow/WorkloadFlow               получает сервис из Environment
├── steps/
│   ├── container/
│   │   ├── WorkloadScenarioSteps   подготовка сценария, рестарт, диагностика
│   │   └── KubernetesWorkloadSteps применение состояния и ожидание готовности
│   ├── flow/splitter/
│   │   ├── workedgroup/            группы, альтернативы, no-MAIN (2690)
│   │   ├── reactions/              предрасчёт, профили, мониторинг (2885)
│   │   └── mapper/                 предрасчёт, валидация, дубликаты (2891)
│   ├── sdk/splitter/               вызовы SDK, startup, цепочка исключений
│   └── reporting/ReportingSteps   Allure-шаги с исходным результатом проверки
├── dto/splitter/precalc/
│   ├── MapperPrecalcRequests       фабрика запросов и конфигураций MAPPER
│   └── ReactionsPrecalcRequests    фабрика запросов и конфигураций REACTIONS
├── util/
│   ├── concurrent/ScenarioConcurrency    запуск/ожидание параллельных операций
│   ├── splittercheck/             контракты ответов, MAIN/ALL, счётчики
│   └── validation/ExceptionChainAssertions
├── config/
│   ├── services/splitter/          профили стенда, правила, имена параметров
│   ├── environment/special/       конфигурации сервисов Environment
│   └── extensions/
│       ├── WorkloadScenario       подключает общий lifecycle
│       ├── WorkloadRunScopeExtension       владеет сессиями и восстановлением
│       ├── WorkloadScenarioEvidenceExtension  границы операции
│       └── *PrecalcBindings        настройки конкретных профилей
└── infrastructure/kubernetes/
    ├── KubernetesWorkloadService   сервис Platform V AT
    ├── KubernetesWorkloadControl   ConfigMap, pod, locks, rollback через Fabric8
    ├── WorkloadTarget / ConfigMapState / WorkloadStates
    ├── WorkloadScenarioEvidence    журнал и результат текущего сценария
    └── WorkloadEvidence / OcServiceLogs     ConfigMap и текст логов pod
```

Основная цепочка: **тест → шаги сервиса → WorkloadFlow → WorkloadScenarioSteps →
KubernetesWorkloadSteps → KubernetesWorkloadService / KubernetesWorkloadControl → Fabric8**.
Существующие DTO-операции 2690 продолжают использовать общую базу
`AbstractSplitterV9FlowTest` из `src/main/java`; реализация REST остаётся в `RestCustomSteps` / `SplitterRestSteps`.

В классах трёх заявок остались тестовые методы и декларативные настройки.
Методы построения данных, вспомогательные проверки, SDK-вызовы, ожидания и работа
с мониторингом вынесены. Поставщики `MethodSource` находятся в test-support;
JUnit Params не стал зависимостью основного кода. Варианты 2690 с запретом no-MAIN
задаются `@WorkedGroupPolicy(allowWithoutMain = false)` и наследуют прежние сценарии.
Имена классов тестов, методов, ID и параметризованные варианты сохранены.

## Границы ответственности

EXPLAB-3056 использует тот же WorkedGroupProfile с собственным каталогом правил
и пространством explab3056. MapperAlternativeMarkupSteps отвечает только за
прямую разметку/откат и последовательности бизнес-запросов; DTO расширяет
WorkedGroupRequests, проверки расширяют WorkedGroupAssertions, параметры находятся
в MapperAlternativeMarkupCases. Подготовка стенда, lease, диагностика и
SplitterKafkaReportClient переиспользованы без нового lifecycle. Карта покрытия
и ограничения: `docs/services/splitter/EXPLAB_3056/README.md`.

Для вариантов правил профиль WorkedGroupProfile принимает преобразование YAML
и явные application overrides. Старые вызовы делегируют без изменений;
преобразование применяется до подготовки общей сессии. Overrides требуют
включённого управления флагами, используют существующие mappings environment
и тот же lease/backup/restart/restore. Нового клиента или lifecycle нет.
Варианты 3056 задаются аннотацией метода ScenarioProfile; подготовка остаётся
в BeforeEach, проверка — в теле, диагностика — в AfterEach. Фабрика
MapperPrecalcRequests.fromSplit сохраняет ключи и параметры объектов; сценарии
предрасчёта намеренно меняют runtime-параметры для обнаружения live fallback.
MAPPER/REACTIONS имеют отдельные Gradle-фильтры и соответствующие read-only gates.

EXPLAB-2984 использует тот же lifecycle через CandidateSelectionSteps и перегрузку
WorkedGroupProfile с собственным пространством настроек, режимом REST/Kafka-загрузки
и явным выключением предрасчёта. Прежний вызов профиля 2690 сохраняет свои настройки.
DTO создаёт WorkedGroupRequests, параметры сценариев находятся в CandidateSelectionCases,
проверки строк и флагов расширяют WorkedGroupAssertions. Полный КАП и публичный ответ
имеют разные ожидаемые ALL; старый no-MAIN контракт 2690 не изменён.
SplitterKafkaReportClient использует существующий KafkaService и BoundedKafkaObservation:
позиции partitions фиксируются до split, наблюдение ограничено по времени и памяти.
Подробная матрица и неподтверждённые ожидания: `docs/services/splitter/EXPLAB_2984/README.md`.

Общий механизм не знает про номера заявок, splitting point и параметры сплиттера.
`WorkloadTarget` определяет workload, Service, порт и контейнер. Функция желаемого
состояния возвращает `ConfigMapState` и выполняется перед **каждым** сценарием,
включая повторное использование сессии. Конфликтующие значения одного ключа
отклоняются до записи. Существующие `prepare` / `ensureState` меняют ConfigMap и
пересоздают pod только при изменении состояния. Явный fresh-профиль дополнительно
запрашивает рестарт, как и до переноса.

Владение сессией, блокировка, проверка UID/готовности, ожидание маршрута приложения
и восстановление исходного состояния остаются в общем Fabric8-механизме.
Сессии закрываются в конце JUnit-run. Не создаются отдельные клиенты Kubernetes
в сценариях; шаги получают `KubernetesWorkloadService` через `Environment`.

Профили сплиттера отвечают за правила и значения флагов, выбор MAPPER/REACTIONS
и маршрут проверки версии. Бизнес-проверки не перенесены в Kubernetes-слой.

## Allure

Сохраняется структура setup → test body → teardown. `@BeforeEach` подготавливает
состояние; `BeforeTestExecution` / `AfterTestExecution` ограничивают операцию.
`@AfterEach` прикладывает диагностику; callback расширения завершает сбор при сбое
подготовки и очищает контекст. Повторный `finish()` не дублирует вложения.

При штатном завершении: runtime metadata сплиттера, состояние управляемых ключей
ConfigMap, лог контейнера сервиса за интервал операции в `text/plain`, короткий
журнал сценария. При ошибке добавляются события pod. Квоты и readiness timeline
в диагностику сценария не добавляются. Прежние ограничения объёма логов сохраняются;
недоступность/усечение логов обозначается в диагностике.

Настройки каталогов бизнес-прогонов и `testOpsUpload` не менялись. Локальные
проверки механизма пишутся отдельно в `build/reports/workload-architecture`, чтобы
не попадать в загрузку бизнес-результатов.

## Применение для другого сервиса

В конфигурации Environment должен быть зарегистрирован `KubernetesWorkloadService`
через `ConfiguredServiceHolder.of(KubernetesWorkloadService.class)`; это уже есть
в `EnvironmentConfigurationExample`. В stand.properties нужны реальные настройки
workload и разрешения общего Kubernetes-механизма.

Пример класса шагов для условного billing-сервиса (имя workload, флаг, адрес и
read-only маршрут нужно заменить на реальные настройки сервиса):

```java
@WorkloadScenario("billing")
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigurationExample.class)
public abstract class BillingSteps extends Flows implements WorkloadFlow {
    @BeforeEach
    void prepareBilling() {
        var target = WorkloadTarget.from(new StandSettings(), "billing");
        workloadScenarioSteps().prepare(target,
                session -> session.environmentState(Map.of("BILLING_ENABLED", "true")),
                WorkloadHttpReadinessProbe.ingressGet(
                        System.getProperty("billing.baseUri"), "/health"),
                WorkloadScenarioEvidence.current(), false);
    }

    @AfterEach
    void collectBillingEvidence() {
        workloadScenarioSteps().collectEvidence();
    }
}
```

Наследник содержит только тестовые сценарии. Для нового сервиса не нужны копии
расширения диагностики или кода управления pod. Настройки сессии и lifecycle
получаются через ту же архитектуру Platform V AT. Для нескольких тестов одного
изменяемого стенда следует задать общий `@ResourceLock` и последовательное выполнение.

## Проверка переноса

* Скомпилированы основные классы рефакторинга и все Java-сценарии трёх заявок
  с доступными корпоративными JAR и классами текущего проекта, Java 17.
* 96 локальных проверок прошли: контракты, журнал, точный интервал логов,
  plain-text вложения, ошибки чтения логов, конфигурация, повторные сценарии и
  применение общего механизма к workload другого сервиса.
* Автоматически сравнены тела 125 тестовых методов с базовой версией:
  отличия только в ссылках на перенесённые классы. В тестовых классах нет
  вспомогательных методов/полей.
* Allure Gradle 2.11.2 перенесён в `gradle/offline-maven` и проверен с пустым
  Gradle-кэшем в offline-режиме. Туда же добавлены девять предоставленных модулей
  `platform-v-at-framework:1.10.3-beta`. Полный `testClasses` основного проекта
  останавливается на остальных отсутствующих offline-зависимостях (Perfeccionista,
  REST Assured и другие). Проверка через существующий режим localLibs дополнительно
  выявила отсутствующие зависимости несвязанных с задачами AI-классов. На стенде после
  установки необходимы `testClasses` и повторные бизнес-прогоны. В этом рефакторинге
  запросы на корпоративный стенд не выполнялись.

```powershell
.\gradlew.bat testClasses
.\gradlew.bat -I gradle/workload-architecture.init.gradle workloadArchitectureCheck
```
