# Корпоративный профиль scheduler: конфигурационный патч v7

## Назначение и ограничения

Патч применяется поверх текущего integration-test с ранее установленной инфраструктурой scheduler и Gradle 8.4. Это не самостоятельный проект и не пакет миграции старой версии тестов.

По последней выгрузке обнаружены две независимые проблемы:
- Read-only запуск не включал туннель и получал Connection refused на фиксированном loopback URI.
- Инфраструктурный запуск включал туннель, но OpenShift отклонял аутентификацию с HTTP 401 / Unauthorized. Истечение срока токена возможно, но не доказано.

В scheduler.properties DEV-туннель теперь включён по умолчанию. В kubernetes-tunnels.properties заданы наблюдавшиеся параметры DEV, транспорт oc, service-port=8080, автоматически выбираемый локальный порт и ограниченный сбор логов в Allure. Известные опечатки в Gradle-параметре schedulerTunnel приводят к понятной ошибке вместо молчаливого запуска без туннеля.

Патч НЕ содержит токены, kubeconfig, сертификаты или пароли. Он не исправляет отклонённый токен: его необходимо обновить на ВАРМ.

## Приоритет настроек

Существующий src/test/resources/test.properties не заменяется. Его значения имеют приоритет над scheduler.properties и kubernetes-tunnels.properties.

Для DEV оставьте ровно один селектор:
```properties
env=dev
```

Новый параметр уже находится в scheduler.properties:
```properties
scheduler.dev.tunnel.enabled=true
```

Если в test.properties есть scheduler.dev.tunnel.enabled=false или scheduler.tunnel.enabled=false, этот явный override перекроет новый default. Измените только соответствующее значение либо удалите устаревшее переопределение. Аналогично уберите ненужный -Dscheduler.tunnel.enabled=false или -PschedulerTunnel=false из конфигурации запуска IDEA/Gradle.

Не добавляйте дубликаты ключей. Сохраните действующие параметры других сервисов, database.properties, secure.local.override.properties и остальные секретные локальные файлы.

В режиме туннеля адрес REST формируется из созданного туннеля. Старые rest.dev.scheduler.base-uri=http://127.0.0.1:28090 и аналогичные URI для прямого режима не требуют ручного слушателя. Не запускайте отдельный port-forward для этих тестов.

Внешние вызовы других сервисов и подключение БД продолжают использовать настройки проекта. Этот патч не отключает их TLS и не подменяет DEV на IFT.

## Действующий DEV-профиль

```properties
kubernetes.oc.executable=C:/Work/utils/oc.exe
kubernetes.transport=oc
kubernetes.dev.context=ci07963639-dev-terra000003-abtm-back/api-dev-terra000003-ids-ocp-delta-sbrf-ru:6443/23209772
kubernetes.dev.api-server=https://api.dev-terra000003-ids.ocp.delta.sbrf.ru:6443
kubernetes.dev.namespace=ci07963639-dev-terra000003-abtm-back
kubernetes.dev.kubeconfig=C:/Users/23209772/.kube/config.scheduler-dev-ca-20260914-215807-fb5f650e1843498d8e808df2d837c1bf
kubernetes.service=scheduler-service
kubernetes.service-port=8080
kubernetes.local-port=0
kubernetes.dictionary.service=dictionary-service
kubernetes.dictionary.service-port=8080
kubernetes.logs.enabled=true
kubernetes.logs.container=scheduler-service
```

Эти строки уже включены в kubernetes-tunnels.properties; повторять весь блок в test.properties не нужно. Если там существуют переопределения, сохраните только актуальные. Путь kubeconfig относится к пользователю 23209772 на ВАРМ; для другого пользователя задайте его собственный подтверждённый профиль, а не чужие credentials.

scheduler.infrastructure.enabled=false в defaults означает opt-in для диагностического класса. Специальная Gradle-задача сама включает диагностику; это не выключает обычный DEV-туннель.

## Обновление отклонённого OpenShift-токена

Получите новый токен через утверждённый корпоративный способ входа в нужный кластер. Не пересылайте токен в чат, не помещайте его в properties, отчёты или архив.

В PowerShell на ВАРМ:
```powershell
Set-Location 'C:/Work/IdeaProjects/integration-test'

$cfg = 'C:/Users/23209772/.kube/config.scheduler-dev-ca-20260914-215807-fb5f650e1843498d8e808df2d837c1bf'
$ctx = 'ci07963639-dev-terra000003-abtm-back/api-dev-terra000003-ids-ocp-delta-sbrf-ru:6443/23209772'

& '.\tools\scheduler\Update-SchedulerOpenShiftToken.ps1' -Kubeconfig $cfg -Context $ctx
```

Введите только свежий токен в скрытом запросе. Скрипт сначала проверит API, выбранный context, наличие CA и личность через whoami с новым токеном. При успешной аутентификации он обновит только выбранную запись пользователя именно в указанном kubeconfig. Если несколько context используют ту же запись пользователя, новый токен применяется и к ним.

