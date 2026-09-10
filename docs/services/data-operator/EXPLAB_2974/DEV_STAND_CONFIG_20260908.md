# EXPLAB-2974: параметры dev из Pod YAML и ConfigMap

Источники: [yaml.txt](A:/Codex/Functional/BACK/07_issue_tracking/jira/EXPLAB-2974/yaml.txt) и [config_map.txt](A:/Codex/Functional/BACK/07_issue_tracking/jira/EXPLAB-2974/config_map.txt), прочитаны 08.09.2026. Первый файл — снимок объекта **Pod**, не Deployment или Service. Значения секретов не извлекались из работающего стенда.

| Параметр | Значение из файлов |
|---|---|
| Namespace | `ci07963639-dev-terra000003-abtm-back` |
| Pod | `data-operator-service-6b964689d9-qgm8n` |
| Контейнер приложения | `data-operator-service` |
| Контейнер по умолчанию | `fluentbit`; поэтому в `oc` явно указывать `-c data-operator-service` |
| ConfigMap приложения | `data-operator-service`, подключена через `envFrom.configMapRef` |
| Ignite thin-client | `tsled-mvp000384.esrt.sber.ru:10800`, `tsled-mvp000385.esrt.sber.ru:10800`, `tsled-mvp000386.esrt.sber.ru:10800` |
| Пользователь Ignite | `ise_client` |
| Ignite SSL | `false` в ConfigMap |
| JAR | `/app/app.jar`, подтверждён командой запуска контейнера |
| Конфигурация секретов | `/sbernba/secrets/env.yml`, формируется Vault и подключается через `-Dspring.config.location` |
| Хранилища Ignite | `/sbernba/ignite_settings/ignite_keystore.jks`, `/sbernba/ignite_settings/ignite_truststore.jks`; при подтверждённом SSL=false helper их не использует |
| Имена пяти кэшей | `splitting_object_cache`, `splitting_field_cache`, `actualization_cache`, `param_cache`, `data_source_cache`; совпадают с текущим helper |
| Образ приложения | `abtm_data-operator-service:d-26.008.03_data-operator-service136` |
| Digest образа | `sha256:fb7a75b8c44b39a3f7a749da6da138d9f20129fbb39db41f29cf0d87bf0524fd` |

По сохранённому статусу контейнер приложения Ready, restartCount=0, startedAt=`2026-08-19T18:03:04Z`. Это состояние снимка, а не текущая онлайн-проверка. Связь развёрнутого образа с исходным архивом коммита `06fd6acb646` не установлена; дату старта Pod и тег образа нельзя считать доказательством наличия EXPLAB-2974. Полученный из Pod JAR позволит проверить состав и метаданные корпоративной сборки.

Готов [фрагмент настроек без пароля](dev-fixture.properties.example). Его следует добавить в существующий корпоративный `test.properties`, сохранив штатные `env`, REST-адрес и HTTP-сертификаты. Путь к JAR в примере соответствует корню проекта из приложенного лога; при другом расположении его необходимо заменить. В существующем `secure.local.override.properties` заполнить ключ `SECURE_EXPLAB2974_DEV_IGNITE_PASSWORD` фактическим паролем этой учётной записи. Отсутствующее значение оставлено явным плейсхолдером.

ConfigMap не содержит пароля. Pod YAML указывает на Vault и файл `env.yml`, но не предоставляет его фактическое содержимое. Секрет, заданный в Spring YAML, может отсутствовать в выводе `printenv`; это само по себе не означает, что сервис запущен без пароля. HTTP TLS и Ignite SSL независимы: `false` для Ignite не меняет HTTPS/mTLS REST-запросов.

## Команды отдельно

В терминале контейнера приложения подтвердить адреса, полученные из ConfigMap:

```sh
printenv DATA_OPERATOR_SERVICE_IGNITE_ADDRESSES
```

Подтвердить режим SSL:

```sh
printenv DATA_OPERATOR_SERVICE_IGNITE_SSL_ENABLED
```

Проверить наличие JAR:

```sh
ls -lh /app/app.jar
```

Получить SHA256 JAR (это другой хэш, не digest контейнерного образа):

```sh
sha256sum /app/app.jar
```

Проверить только наличие файла с секретами:

```sh
ls -l /sbernba/secrets/env.yml
```

Из PowerShell корпоративного компьютера, в корне тестового проекта, скопировать JAR. Использован относительный локальный путь без двоеточия Windows-диска. Команда требует настроенного `oc`, доступов к Pod и `tar` в контейнере. Имя Pod относится к предоставленному снимку; после его замены использовать актуальное имя.

```powershell
oc cp -n ci07963639-dev-terra000003-abtm-back -c data-operator-service data-operator-service-6b964689d9-qgm8n:/app/app.jar ./data-operator-dev.jar
```

Проверить хэш полученного файла:

```powershell
Get-FileHash -LiteralPath .\data-operator-dev.jar -Algorithm SHA256
```

С компьютера, где запускаются тесты, проверить TCP-доступ отдельно к каждому узлу:

```powershell
Test-NetConnection tsled-mvp000384.esrt.sber.ru -Port 10800
```

```powershell
Test-NetConnection tsled-mvp000385.esrt.sber.ru -Port 10800
```

```powershell
Test-NetConnection tsled-mvp000386.esrt.sber.ru -Port 10800
```

`TcpTestSucceeded=True` подтверждает сетевую доступность порта, но не успешную аутентификацию и не права на кэши. Эти проверки нужны на корпоративном компьютере с тестами: сеть контейнера и сеть рабочего места могут отличаться.

Остаются: JAR развёрнутого приложения на компьютере с тестами, пароль `ise_client`, подтверждение сетевого доступа/прав Ignite и реального REST-загрузчика. Сами конфигурационные файлы не доказывают доступность или работоспособность загрузчика. Дополнительные заглушки не создавались, текущие настройки проекта и ранее выданный ZIP не изменялись.
