# Политика тегов и состава Allure-отчета

## Канонические теги

`RequiredAllureLabelsExtension` удаляет из результата все произвольные JUnit/Allure tags и формирует только следующие категории:

- номер задачи: `EXPLAB-####` или `LG-####`;
- сервис: `splitter-service`, `experiment-service`, `configuration-service`, `dictionaries-service`, `data-operator-service`, `message-service`;
- регресс: `regress`;
- критический регресс: `critical-regress` вместе с `regress`;
- тип выполнения: ровно один тег `manual` или `automated`.

Номер задачи извлекается из package/class/display name. Сервис определяется по package теста. Для временно расположенного пакета `splitter.EXPLAB_2729` сервис принудительно определяется как `data-operator-service`.

## Семантические аннотации

- `@CriticalRegression` — критический регресс; автоматически добавляет также обычный регресс.
- `@Regression` — обычный регресс.
- `@ManualTest` — тест требует ручной подготовки/запуска. Без этой аннотации тест считается автоматизированным.

Произвольные `@Tag("flow")`, `@Tag("smoke")`, `@Tag("requires-configmap")`, `@Tag("tests-v9")` и аналогичные технические теги удалены. Gradle-задача `auditReportingTags` блокирует повторное добавление `@Tag(...)` и прямых `Allure.label("tag", ...)` в тестовый код.

## Какие тесты не попадают в отчет

Перед `test` и `bypassTests` выполняется `generateReportEligibility`. Сканер исключает тест до JUnit discovery, если:

1. тест или класс помечен `@Disabled`;
2. в тесте нет assertion/matcher/expected-status проверки и нет вызова локального helper с такой проверкой;
3. тест совпадает с правилом в `config/reporting/outdated-tests.properties`.

Результат сканирования сохраняется в:

- `build/report-eligibility/report-eligibility.md`;
- `build/report-eligibility/excluded-tests.txt`;
- `build/report-eligibility/eligible-tests.txt`.

Таким образом исключенные тесты не формируют ни passed, ни skipped, ни broken записи Allure.

Если исключенный тест запущен напрямую из IDE или точечным Gradle-фильтром, `RequiredAllureLabelsExtension`
дополнительно удаляет его `*-result.json` из `build/allure-results` при завершении JVM. Поэтому такие
сценарии не участвуют в Allure-статистике даже при фактическом выполнении.

## Bypass

`bypassTests` генерируется только для тестов из `eligible-tests.txt`:

- исключенные/устаревшие/не содержащие проверок тесты не создаются;
- технический тег `bypass` удален;
- мусорные tags и технические custom labels не копируются;
- сохраняются исходные package/class/method/display name;
- сохраняются `@CriticalRegression`, `@Regression`, `@ManualTest`;
- канонические task/service/regression/manual-automated tags добавляются тем же listener, что и при функциональном запуске.
