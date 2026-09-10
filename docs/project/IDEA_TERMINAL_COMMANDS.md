# Команды для терминала IntelliJ IDEA

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

Expand-Archive "A:\Codex\Functional\output\integration-test-structure-refactor-files-20260806_221333.zip" -DestinationPath "." -Force
.\scripts\cleanup-structure-refactor.ps1
```

## Проверка проекта без реальных сервисов

Этот режим компилирует `main`, `test`, reporting/bypass tooling и запускает registration-only bypass.
REST-сервисы, Kafka и реальные брокеры для него не нужны.

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "A:\Codex\Functional\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = "A:\Codex\Functional\integration-test\.gradle"

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

$env:LOCAL_LIB_DIR = "A:\Codex\Functional\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = "A:\Codex\Functional\integration-test\.gradle"

.\launch-project.ps1 -Mode check
```

## Только компиляция

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "A:\Codex\Functional\lib"
$env:USE_LOCAL_LIBS = "true"
$env:GRADLE_USER_HOME = "A:\Codex\Functional\integration-test\.gradle"

.\launch-project.ps1 -Mode compile -EnvName ift
```

## Если Gradle 8.10 лежит в другом месте

Если в `GRADLE_USER_HOME` нет распакованного `gradle-8.10\bin\gradle.bat`, укажи путь к Gradle явно:

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
Set-Location "C:\Work\IdeaProjects\integration-test"

$env:LOCAL_LIB_DIR = "A:\Codex\Functional\lib"
$env:USE_LOCAL_LIBS = "true"
$gradleHome = "C:\Users\Dietrich\.gradle\wrapper\dists\gradle-8.10-bin\deqhafrv1ntovfmgh0nh3npr9\gradle-8.10"

.\launch-project.ps1 -Mode project -EnvName ift -GradleHome $gradleHome
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
