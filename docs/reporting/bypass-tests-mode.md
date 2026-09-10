# Bypass tests mode

`bypassTests` — технический Gradle step для регистрации/загрузки автотестов в TMS/TestOps.

Режим не запускает реальные функциональные проверки из `src/test/java`. Перед запуском он генерирует Java stub-тесты с теми же `package`, `class` и `method`, а затем выполняет их как отдельный JUnit5 source set.

## Запуск

```bash
./gradlew clean bypassTests
```

## Результаты Allure

```text
build/allure-results-bypass
```

## Локальный отчет

```bash
./allure/bin/allure generate build/allure-results-bypass -o build/reports/allure-bypass --clean
```

## Наследование разметки

`bypassTests` переносит разметку с оригинальных классов и методов:

- JUnit `@Tag` с класса и метода;
- `@DisplayName` с метода;
- Allure metadata: `@Owner`, `@Epic`, `@Feature`, `@Story`, `@Severity`, `@Link`, `@Issue`, `@TmsLink`, `@AllureId`, `@Label`, `@Description`, suite labels;
- локальный маркер `@CriticalRegression`.

Для обязательных TestOps labels используется bypass-local Allure listener:

```text
src/bypassTests/java/ru/sber/qa/allure/RequiredAllureLabelsExtension.java
```

Он добавляет те же обязательные labels, что и обычный тестовый listener:

- `system=CI07963639`
- `layer=api`
- `team=EXPLAB`
- `appType=backend`
- `functionalArea=<по package>`
- `testStage=<из system/env/test.properties>`
- `criticalRegress=true` и `regress=true`, если оригинальный тест был размечен `@CriticalRegression`

## Технические признаки bypass-режима

Каждый сгенерированный тест дополнительно содержит labels:

- `bypassTests=true`
- `registrationOnly=true`
- `functionalExecution=false`
- `source=generated-bypass-test`
- `originalFullName=<package>.<class>#<method>`

Обычный функциональный прогон остается через стандартный step:

```bash
./gradlew clean test
```


## Allure results

Задача `bypassTests` генерирует Allure results в отдельную директорию:

```text
build/allure-results-bypass
```

Перед запуском директория очищается отдельной задачей `cleanBypassAllureResults`, после выполнения тестов туда копируется `allure/categories.json`. Повторный запуск из Gradle-панели не будет пропущен как `UP-TO-DATE`, поэтому `allure-results-bypass` пересоздается при каждом запуске `bypassTests`.
