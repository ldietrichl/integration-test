# Подтвержденный IFT mTLS-профиль

Параметры внесены в stand.properties и test.properties подготовленного проекта.
Активное env=dev сохранено: добавление IFT-профиля не является разрешением
запускать полный регресс или изменять общий IFT-стенд.

## Подтверждено

- API OpenShift: https://api.ift-efs1-ds.delta.sbrf.ru:6443
- namespace: ci07963639-eift-efs1-ds-abtm-back
- REST-шлюз: https://ingress-v2.ci07963639-eift-efs1-ds-abtm-back.apps.ift-efs1-ds.delta.sbrf.ru
- Клиент: CI02047903-IFT-EXPLAB
- SHA256 клиента: 8CED9CFC0666C1F7921807A99516A1E53206A0B303548C2C0385EB233F4ACFC3
- Срок сертификата: до 2027-08-19T20:30:13Z
- Корпоративный P12: C:/Work/private/openshift-service-access/ift-client-profile/ift-client-20260930-042857-1445ee10/private/client-ift.p12
- Проверка пользователем: HEAD /, HTTP=404, TLS_VERIFY=0, curlExit=0.
- P12 создан и проверен экспортёром; пароль пользователем задан локально.

HTTP 404 относится к корню шлюза, не подтверждает работу конкретного API,
прикладного пользователя, прав, MY_TASKS, БД, Kafka или Ignite.

## Что перенести

Из этого проекта в корпоративную копию:
- src/test/resources/stand.properties
- src/test/resources/test.properties
- config/certificates/ift/ift-ca-bundle.pem
- config/certificates/ift/ift-truststore.p12

До замены сделать резервную копию текущих properties и имеющейся папки
config/certificates/ift. Не заменять весь secure.local.override.properties
файлом из другой среды. Существующий приватный P12 остается на корпоративной машине.

Пример резервной копии текущих properties в корпоративном проекте:

~~~powershell
$project = 'C:\Work\IdeaProjects\integration-test'
$backup = Join-Path $project ('.patch-backups\ift-mtls-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup -ErrorAction Stop | Out-Null
Copy-Item -LiteralPath (Join-Path $project 'src\test\resources\stand.properties') -Destination $backup
Copy-Item -LiteralPath (Join-Path $project 'src\test\resources\test.properties') -Destination $backup
$caFolder = Join-Path $project 'config\certificates\ift'
if (Test-Path -LiteralPath $caFolder) {
    Copy-Item -LiteralPath $caFolder -Destination (Join-Path $backup 'ift-certificates') -Recurse
}
~~~

## Единственное недостающее значение для mTLS

В локальный игнорируемый secure.local.override.properties добавить отдельный ключ:
SECURE_IFT_MTLS_CLIENT_PKCS12_PASSWORD

Значение: НОВЫЙ пароль, введенный при экспорте client-ift.p12.
Он не известен ассистенту и не хранится в отчете. Предпочтителен ENC(...)
через существующий механизм шифрования проекта с текущим encryption.password.
Шаблон ключа: config/examples/ift-mtls-secret.example.properties.
Шаблон не подгружается автоматически и не содержит действующего пароля.
Не менять DEV-ключ SECURE_MTLS_CLIENT_PKCS12_PASSWORD.

При наличии локальных override для stand.ift.* они имеют приоритет;
устаревшие значения надо согласованно обновить, не удаляя другие настройки.

## Truststore

config/certificates/ift/ift-truststore.p12 содержит только публичные CA:
- SberCA Test Ext G2: E894BE9185C0D478A889CAB0A6E7F73C4C441957A05F6C36FB44F1AB0134F0B5
- SberCA Test Root Ext: 7F5E87410FA03EF4B747E947F53B59A29ABDB9920DD2D0D74D4D56C617785EB9

Пароль public-ift-ca-only опубликован намеренно: этот truststore не содержит
приватного ключа или секрета клиента. Это НЕ пароль client-ift.p12.
PEM сохранен для curl; Java REST-клиенты используют PKCS12 truststore.
Проверка сервера остается строгой. CA шлюза не назначен CA OpenShift API.

## Сохраненные ограничения

Kubernetes, туннель, мутации и подготовка фикстур IFT остаются выключенными.
Временный kubeconfig удален экспортёром; не указывать его как рабочий.
Пользовательские ID и прикладной токен не выводятся из mTLS-сертификата.
Параметры БД, Kafka, Ignite и существующие отключения тестов не изменены.
Для IFT выбрать env=ift в test.properties только перед согласованным запуском.
Это не делает полный регресс автоматически разрешенным или готовым.

Компиляция, локальная проверка конфигурации и обращения к стенду после этой
правки не выполнялись.
