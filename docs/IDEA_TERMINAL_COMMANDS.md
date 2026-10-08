# Команды для терминала IntelliJ IDEA

Откройте терминал PowerShell в корне готового корпоративного проекта и используйте его Gradle Wrapper с JDK 17. Старые overlay-архивы и cleanup-скрипты для переноса структуры повторно не применяются.

## Настройки перед запуском

Среда выбирается только ключом `env` в `src/test/resources/test.properties`. Там же находятся REST-адреса и параметры сценариев. Kafka хранится в `kafka-consumers.properties` / `kafka-producers.properties`, БД — в `database.properties`, Ignite — в `ignite.properties`. Версионируемые файлы содержат ссылки на секреты, а значения — только игнорируемый `secure.local.override.properties`. Существующий override не заменяйте шаблоном.

Настройки Nexus и TestOps без секретов находятся в `gradle.properties`. Credentials и `allureToken` находятся в override. Аргументы JVM/Gradle и переменные окружения не переопределяют среду, подключения или секреты. Профиль `ift-dm` независим от IFT и требует отдельного заполнения.

Полный порядок: [CONFIGURATION.md](project/CONFIGURATION.md).

## Проверки без обращения к стендам

```powershell
.\gradlew.bat --version
.\gradlew.bat testClasses propertyLayoutTest --console=plain
.\gradlew.bat generateReportEligibility --console=plain
```

Компиляция использует корпоративные репозитории из проекта. Готовые локальные JAR, внешний developer-кэш и отсутствующие launch-скрипты не требуются. `propertyLayoutTest` проверяет конфигурацию локально; это не функциональный прогон стенда.

Техническая регистрация актуальных сценариев в Allure:

```powershell
.\gradlew.bat bypassTests --console=plain
```

## Проверка EXPLAB-2972 на выбранном стенде

Сначала заполните выбранный профиль и выполните предусловия из [плана EXPLAB-2972](services/experiments/EXPLAB-2972/README.md). Следующие команды обращаются к реальному стенду:

```powershell
.\gradlew.bat explab2972Test --console=plain
.\gradlew.bat explab2972LaunchPlanTest --console=plain
```

Управляемый набор с отказами инфраструктуры запускается только при выполнении его отдельного контракта и предусловий:

```powershell
.\gradlew.bat explab2972ManagedTest --console=plain
```

## Подготовка корпоративного Ignite runtime

Эти задачи разрешают корпоративные зависимости и компилируют helper, не обращаясь к стенду:

```powershell
.\gradlew.bat -I tools/ignite-client/corporate-client.init.gradle prepareCorporateIgniteClient
.\gradlew.bat -I tools/data-operator-explab-2974/corporate-client.init.gradle prepareExplab2974CorporateClient
```

Результаты находятся соответственно в `build/corporate-ignite-runtimes/ignite-client/<runtime-sha256>` и `build/corporate-ignite-runtimes/data-operator-explab-2974/<runtime-sha256>`. Запишите подходящий путь в `ignite.<env>.runtime.directory` файла `ignite.properties`. Для data-operator нужен второй комплект. Подробнее: [IGNITE_CLIENT.md](testing/IGNITE_CLIENT.md).

## Результаты и очистка

Allure, regression, TestOps и фикстуры сохраняются в `build`. Исторические корпоративные JSON находятся в `build/regression-fixtures`. Перед очисткой завершите восстановление данных стенда; при активных lease сохраните во внутреннем хранилище вне проекта полный каталог запуска и точный runtime с lock, библиотеками и helper-исходниками. Затем можно выполнить:

```powershell
.\gradlew.bat clean
```

`clean` удаляет весь `build`, включая подготовленные Ignite runtime. Для следующего запуска подготовьте runtime заново и проверьте выбранный путь. Properties и `secure.local.override.properties` сохраняются.
