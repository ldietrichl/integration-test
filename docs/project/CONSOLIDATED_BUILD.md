# Объединённая функциональная сборка

Состояние исходников на 08.10.2026. Сборка объединяет EXPLAB-2690,
2885 / EXLAB-2891, 2972, 2984 и 3056, регрессы scheduler, user-service,
Data Operator и существующие сценарии experiment-service и splitter.

Используется модульная Gradle-сборка: общие правила в `gradle/build-logic`,
задачи заявок в `gradle/tickets`, каталог в `gradle/architecture`,
подготовка и загрузка результатов в `gradle/testops`.
Общие метаданные Allure находятся в `src/reporting/java`.
JUnit ID и имена сценариев сохраняются.

## Сборка и локальная проверка

Требуются JDK 17 и Gradle 8.10, версия закреплена в wrapper.
Из корня проекта:

```powershell
.\gradlew.bat compileWithoutTests
.\gradlew.bat explab2885LocalChecks exlab2891OfflineChecks explab2984LocalChecks explab3056LocalChecks splitterReliabilityCheck workloadLeaseRecoveryCheck workloadContractChecks propertyLayoutTest allureSourceLayoutCheck
.\gradlew.bat -I gradle/workload-architecture.init.gradle workloadArchitectureCheck
```

Для локальных проверок нужны зависимости проекта, включая HTTP-провайдер
Fabric8. При использовании `-PuseLocalLibs=true` каталог `-PlocalLibDir`
должен содержать полный согласованный runtime. Библиотеки не поставляются
вместе с исходниками. Доступ к Nexus настраивается отдельно.

Ключи и пароли задаются в игнорируемом `secure.local.override.properties`.
Адреса и разрешения стенда проверяются перед функциональным запуском.
Локальные проверки не подтверждают работоспособность конкретного стенда;
новый функциональный прогон и отправка TestOps при объединении не выполняются.
Результаты propertyLayoutTest отделены от функционального Allure.

## Сохранённые ограничения

- Pairwise-набор 3056 не входит в завершённую реализацию v5.
- Выводы анализа Data Operator и вопросы бизнес-контрактов не заменяют
  утверждённых требований; ожидания не ослаблялись при объединении.
- Локальные настройки, приватные журналы, результаты прогонов, архивы и
  экспериментальный прототип языковой модели остаются вне поставки.
- Исторические отчёты заявок описывают свои прогоны и не являются результатом
  новой общей сборки. Публикация ветки не подтверждает установку на стенде.

## Проверка объединения 08.10.2026

Полная компиляция выполнена с JDK 17, Gradle 8.10 и согласованным локальным
runtime. Проверены настоящие модульные скрипты сборки.

| Задача | Проверок | Ошибок | Пропущено |
|---|---:|---:|---:|
| explab2885LocalChecks | 43 | 0 | 0 |
| exlab2891OfflineChecks | 49 | 0 | 0 |
| explab2984LocalChecks | 45 | 0 | 0 |
| explab3056LocalChecks | 92 | 0 | 0 |
| splitterReliabilityCheck | 17 | 0 | 0 |
| workloadLeaseRecoveryCheck | 14 | 0 | 0 |
| workloadArchitectureCheck | 54 | 0 | 0 |
| propertyLayoutTest | 80 | 0 | 0 |

Дополнительно прошли 29 проверок Allure, 31 проверка контрактов запросов scheduler,
74 проверки общего стенда и 29 проверок очистки. Полный workloadContractChecks
подтвердил lifecycle, 27 проверок изоляции scheduler, 174 фикстуры splitter
и 617 отклонений мутаций контракта. Это локальная проверка, без стенда и upload.

Восстановлена актуальная изоляция Kafka-профиля IFT-DM. Старая проверка cleanup
SQL согласована с обязательным доказательством владения orphan-записями
и сохранением чужих action. Локальные настройки исходных рабочих копий сохранены.
