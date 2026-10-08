# Команды для терминала IntelliJ IDEA

Текущий стандарт проекта: Gradle 8.4, JDK 17. В IDEA выберите Gradle distribution:
Wrapper и Gradle JVM: JDK 17. [Инструкция перехода](GRADLE_8_4.md).

Для текущей поставки проекта используйте:

```powershell
.\gradlew.bat --version
.\gradlew.bat testClasses
.\gradlew.bat schedulerReadOnlyRegression -PschedulerTunnel=true
```

Ниже сохранены примеры старой внешней launch-обвязки. Её скриптов нет
в текущей поставке; они не требуются для wrapper-запуска.
Числа старых bypass-результатов не являются ожидаемыми результатами нового прогона.


Команды рассчитаны на PowerShell-терминал IDEA, открытый для проекта:

```powershell
Set-Location "C:\Work\IdeaProjects\integration-test"
```

## Применение архива с патчем

Архив нужно распаковать в корень проекта, а затем запустить cleanup-скрипт.
Cleanup удаляет старые копии файлов из `src/test`, потому что обычная распаковка
архива не удаляет файлы, которые были перенесены в новые директории.

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

Expand-Archive "<workspace>\output\integration-test-structure-refactor-files-20260806_221333.zip" -DestinationPath "." -Force
.\scripts\cleanup-structure-refactor.ps1
```

## Проверка проекта без реальных сервисов

Этот режим компилирует `main`, `test`, reporting/bypass tooling и запускает registration-only bypass.
REST-сервисы, Kafka и реальные брокеры для него не нужны.

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "<workspace>\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

.\launch-project.ps1 -Mode project -EnvName ift
```

Ожидаемый успешный итог:

```text
BUILD SUCCESSFUL
Report eligibility: discovered=589, eligible=506, excluded=83
Bypass tests generated: classes=120, methods=506
```

## Быстрая диагностика окружения

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "<workspace>\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

.\launch-project.ps1 -Mode check
```

## Только компиляция

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "<workspace>\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

.\launch-project.ps1 -Mode compile -EnvName ift
```

## Если Gradle 8.4 установлен отдельно

Вместо смены версии wrapper можно явно указать каталог установленного Gradle 8.4:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "<workspace>\lib"
$env:USE_LOCAL_LIBS = "true"
$gradleHome = Read-Host 'Абсолютный путь к каталогу установленного Gradle 8.4'

& (Join-Path $gradleHome 'bin/gradle.bat') testClasses generateReportEligibility bypassTests
```

## Корпоративный режим

В корпоративной сети локальные jar и внешний Gradle-кэш можно не задавать.
Так проект будет использовать штатный `gradlew.bat` и зависимости из Nexus.

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

Remove-Item Env:LOCAL_LIB_DIR -ErrorAction SilentlyContinue
Remove-Item Env:USE_LOCAL_LIBS -ErrorAction SilentlyContinue
Remove-Item Env:GRADLE_USER_HOME -ErrorAction SilentlyContinue

.\launch-project.ps1 -Mode project -EnvName ift
```

## Smoke/full режимы

Эти режимы уже могут ходить в реальные REST-сервисы, Kafka или хранилища. Для нашей текущей задачи их запускать не обязательно.

```powershell
$env:ENCRYPTION_PASSWORD = "<password>"
.\launch-project.ps1 -Mode smoke -EnvName ift -TestPattern "ru.sber.qa.splitter.EXPLAB_2892.*"
.\launch-project.ps1 -Mode full -EnvName ift
```
