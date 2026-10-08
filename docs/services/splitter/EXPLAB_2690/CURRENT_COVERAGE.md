# EXPLAB-2690: границы основного набора, 03.10.2026

## Состав

| Основные классы (включая Denied-варианты) | Сценарии |
|---|---:|
| SplitterMapperWorkedGroup2690FlowTest | 14 |
| SplitterMapperAlternative2690FlowTest | 2 |
| SplitterMapperNoMain2690FlowTest | 8 |
| SplitterMapperMixedObjects2690FlowTest | 4 |
| SplitterReactionsGroups2690FlowTest | 16 |
| SplitterReactionsNoAlternative2690FlowTest | 4 |
| Всего | 48 |

Здесь сохранены исходные проверки no-MAIN, expGroup, finalExpGroup, conditionId,
параметров связанной группы и независимой обработки объектов. Допустим no-MAIN
objectFlags absent/null/[] либо единственный строковый filtered=false.

Ядро не проверяет resultDt, алгоритм фильтрации MAIN, выбор нескольких MAIN слоя
или спорное сохранение Kafka ALL при allow=false. Эти проверки сохранены в extension,
а не удалены или изменены для получения зелёного результата.

## Запуск и стенд

- explab2690Coverage: 48 сценариев, MAPPER 28 + REACTIONS 20.
- explab2690MapperCoverage: 28.
- explab2690ReactionsCoverage: 20.
- splitterExtensionRegression: отдельные 125, группа regression.

Реализация задач и общего управляемого запуска: gradle/build-logic/tickets.gradle.kts.
Основной пакет и extension не пересекаются по Java-классам. Extension исключён из
обычного Gradle test и широких splitterRestRegression / splitterKafkaRegression;
для него нужен явный splitterExtensionRegression. При --tests результат может быть подмножеством.

Окружение читается из src/test/resources/test.properties (для DEV env=dev).
Сохраняются проверки готовности, подтверждение namespace, Fabric8, leases, исходные
ConfigMap и восстановление после падений. Фазы: MAPPER allow=true/false, затем REACTIONS
allow=true/false. Новых фаз и переключений внутри класса нет. Число рестартов зависит
от исходного состояния; ранее для полного набора было 5, это не гарантия нового прогона.
Раздельный запуск core и extension создаёт две независимые сессии подготовки/восстановления.

Настройки workload, service, rules ConfigMap и application env используют прежние
префиксы explab2690.* / explab2690.reactions.* для совместимости обоих наборов.
Общая инфраструктура стенда не дублируется. Не отключать application-flags.enabled,
если требуется выполнение Denied-классов.

Отчёты разделены по имени задачи:
build/test-results/<task>, build/reports/tests/<task>, build/allure-results/<task>.
Технические проверки остаются только в build/contract-checks, не для TestOps.

## Статус доказательств

Последний приложенный DEV-прогон выполнен до разделения: 169 тестов, 105 passed / 64 failed.
Это исторический общий результат, не результат нового core-прогона.
После time-flags суммарно объявлено 173 (48 + 125); DEV этой версии не запускался.
Компиляция, локальные контрактные проверки и подсчёт фикстур не заменяют live-прогон.
Критерий закрытия заявки: подтверждение четырёх требований REQUIREMENTS_MATRIX.md,
а не процент от прежней расширенной документной матрицы.
