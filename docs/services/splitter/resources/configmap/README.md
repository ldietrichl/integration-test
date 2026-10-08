# Splitter ConfigMap profiles for analytictests

Файлы в этой папке фиксируют ConfigMap-контракт, под который написаны REST-only analytic tests.

Они не применяются к стенду автоматически. Их назначение:

1. Документировать предусловие автотеста.
2. Прикладываться в Allure как доказательная база.
3. Использоваться в CI/стендовом профиле, если появится механизм применения ConfigMap перед прогоном.

Основной профиль:

- `mapper-current.yml` — текущий ожидаемый профиль MAPPER.

Дополнительные профили:

- `mapper-main-priority.yml` — правила выбора MAIN по actionType.
- `mapper-filter-2-4.yml` — фильтрация actionType 2 и 4.
- `mapper-alternative-default.yml` — current alternative markup / rollback values with traffic-based-alternative=true.
- `mapper-layer-priority.yml` — layer priority / tie-break assumptions.
