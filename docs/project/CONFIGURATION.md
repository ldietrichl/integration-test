# Конфигурация проекта

Среда выбирается только ключом `env` в `src/test/resources/test.properties`. Подключения хранятся в профильных properties-файлах; секретные значения — только в игнорируемом `secure.local.override.properties` в корне проекта. Один и тот же порядок используется при запуске через Gradle и IDEA.

## Файлы и ответственность

| Файл относительно проекта | Что настраивать |
| --- | --- |
| `src/test/resources/test.properties` | `env`, REST-адреса, параметры сценариев, выбранные номера тестов, таймауты и ссылки `${...}` на токены |
| `src/test/resources/kafka-consumers.properties` | Нативные consumer-профили Platform V AT: `kafka_consumer.<profile>.*`, включая bootstrap, TLS/SASL и параметры чтения |
| `src/test/resources/kafka-producers.properties` | Нативные producer-профили Platform V AT: `kafka_producer.<profile>.*`, включая bootstrap, TLS/SASL и параметры публикации |
| `src/test/resources/database.properties` | Именованные подключения `db.<env>.<name>.*`: URL, login, password и timeout; секреты представлены ссылками `${...}` |
| `src/test/resources/ignite.properties` | Подключения и настройки Ignite через проектный адаптер; отдельного Ignite service в используемом SDK Platform V AT нет |
| `src/test/resources/regression-profiles.properties` | Несекретные параметры регрессов и соответствия Kafka-профилей/топиков; адреса брокеров и настройки аутентификации остаются в Kafka-файлах |
| `secure.local.properties` | Версионируемый шаблон имён секретов и незаполненных значений; не источник рабочих секретов |
| `secure.local.override.properties` | Единственный источник рабочих секретов; файл не попадает в Git |
| `gradle.properties` | Несекретные параметры сборки и версии зависимостей; рабочие пароли/токены здесь не размещаются |

Properties-файлы в репозитории содержат несекретную конфигурацию и ссылки на секреты. Не переносите рабочие секреты в шаблон `secure.local.properties`, профильные файлы или команды запуска. Сертификаты и приватные ключи также не добавляются в Git.

Для поддерживаемых значений `ENC(...)` и значение, и ключ `encryption.password` находятся только в `secure.local.override.properties`. Расшифровка использует проектный resolver и совместимый `BasicTextEncryptor`; глобальный JVM-источник ключа не используется. Отсутствующий, пустой или незаполненный ключ блокирует разрешение секрета; `-Dencryption.password` и переменные окружения его не заменяют. Шифрование не делает рабочий секрет допустимым содержимым версионируемого шаблона.

`ENV`, `-Denv` и `-Penv` не меняют выбранную среду. Переменные окружения, JVM properties, `gradle.local.properties` и `gradle.*.local.properties` больше не являются источниками секретов или параметров подключения. Если старый запуск зависел от такого переопределения, перенесите адрес/параметр в профильный файл, а секрет — в `secure.local.override.properties`.

Это правило не отменяет служебные параметры запуска. Gradle может передавать в JVM несекретные runtime-флаги фикстур `enabled`, `output.directory` и `run-id`. Они управляют подготовкой и местом сохранения артефактов, а не подключением; адреса, сертификаты, пароли и выбор среды через них не переопределяются.

## Подготовка

1. Откройте проект и используйте его корень как Working directory.
2. Если файла `secure.local.override.properties` ещё нет, создайте его из шаблона; существующий файл сохраняйте.
3. Заполните в нём необходимые значения для выбранных профилей. Незаданные секреты нельзя заменять фиктивными токенами или пустыми паролями.
4. Выберите `env` в `test.properties` и проверьте адреса/профили в соответствующих файлах.
5. Выполните `propertyLayoutTest`, затем запускайте подготовленный набор сценариев.

Команды выполняются из корня проекта:

```powershell
if (-not (Test-Path -LiteralPath '.\secure.local.override.properties')) {
    Copy-Item -LiteralPath '.\secure.local.properties' -Destination '.\secure.local.override.properties'
}
.\gradlew.bat propertyLayoutTest --no-daemon
```

Задача сначала выполняет `testClasses`, затем проверяет правила выбора и размещения properties. Проверки конфигурации не обращаются к стенду и не подтверждают доступность сервисов или действительность выданного IAM-токена.

## REST и среда

В `test.properties` оставьте один действующий ключ `env`. Например:

```properties
env=ift
rest.ift.experiments.base-uri=https://<EXPERIMENT_API>
rest.ift.configuration-service.base-uri=https://<CONFIGURATION_API>
rest.explab-gateway.token=${SECURE_2972_IFT_PRIMARY_TOKEN}
rest.configuration-service.token=${SECURE_2972_IFT_CONFIGURATION_TOKEN}
```

