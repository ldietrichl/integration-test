# Маппинг analytic tests на детали проверки аналитика

Каждый тест помечен Allure tag через `@AnalyticTag(...)`. Значение tag соответствует колонке аналитика `Детали проверки Заново сформулировано` или ближайшему нормализованному пункту.

## Analytic05AllResultAndConditionBindingFlowTest

- `allShouldContainEveryExperimentLinkedToObject` — **AN-ALL-01. ALL содержит все эксперименты, связанные с одним объектом**
  - tag: `Аналитика: К объекту привязалось много экспериментов. Проверяем, что все они присутствуют в ALL.`
- `allShouldNotMixExperimentsBetweenObjects` — **AN-ALL-02. ALL по нескольким объектам не смешивает чужие objectSelectConditions**
  - tag: `Аналитика: У эксперимента группы привязались к разным объектам; проверяем, что ALL не смешивает чужие связи.`
- `conditionBindingShouldKeepMatchedConditionForResult` — **AN-ALL-03. При нескольких condition в группе используется condition, по которому привязался объект**
  - tag: `Аналитика: В эксперименте много condition, в группе один condition; result должен быть связан с condition, по которому привязался объект.`
- `manyConditionsInExperimentAndGroupShouldKeepMatchedConditionId` — **AN-ALL-04. Много condition в experiment и много condition в group: выбирается корректный conditionId**
  - tag: `Аналитика: В эксперименте много condition, в группе много condition; выбирается корректный conditionId и связанный result.`
- `nonMatchedGroupWithHigherActionTypeShouldNotAffectMainOrAllResultParams` — **AN-ALL-05. Несработавшая группа с высоким actionType не попадает в MAIN и не влияет на выбор**
  - tag: `Аналитика: Эксперимент привязан к объекту не сработавшей группой с высоким actionType; несработавшая группа не должна влиять на MAIN.`
- `allRuleShouldDisappearWhenAllOutputDisabled` — **AN-ALL-06. При выключенной опции отдавать ALL результат ALL не возвращается**
  - tag: `Аналитика: Опция \"Отдавать ALL\" выключена. ALL не должен возвращаться.`
## Analytic08AlternativeMarkupFlowTest

- `ordinaryLinkedExperimentsOnSameObjectShouldNotBecomeAlternative` — **AN-ALT-01. При traffic-based-alternative=true два обычных связанных exp на одном объекте не размечаются альтернативой**
  - tag: `Аналитика: Эксперимент привязан к объекту несколькими группами; альтернатива не должна срабатывать.`
- `documentedAlternativeScenarioShouldStayFalseOnCurrentConfigMap` — **AN-ALT-02. Связанные группы на одном объекте не размечаются альтернативой**
  - tag: `Аналитика: Эксперимент привязан к одному объекту сразу несколькими группами; группа, привязанная к тому же объекту, не должна размечаться альтернативой.`
- `oneExperimentTwoObjectsShouldMarkOtherObjectAsAlternative` — **AN-ALT-04. Один эксперимент на двух объектах размечает альтернативой объект из несработавшей группы**
  - tag: `Аналитика: Простейший A/B: один эксперимент, два объекта, группа A привязана к первому объекту, группа B ко второму; объект из несработавшей группы размечается альтернативой.`
- `alternativeMarkupAndRollbackShouldBeCoveredWithConfigMapMatrix` — **AN-ALT-03. Alternative markup и rollback при разных списках АТ**
  - tag: `Аналитика: Меняем список actionType для alternative markup и rollback, перепроверяем результат.`
## Analytic01ConfigLifecycleFlowTest

- `restConfigShouldLoadAndBecomeActiveForSplit` — **AN-CFG-01. REST config загружается и становится активным для split**
  - tag: `Аналитика: Отправить запрос с конфигом, должен быть ответ; после загрузки корректный split должен работать на активной конфигурации.`
- `olderVersionWithoutForceShouldNotOverrideActiveConfig` — **AN-CFG-02. Более старая версия без forceConfigLoad не меняет активный config**
  - tag: `Аналитика: Получение конфигурации с версией меньше загруженной и равной загруженной. Должна быть проигнорирована.`
