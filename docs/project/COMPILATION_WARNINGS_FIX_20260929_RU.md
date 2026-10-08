# Исправление 18 предупреждений компиляции

Дата: 2026-09-29.

В 10 тестовых классах заменены 18 вызовов
JsonMatchers.evaluateJsonPathExpression на
JsonMatchers.evaluateGroovyPathExpression.

В установленной библиотеке platform-v-at-framework 1.10.3-beta оба метода
создают GroovyPathExpressionEvaluateMatcher с тем же аргументом.
Выражения containsKey('params') и content!=null оставлены без изменений.
Проверки ответов не ослаблены, тесты не отключены, подавление предупреждений
не добавлялось.

На временной копии выполнено:
compileWithoutTests --rerun-tasks --offline --no-daemon --console=plain

Результат: BUILD SUCCESSFUL, 7 actionable tasks: 7 executed.
18 предупреждений removal больше нет.
Остались общие сообщения javac об использовании другого deprecated API
и уведомление об устаревших возможностях Gradle. Они не скрывались.
Тесты, подключения к стенду/БД и TestOps не запускались.

Резервная копия изменённых файлов:
<workspace>/BACK/05_outputs_results/project-backups/integration_test_ready_20260929/json-matcher-warnings-20260929

Журнал: gradle-task-audit-20260929/fix-removal-warnings-compile.log

Изменённые файлы:
- src/test/java/ru/sber/qa/controllers/refBookController/ChangeSplitNegativeTest.java
- src/test/java/ru/sber/qa/controllers/refBookController/CreateSplitNegativeTest.java
- src/test/java/ru/sber/qa/controllers/refBookController/DeleteSplitNegativeTest.java
- src/test/java/ru/sber/qa/controllers/refBookController/GetSplitsTest.java
- src/test/java/ru/sber/qa/experiments/EXPLAB_2539/LayerV2GetByIdNegativeFlowTest.java
- src/test/java/ru/sber/qa/experiments/layers/ChangeLayerNegativeTest.java
- src/test/java/ru/sber/qa/experiments/layers/CreateLayerNegativeTest.java
- src/test/java/ru/sber/qa/experiments/layers/DeleteLayerNegativeTest.java
- src/test/java/ru/sber/qa/experiments/layers/GetLayerByIdNegativeTest.java
- src/test/java/ru/sber/qa/experiments/layers/GetLayerRegistryTest.java