Замените адресные заполнители на параметры своего стенда. Значения ключей `SECURE_2972_IFT_PRIMARY_TOKEN` и `SECURE_2972_IFT_CONFIGURATION_TOKEN` заполняются только в `secure.local.override.properties`. Параметры сценариев остаются в `test.properties`; например, `explab2972.<env>.managed.cases` выбирает номера, но не создаёт для них фикстуры и не внедряет отказ.

## Kafka

Подключения задаются в нативном формате SDK. Общие параметры профилей находятся под `.all.`, настройки выбранного именованного профиля дополняют и переопределяют их. Профиль должен быть явно объявлен в соответствующем файле.

Пример для consumer в `kafka-consumers.properties`:

```properties
kafka_consumer.ift.bootstrap.servers=<BROKERS>
kafka_consumer.ift.security.protocol=SASL_SSL
kafka_consumer.ift.sasl.mechanism=<SASL_MECHANISM>
kafka_consumer.ift.sasl.jaas.config=${SECURE_KAFKA_IFT_JAAS}
```

Producer настраивается отдельно в `kafka-producers.properties`, с префиксом `kafka_producer.ift.`. Режим безопасности должен соответствовать реальному кластеру; пример не устанавливает его за оператора. Пароли TLS/SASL и JAAS со встроенными секретами задаются ссылками на override-файл.

Для EXPLAB-2972 имя consumer-профиля вычисляется из `env`; дефис заменяется подчёркиванием:

| `env` | Kafka-профиль |
| --- | --- |
| `dev` | `dev` |
| `ift` | `ift` |
| `ift-dm` | `ift_dm` |
| `lt` | `lt` |
| `local` | `local` |

`explab2972.<env>.kafka.topics` остаётся параметром наблюдения в `test.properties`. Старые `explab2972.<env>.kafka.bootstrap.servers` и `explab2972.<env>.kafka.property.*` не задают подключение: перенесите их в выбранный consumer-профиль. В EXPLAB-2972 consumer получает отдельную группу наблюдения, `enable.auto.commit=false` и начальные offsets перед бизнес-действием; это не меняет выбранный брокер или безопасность профиля.

## База данных и Ignite

Для БД адрес/учётная запись выбираются через `db.<env>.<name>.*` в `database.properties`. Сценарий задаёт имя клиента, например `explab2972.ift.db.experiment.name=explab`; фактическое подключение определяется `db.ift.explab.*`. При разделённых базах сервисов используйте разные имена. Настройки другой среды не должны подменять отсутствующее подключение.

Ignite-параметры находятся в `ignite.properties` и читаются проектным адаптером. В новом файле addresses, пути stores и client bundle оставлены явными `SET_ME`: рабочих значений в исходной конфигурации репозитория не было. Заполните их по фактическому стенду; неизвестные адреса и пути не подставляются автоматически. REST и Kafka продолжают использовать собственные клиенты Platform V AT; перенос Ignite-параметров в `test.properties` или JVM-аргументы не настраивает этот адаптер. Флаги подготовки фикстур и другие несекретные параметры сценария сохраняйте по назначению, отдельно от соединения и учётных данных.

## Сгенерированные файлы и очистка

Сгенерированные результаты и рабочие фикстуры располагаются внутри `build`. Проверка выходных путей запрещает запись Allure, логов и generated-фикстур за пределами `build`, в том числе при runtime-переопределении каталога:

- `build/regression-results/<env>/` — прогоны регрессов и объединённый TestOps-набор;
- `build/regression-fixtures/<env>/<run-id>/` — созданные данные/манифесты владения;
- `build/testops-results/<env>/` — подготовленные результаты выбранных тестов;
- `build/allure-results/`, `build/test-results/`, `build/reports/` — стандартные результаты;
- `build/explab2972-stand/` — evidence EXPLAB-2972.

Обычная команда удаляет весь `build`:

```powershell
.\gradlew.bat clean --no-daemon
```

Перед ней сохраните нужные отчёты и завершите очистку созданных данных стенда либо сохраните требуемые recovery-манифесты. Не запускайте `clean` между этапами одного цикла до формирования общего отчёта. `cleanRegressionResults` и `cleanTestOpsResults` выполняют адресную очистку по своим правилам; подробности — в [инструкции регрессов](REGRESSION_TASKS.md) и [инструкции TestOps](../reporting/TESTOPS_SELECTED_RUNS.md).

Статические входные JSON/SQL-фикстуры в `src/test/resources` сохраняются. Пользовательский внешний архив/raw-каталог также является входом сборщика: `clean` не расширяется на произвольные внешние пути и не удаляет их.
