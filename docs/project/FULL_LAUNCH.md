# Полный локальный запуск

Проект - Java 17 Gradle-набор автотестов для ExpLab A/B testing services.

## 1. Локальная настройка

Скопируйте `launch.local.example.ps1` в `launch.local.ps1` и задайте:

```powershell
$env:ENCRYPTION_PASSWORD = "<password-for-ENC-values>"
```

При необходимости Nexus-доступы можно задать там же:

```powershell
$env:NEXUS_TOKEN_NAME = "<nexus-login>"
$env:NEXUS_TOKEN_PASSWORD = "<nexus-password>"
```

Скрипт также принимает Nexus-доступы из `~/.gradle/gradle.properties`
как `tokenName` / `tokenPassword`, либо из уже существующих legacy-полей
проекта `nexusUserSigma` / `nexusPasswordSigma`.

## 2. Сетевые условия

Gradle wrapper и зависимости скачиваются из внутреннего Nexus:

```text
nexus-ci.delta.sbrf.ru
```

Перед первым запуском нужна корпоративная сеть/VPN. Без нее `gradlew.bat`
не сможет скачать Gradle 7.3.3 и зависимости проекта.

Если Gradle 7.3.3 недоступен, bootstrap автоматически пробует локальный
Gradle 8.10 из `%USERPROFILE%\.gradle\wrapper\dists`. Это позволяет стартовать
сам build, но внутренние зависимости `ru.sber.qa.platform-v-at-framework`
все равно требуют доступный Nexus или локальный Gradle cache.

Для запуска без корпоративного Nexus локальные jar-файлы подключаются только
явно, чтобы не менять поведение проекта в корпоративной сети. Включить режим
можно одним из способов:

```powershell
$env:USE_LOCAL_LIBS = "true"
$env:LOCAL_LIB_DIR = "A:\Codex\Functional\lib"
```

или:

```powershell
.\launch-project.ps1 -Mode project -EnvName ift -GradleArgs "-PuseLocalLibs=true", "-PlocalLibDir=A:\Codex\Functional\lib"
```

Папка `lib` может быть копией Gradle cache `modules-2/files-2.1`: Gradle
подключает все `**/*.jar` из нее напрямую. Из-за отсутствия `.pom`-метаданных
часть транзитивных публичных зависимостей явно объявлена в `build.gradle.kts`.

Без этих параметров сборка использует штатные зависимости из Nexus так же,
как исходный корпоративный проект.

## 3. Команды

Из корня проекта:

```powershell
.\launch-project.ps1 -Mode check
.\launch-project.ps1 -Mode compile -EnvName ift
.\launch-project.ps1 -Mode project -EnvName ift
.\launch-project.ps1 -Mode smoke -EnvName ift -TestPattern "ru.sber.qa.splitter.EXPLAB_2892.*"
.\launch-project.ps1 -Mode full -EnvName ift
.\launch-project.ps1 -Mode report
```

Дополнительно:

```powershell
.\launch-project.ps1 -Mode bypass -EnvName ift
.\launch-project.ps1 -Mode smoke -EnvName dev -TestPattern "ru.sber.qa.experiments.v2.*"
```

Дополнительные Gradle-аргументы передаются через `-GradleArgs`:

```powershell
.\launch-project.ps1 -Mode full -EnvName ift -GradleArgs "--info"
```

### Сравнение mapper и Java 8/vintage splitter

Один и тот же набор splitter-тестов можно прогнать против текущего mapper
endpoint и mapper-совместимого Java 8/vintage endpoint без изменения тестовых
классов. По умолчанию используется `/api/v1/splitter/mapper`; для второго
прогона задайте другой prefix:

```powershell
.\gradlew.bat cleanTest test --tests "ru.sber.qa.splitter.*" `
  -Dsplitter.endpoint.prefix=/api/v1/splitter/mapper

Copy-Item build\test-results\test build\splitter-comparison\mapper -Recurse -Force

.\gradlew.bat cleanTest test --tests "ru.sber.qa.splitter.*" `
  -Dsplitter.endpoint.prefix=<фактический-prefix-java8-сервиса>

Copy-Item build\test-results\test build\splitter-comparison\vintage -Recurse -Force
```

Если у Java 8 сервиса пути не сводятся к одному prefix, переопределите
отдельные методы: `-Dsplitter.endpoint.config`,
`-Dsplitter.endpoint.split`, `-Dsplitter.endpoint.precalculate`,
`-Dsplitter.endpoint.version`.

## 4. Режимы

- `check`: проверяет Java 17, DNS Nexus и доступность Gradle wrapper.
- `compile`: запускает `clean testClasses generateReportEligibility`.
- `project`: запускает `clean testClasses generateReportEligibility bypassTests`;
  это основной режим проверки проекта без реальных REST-сервисов и Kafka.
- `smoke`: запускает `test --tests <pattern>`.
- `full`: запускает `clean test`.
- `report`: запускает `allureReport`.
- `bypass`: запускает `bypassTests` для registration-only результатов TestOps.

## 5. Результаты

- Test results: `build/test-results/test`
- Allure raw results: `build/allure-results`
- Allure report: `build/reports/allure-report/allureReport`
- Report eligibility scan: `build/report-eligibility`