CA, cluster, context и namespace не перенастраиваются. Копии kubeconfig не создаются; Set-Acl и изменение владельца не используются. Обновление токена не является восстановлением или повторным одобрением CA.

[Явный --kubeconfig выбирает конкретный файл](https://kubernetes.io/docs/concepts/configuration/organize-cluster-access-kubeconfig/), а [config set-credentials обновляет запись пользователя](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_config/kubectl_config_set-credentials/). Обычный oc login без выбора файла может обновить другой профиль и не устранить 401 в тестах.

Токен не вводится литералом в команду PowerShell, но передаётся нативному oc как аргумент процесса. Не используйте трассировку команд/процессов; учитывайте корпоративную политику мониторинга. Если такой способ запрещён, используйте утверждённый механизм обновления credentials. Не обходите политику исполнения скриптов; при блокировке запросите разрешённую подпись/процедуру.

После успешного обновления можно отдельно проверить выбранный профиль:
```powershell
& 'C:/Work/utils/oc.exe' --kubeconfig $cfg --context $ctx --request-timeout=15s whoami
```

Ожидаемая личность этого профиля: 23209772. Не выводите oc whoami -t или config view --raw в журнал.

## CA и TLS

В предыдущей диагностике явная CA-цепочка Fabric8 присутствовала, а запросы доходили до HTTP 401. Сам по себе defaultAcceptedIssuerCount=0 не доказывает текущую ошибку TLS для клиента с явной CA.

Сохраняйте ранее подтверждённую CA в выбранном kubeconfig. Не включайте insecure-skip-tls-verify, trust-all или отключение проверки имени. HTTP между тестом и loopback-туннелем не отменяет HTTPS-проверку OpenShift API. Приложению scheduler не передаётся OpenShift-токен как его собственный REST-токен.

## Gradle и порядок запуска

Дистрибутив Gradle 8.4 загружается Wrapper из подтверждённого Nexus URL:
```text
https://nexus-ci.delta.sbrf.ru/repository/raw-sberosc-cache/distributions/gradle-8.4-bin.zip
```

В проекте остаются только загрузчик Wrapper и конфигурация. Дистрибутив хранится в GRADLE_USER_HOME/wrapper/dists, по умолчанию в пользовательском .gradle вне проекта. Не удаляйте gradle-wrapper.jar или gradlew.bat. URL и SHA-256 заданы в gradle/wrapper/gradle-wrapper.properties; задачи остаются в build.gradle.kts. В settings.gradle.kts сохраняется контроль версии. Пароли Nexus в архив не добавляются.

Используйте JDK 17 и Wrapper, а не произвольный gradle из PATH. Для русскоязычной консоли перед командами:
```powershell
chcp 1251 > $null
$enc = [System.Text.Encoding]::GetEncoding(1251)
[Console]::InputEncoding = $enc
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc
```

Сначала сборка без выполнения тестов:
```powershell
.\gradlew.bat compileWithoutTests --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Сборка без тестов завершилась ошибкой' }
```

Затем отдельная проверка туннеля и REST:
```powershell
.\gradlew.bat schedulerInfrastructureDiagnostics --tests '*SchedulerInfrastructureFlowTest.inf010' --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Диагностика туннеля не пройдена; сохраните артефакты этого запуска' }
```

После её успеха:
```powershell
.\gradlew.bat schedulerReadOnlyRegression --console=plain --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Read-only регресс не пройден; сохраните артефакты этого запуска' }
```

Параметр -PschedulerTunnel=true теперь не обязателен. При его использовании набирайте schedulerTunnel латиницей. Прямой запуск пакета в IDEA использует тот же env и defaults при рабочем каталоге корня проекта.

Полная инфраструктурная диагностика запускается той же задачей без --tests. Полный изменяющий регресс не включайте автоматически: ранее DEV был с fixtures.isolated=false и jobs.paused=false. Подготовка изолированных данных и согласование вмешательства в jobs остаются отдельными условиями.

В Allure используйте артефакты именно нового запуска. При успешном выборе pod и доступном pods/log инфраструктура прикладывает ограниченные логи. Если остановка произошла до выбора pod/запроса к сервису, NO_TARGET_NO_SERVICE_REQUEST объясняет отсутствие логов, а не подтверждает успешное получение логов.

## IFT

Фактические context, API, namespace и kubeconfig для IFT не предоставлены. Defaults оставлены пустыми, scheduler.ift.tunnel.enabled=false. Существующие подтверждённые test.properties overrides не удаляются. Включать IFT следует только после заполнения его собственного профиля и проверки доступа; DEV-значения использовать вместо него нельзя.

## Статус подготовки

В этой задаче подготовлены заменяемые файлы. Новый патч не компилировался, сценарии и токен-скрипт не запускались, корпоративный API не вызывался. Успех сборки и регресса необходимо установить на ВАРМ после установки и обновления аутентификации.
