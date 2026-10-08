# Splitter Extension

Расширенная регрессия находится в src/test/java/ru/sber/qa/splitter/extension.
Она отделена от критериев закрытия EXPLAB-2690. Все 125 сценариев сохранены.

| Класс | Число |
|---|---:|
| MapperDocumentMatrixFlowTest | 26 |
| MapperDocumentMatrixWithoutMainDeniedFlowTest | 26 |
| ReactionsDocumentMatrixFlowTest | 28 |
| ReactionsDocumentMatrixWithoutMainDeniedFlowTest | 28 |
| MapperKafkaReportContractFlowTest | 4 |
| ReactionsKafkaReportContractFlowTest | 4 |
| ReactionsLayerPriorityFlowTest | 4 |
| ReactionsLayerPriorityWithoutMainDeniedFlowTest | 4 |
| ReactionsMultipleFinalExperimentsFlowTest | 1 |
| Всего | 125 |

Общие фикстуры: AbstractSplitterDocumentMatrixFlowTest, SplitterDocumentFixtures,
SplitterDocumentOracle. Локальные проверки: src/workloadChecks/java/ru/sber/qa/splitter/extension/
SplitterDocumentContractChecks.java. База DTO/flow и управление стендом переиспользуются,
без копии инфраструктуры Fabric8.

```powershell
.\gradlew.bat testClasses workloadContractChecks
.\gradlew.bat splitterExtensionRegression --dry-run
.\gradlew.bat splitterExtensionRegression
```

Задача находится в группе regression. Это полноценный управляемый запуск:
проверяет готовность, применяет профиль и восстанавливает ConfigMap/поды. Предусловия
и параметры такие же, как у core (env, namespace, права, mTLS, Kafka).
Префиксы explab2690.* / explab2690.reactions.* пока сохранены для совместимости.

Extension не входит в explab2690Coverage, обычный Gradle test и широкие REST/Kafka-задачи.
Его нужно запускать явно. Отчёты находятся в отдельных каталогах splitterExtensionRegression.
Нельзя одновременно запускать независимые Gradle-процессы на одном стенде.
Переименование изменяет Allure fullName/historyId; старые и новые результаты не объединять
как один прогон. Байпасс-регистрация метаданных сохраняет видимость extension, но не запускает его.

116 сценариев ожидают Kafka-отчёт; оставшиеся 9 проверяют слои через REST.
Дополнительные требования и ограничения: REQUIREMENTS_MATRIX.md, KNOWN_DEVIATIONS.md.
