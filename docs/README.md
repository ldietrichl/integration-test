# Документация проекта

Основные разделы:

- `project/` - локальный запуск, IDE/terminal setup и git workflow.
- `reporting/` - правила Allure/TestOps, bypass mode и политика тегов.
- `services/` - runbook'и и сценарии по сервисам.
- `references/` - указатели внешней локальной документации и больших справочников.
- `images/` - изображения для документации.

Быстрые ссылки:

- [Конфигурация: properties, профили, секреты и generated-артефакты](project/CONFIGURATION.md)
- [Полный локальный запуск](project/FULL_LAUNCH.md)
- [Команды для терминала IDEA](project/IDEA_TERMINAL_COMMANDS.md)
- [Git workflow](project/GIT_WORKFLOW.md)
- [Политика тегов и Allure-отчета](reporting/TAGGING_AND_REPORTING_POLICY.md)
- [Bypass tests mode](reporting/bypass-tests-mode.md)
- [Полный табличный план регресса data-operator](services/data-operator/regression/REGRESSION_PLAN.md)
- [Матрица требований data-operator: покрытие по документации и пробелы](services/data-operator/regression/REQUIREMENTS_TRACEABILITY.md)
- [Стартовый тест-план EXPLAB-2974: только POST /splitting-objects-links v7](services/data-operator/EXPLAB_2974/TEST_PLAN_ENDPOINT_V7.md)
- [Реализация EXPLAB-2974: flow/rest, подготовка данных и результаты локальной проверки](services/data-operator/EXPLAB_2974/IMPLEMENTATION.md)
- [Локальный прогон EXPLAB-2974 и анализ лога сервиса](services/data-operator/EXPLAB_2974/LOCAL_RUN_20260907.md)
- [Архитектура тестов и локального стенда EXPLAB-2974](services/data-operator/EXPLAB_2974/HARNESS_ARCHITECTURE_20260907.md)
- [Причины падений EXPLAB-2974 и влияние соседних сервисов](services/data-operator/EXPLAB_2974/FAILURE_CAUSES_AND_DEPENDENCIES_20260907.md)
- [Заглушки зависимостей EXPLAB-2974: D1, REST-загрузка и очистка](services/data-operator/EXPLAB_2974/DEPENDENCY_STUBS_20260907.md)
- [Полный запуск 104 реализованных проверок EXPLAB-2974](services/data-operator/EXPLAB_2974/FULL_RUN_20260907.md)
- [Повтор EXPLAB-2974 после переноса Docker на A](services/data-operator/EXPLAB_2974/REPEAT_RUN_20260907.md)
- [EXPLAB-2974: 70 функциональных вариантов и полный локальный прогон 173 проверок](services/data-operator/EXPLAB_2974/FUNCTIONAL_RUN_20260907.md)
- [Корпоративный запуск функциональных EXPLAB-2974 с REST-данными и очисткой](services/data-operator/EXPLAB_2974/CORPORATE_FUNCTIONAL_REST.md)
- [Расширенный тест-план EXPLAB-2974: связи объектов сплиттования](services/data-operator/EXPLAB_2974/TEST_PLAN.md)
- [Полная документация ФП ExpLab: расположение и поиск](references/EXPLAB_FP.md)
- [Целевая архитектура Сплиттера: сравнение вариантов, сроки и тестирование](services/splitter/architecture/VARIANTS_REVIEW_20260913.md)
- [Исходные документы по архитектуре Сплиттера](references/SPLITTER_ARCHITECTURE.md)
- [Jira-план EXPLAB-2974: 174 результата и группы багов ИФТ](services/data-operator/EXPLAB_2974/jira-test-plan/README.md)