- `forceConfigLoadShouldOverrideVersionControl` — **AN-CFG-03. forceConfigLoad=true позволяет загрузить версию ниже активной**
  - tag: `Аналитика: Загрузка конфига с forceConfigLoad. Версия больше, меньше и равна текущей. Должна быть загружена.`
- `invalidConfigShouldNotBecomeActive` — **AN-CFG-04. Невалидный config не должен становиться активным**
  - tag: `Аналитика: Получение конфигурации с некорректным экспериментом. Должна быть проигнорирована.`
- `foreignSplittingPointConfigShouldNotOverrideMapperConfig` — **AN-CFG-05. Config с чужой splittingPointCode не должен подменять MAPPER config**
  - tag: `Аналитика: Получение конфига с точкой сплиттования, отличной от конфигурации. Должна быть проигнорирована.`
- `missingSplittingPointConfigShouldNotBecomeActive` — **AN-CFG-06. Config без splittingPointCode не должен становиться активным**
  - tag: `Аналитика: Получение конфига без точки сплиттования. Должна быть проигнорирована.`
- `malformedConfigJsonShouldNotBecomeActive` — **AN-CFG-07. Malformed JSON в /config не должен становиться активным**
  - tag: `Аналитика: Получение конфигурации с некорректным JSON. Должна быть проигнорирована.`
## Analytic09FilteringSuppressionFlowTest

- `nonFilteredActionTypeShouldReturnObjectWithFilteredFalse` — **AN-FILT-01. Нефильтруемый actionType возвращает объект с filtered=false**
  - tag: `Аналитика: Проверяем фильтрацию по actionType; actionType не из списка фильтруемых должен давать filtered=false.`
- `filteredActionTypeShouldSuppressObjectOnCurrentConfigMap` — **AN-FILT-02. Фильтруемый actionType из текущей ConfigMap возвращает объект с filtered=true**
  - tag: `Аналитика: Делаем эксперименты с actionType из списка фильтруемых. Объект должен получать флаг filtered=true.`
- `filteredObjectsShouldBeReturnedWhenConfigured` — **AN-FILT-03. При текущей ConfigMap объект с filtered=true возвращается в ответе**
  - tag: `Аналитика: Отдавать отфильтрованные = true. Объекты с filtered=true должны попадать в ответ.`
- `filteringShouldRespectAlternativeRollback` — **AN-FILT-04. Фильтрация в комбинации с alternative rollback**
  - tag: `Аналитика: Комбинируем фильтрацию на объекте с альтернативой и откатом альтернативы.`
## Analytic04GroupDistributionFlowTest

- `fullRangeGroupShouldAlwaysBeSelected` — **AN-GROUP-01. Группа с диапазоном 0..10000 гарантированно выбирается**
  - tag: `Аналитика: У эксперимента одна группа занимает весь диапазон; проверяем корректность выбора группы.`
- `objectOutsideAllGroupRangesShouldNotHaveMainAssignment` — **AN-GROUP-02. Объект вне всех share-диапазонов не получает MAIN**
  - tag: `Аналитика: У эксперимента одна группа занимает не весь диапазон; при попадании вне интервала MAIN не должен формироваться.`
- `multipleGroupsShouldSelectGroupBySpreadValue` — **AN-GROUP-03. Несколько групп покрывают разные диапазоны и выбирается группа по spreadValue**
  - tag: `Аналитика: У эксперимента несколько групп занимают весь диапазон; проверяем корректность выбора группы по spreadValue.`
- `batchWithOnlyMatchedObjectsShouldReturnResultsForEveryObject` — **AN-GROUP-04. Batch: все объекты, подходящие под условия, получают MAIN и ALL**
  - tag: `Аналитика: Все объекты привязались к экспериментам; проверяем результат по каждому объекту batch-запроса.`
- `groupBoundaryValuesShouldBeCoveredByDedicatedSpreadMatrix` — **AN-GROUP-05. Граничные значения shareFrom/shareTo и внутренние границы**
  - tag: `Аналитика: Проверяем попадание распределения на начало и конец интервала группы, включая внутренние границы.`
