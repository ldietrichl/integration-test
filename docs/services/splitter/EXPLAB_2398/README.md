# EXPLAB-2398. Мониторинг метода предварительного расчета связей

Пакет: `ru.sber.qa.splitter.EXPLAB_2398`.

## Что покрыто автотестами

| ID | Класс/метод | Проверка | Статус |
|---|---|---|---|
| EXPLAB-2398-PC-01 | `SplitterPrecalcMonitoring2398FlowTest.repeatedPrecalcShouldWriteLoadedMonitoringWithStableCounters` | Повторный `pre-calculate` по тому же полному списку пишет `LOADED`, `copiedObjects=2`, `objectsAdded=0`, `objectsDeleted=0`, `notLinkedObjects=1`, `totalObjects=2`, `linkedExps=1`, `totalExps=1`; дополнительно проверяется split после predcalc по `uniqueConfigurationId`. | Auto |
| EXPLAB-2398-PC-02 | `SplitterPrecalcMonitoring2398FlowTest.exhaustiveObjectListShouldWriteAddedDeletedCounters` | Полный список объектов является исчерпывающим: при переходе `[A,B] -> [A,C]` мониторинг показывает `copiedObjects=1`, `objectsAdded=1`, `objectsDeleted=1`. | Auto |
| EXPLAB-2398-PC-03 | `SplitterPrecalcMonitoring2398FlowTest.notLinkedObjectsAndLinkedExpsShouldBeCalculatedFromPrecalcLinks` | Два объекта без связей дают `notLinkedObjects=2`, `linkedExps=0`, `totalExps=1`. | Auto |
| EXPLAB-2398-PC-04 | `SplitterPrecalcMonitoring2398FlowTest.monitoringEventShouldBeCorrelatedByRequestIdAndSoConfigVersion` | Контракт и корреляция monitoring event: `function=PRE_CALC_REQUEST`, `requestIdIn`, `soConfigVersion`, `service=splitter--service`, `completedTimestamp`, splitting point. | Auto |
| EXPLAB-2398-PC-05 | `SplitterPrecalcMonitoring2398FlowTest.validationFailedShouldWriteMonitoringEvent` | Ошибка структуры pre-calculate пишет `VALIDATION_FAILED` в monitoring topic. | Auto |
| EXPLAB-2398-PC-06 | `SplitterPrecalcMonitoring2398ManualFlowTest.disabledPrecalcShouldWriteRejectedMonitoringEvent` | При выключенном предрасчете ожидается `PRECALC_NOT_ENABLED` и monitoring result `REQUEST_REJECTED_PRECALC_NOT_ENABLED`. | Manual / Disabled |

## Запуск

```bash
./gradlew test --tests "ru.sber.qa.splitter.EXPLAB_2398.*" \
  -Dsplitter.precalc.monitoring.kafka.env=dev \
  -Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log \
  -Dsplitter.precalc.monitoring.timeout.seconds=30
```

Для Windows CMD:

```cmd
gradlew.bat test --tests "ru.sber.qa.splitter.EXPLAB_2398.*" -Dsplitter.precalc.monitoring.kafka.env=dev -Dsplitter.precalc.monitoring.topic=omon_explab_splitter_log -Dsplitter.precalc.monitoring.timeout.seconds=30
```

## Важные предусловия

1. На стенде должен быть включен предрасчет.
2. Kafka consumer должен иметь доступ к `omon_explab_splitter_log`.
3. Автотесты используют REST для подготовки состояния: загрузка config -> seed pre-calculate -> проверяемый pre-calculate.
4. Первый seed-запрос нужен, чтобы не зависеть от текущего состояния общей таблицы предрасчета на стенде.