## Analytic10LayerPriorityFlowTest

- `layeredExperimentsShouldChooseMainByLayerPriority` — **AN-LAYER-01. Среди экспериментов со слоями MAIN выбирается по layerPriority**
  - tag: `Аналитика: На одном объекте эксперименты без слоя и со слоями; выбор происходит по приоритету слоя только из экспериментов со слоями.`
- `equalLayerPriorityShouldChooseMinimalExpId` — **AN-LAYER-02. При одинаковом layerPriority tie-break выполняется по expId**
  - tag: `Аналитика: Несколько экспериментов с одинаковыми слоями на объекте; должен выбираться правильный приоритет и среди них по id.`
- `experimentsWithoutLayerShouldChooseMinimalExpIdWhenPriorityIsEqual` — **AN-LAYER-03. Без слоя MAIN выбирается по expId при одинаковом actionType**
  - tag: `Аналитика: На одном объекте эксперименты без слоя. Выбор происходит по id.`
- `layerPriorityMinMaxConfigShouldBeCoveredWithConfigMapMatrix` — **AN-LAYER-04. Переключение min/max правил выбора layerPriority и expId**
  - tag: `Аналитика: Меняем в конфигурации параметр выбора по приоритету слоя min/max и параметр выбора по id min/max.`
## Analytic03LinkRulesOperatorsFlowTest

- `andInsideRuleBlockShouldRequireAllExpressions` — **AN-RULES-01. AND внутри блока: объект привязывается только при выполнении всех выражений**
  - tag: `Аналитика: Проверяем работу правил привязки без ИЛИ; несколько выражений внутри блока должны выполняться как AND.`
- `orBetweenRuleBlocksShouldMatchAnyBlock` — **AN-RULES-02. OR между блоками: объект привязывается при выполнении любого блока**
  - tag: `Аналитика: Проверяем условие с ИЛИ; объект должен привязаться при выполнении любого блока OR.`
- `missingParamInOneOrBlockShouldNotBlockOtherMatchingBlock` — **AN-RULES-03. Отсутствующий параметр в одном OR-блоке не мешает привязке по другому блоку**
  - tag: `Аналитика: Условие с ИЛИ и отсутствующий параметр в одном блоке выражений; объект должен привязаться по выражениям, где нет отсутствующего параметра.`
- `coreOperatorsAndDataTypesShouldBeCoveredByRestMatrix` — **AN-RULES-04. Матрица базовых операторов rules для INTEGER/STRING**
  - tag: `Аналитика: Проверяем работу реализуемой через REST матрицы операторов: equal, not_equal, more, less, more_equal, less_equal, in, not_in, is_null, is_not_null для INTEGER и STRING.`
## Analytic07MapperMainPriorityFlowTest

- `mainShouldUseHighestActionTypePriorityFromReturnedObject` — **AN-MAIN-01. MAIN выбирается по максимальному приоритету actionType из неподавляемого набора**
  - tag: `Аналитика: Проверяем все actionType; побеждать должен эксперимент с максимальным приоритетом actionType.`
- `sameActionTypeShouldChooseMinimalExpId` — **AN-MAIN-02. При одинаковом actionType побеждает меньший expId**
  - tag: `Аналитика: Привязано несколько экспериментов с одинаковыми actionType; побеждает эксперимент с минимальным id.`
- `nonMatchedGroupWithHigherActionTypeShouldNotWinMain` — **AN-MAIN-03. Несработавшая группа с более высоким actionType не должна становиться MAIN**
  - tag: `Аналитика: В несработавших группах максимальный приоритет actionType; побеждает только эксперимент по сработавшей группе.`
- `manyConditionsInOneGroupShouldStillReturnSingleMainResult` — **AN-MAIN-04. Несколько conditions в одной группе дают один MAIN result**
  - tag: `Аналитика: Проверки делаем с несколькими condition в группе; результат MAIN должен быть один.`
## Analytic06PrecalcFlowTest

- `precalculatedObjectShouldReturnNonEmptySplitResult` — **AN-PREC-01. Предрасчитанный object по uniqueConfigurationId возвращает непустой split result**
  - tag: `Аналитика: Добавляем в предрасчет запрашиваемый объект и делаем сплиттование; результат должен быть корректный и непустой.`
- `runtimeSplitBeforeAndAfterPrecalcShouldStayPositive` — **AN-PREC-02. Runtime split до и после predcalc возвращает одинаково непустой результат**
  - tag: `Аналитика: Делаем сплиттование без предрасчета и с предрасчетом; результат должен быть одинаково корректным.`
- `objectNotLinkedByPrecalcShouldReturnEmptyResult` — **AN-PREC-03. Если объект не был добавлен в predcalc как positive, split возвращает пустой результат**
  - tag: `Аналитика: Делаем сплиттование при наличии предрасчета, но в нем нет запрашиваемого объекта.`
- `changedObjectParamsForSameUniqueIdShouldNotChangePrecalculatedResult` — **AN-PREC-04. Изменение параметров объекта при том же uniqueConfigurationId не меняет результат**
  - tag: `Аналитика: Изменяем конфигурацию объекта в запросе после предрасчета; результат не должен измениться для того же uniqueConfigurationId.`
- `precalcShouldImprovePerformanceUnderLoad` — **AN-PREC-05. Split с predcalc быстрее split без predcalc**
  - tag: `Аналитика: Делаем сплиттование с предрасчетом, засекаем временные параметры; должно быть быстрее.`
## Analytic02SplitApiContractFlowTest

- `validSplitShouldReturnBaseResponseContract` — **AN-SPLIT-01. Корректный split возвращает базовый контракт ответа**
  - tag: `Аналитика: Делаем корректный запрос на сплиттование с загруженной конфигурацией, проверяем корректность структуры ответа.`
- `unmatchedObjectShouldNotHaveMainAssignment` — **AN-SPLIT-02. Запрос с объектом без привязки возвращает объект с пустым результатом или без MAIN**
  - tag: `Аналитика: Ни один объект не привязался; результат сплиттования не должен содержать MAIN для объекта.`
- `invalidSplitRequestShouldReturnValidationError` — **AN-SPLIT-03. Некорректный split request возвращает ошибку валидации**
  - tag: `Аналитика: Делаем некорректный запрос на сплиттование. Должны получить ошибку.`

## Осталось TODO вне REST-only harness

- Отключение ALL через ConfigMap.
- Performance-сравнение predcalc.
- Варианты alt-markup/alt-rollback-values через управляемую ConfigMap.
- Комбинация filtering + alternative rollback через отдельный ConfigMap-профиль.
- Переключение min/max правил выбора layerPriority/id через ConfigMap.

## Дополнительно реализовано в patch manual-and-rest-extension

- `splitWithoutLoadedConfigShouldReturnNoConfigErrorManual` — **AN-CFG-08 / CFG-01. Manual: split без загруженного config после рестарта pod/чистого стенда**. Помечен `@ManualTest` и `@Disabled`, потому что требует изолированного состояния сервиса.
- `likeFamilyOperatorsShouldBeCoveredByRestMatrix` — **AN-RULES-06 / OP-14. LIKE-семейство операторов для STRING**: `like`, `not_like`, `like_any`, `not_like_any`.
- `singleGroupWithTwoAdjacentRangesShouldCoverInternalBoundary` — **AN-GROUP-06 / GRP-10. Одна группа с двумя соседними share-интервалами и проверкой внутренней границы**.
- `oneGroupWithSeveralMatchedConditionsShouldUseSmallestConditionId` — **AN-ALL-07. Одна группа с несколькими matched conditions выбирает минимальный conditionId**.
- `severalGroupsWithSeveralMatchedConditionsShouldUseSmallestConditionIdInSelectedGroup` — **AN-ALL-08. Несколько групп с несколькими matched conditions выбирают conditionId внутри выбранной по spreadValue группы**.

## Manual-tag policy

`@ManualTest` проставлен на сценарии, которые нельзя стабильно выполнять в обычном REST regression без внешнего управления состоянием стенда: Kafka harness, рестарт pod/чистый state, смена ConfigMap-профиля или performance harness.
