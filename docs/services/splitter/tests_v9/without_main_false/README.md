# tests_v9/without_main_false

Пакет проверяет профиль сервиса:

```text
SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false
```

Сценарии помечены аннотацией `@ManualTest`, потому что проверяют именно false-профиль и требуют ручного переключения configmap/env и перезапуска pod MAPPER. После EXPLAB-2690 технический пустой `MAIN` запрещён в обоих профилях; false-профиль дополнительно фиксирует форму ответа при исключении объекта без итогового эксперимента.

## Что проверяется

- объект без итогового `MAIN` не должен возвращаться с техническим `MAIN.resultExps=[]`;
- объект с группой без `resultParams`/без параметра для выбора итогового эксперимента не должен возвращаться как пустой `MAIN`;
- в смешанном запросе объект с валидным итоговым `MAIN` остается в ответе, а объект без итогового `MAIN` исключается/возвращается без `objectResults`;
- в любом ответе false-профиля запрещен пустой блок `MAIN`.

## Как запускать

Точечно пакет можно запускать по package-фильтру. В общем v9-прогоне эти сценарии помечаются как manual в Allure через `@ManualTest` и требуют стенд с `SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false`.


Перед запуском вручную выставить на MAPPER-сервисе:

```text
SPLITTER_ALLOW_RESULT_WITHOUT_MAIN=false
```

После изменения configmap/env перезапустить pod сервиса.

Linux / Git Bash:

```bash
./gradlew clean test \
  --tests "ru.sber.qa.splitter.tests_v9.without_main_false.*" \
  -Dencryption.password=<PASSWORD> \
  -Denv=dev
```

Windows PowerShell:

```powershell
.\gradlew.bat clean test `
  --tests "ru.sber.qa.splitter.tests_v9.without_main_false.*" `
  -Dencryption.password="<PASSWORD>" `
  -Denv=dev
```

После проверки вернуть штатное значение флага, принятое для общего DEV-профиля. Основной `tests_v9.document.*` набор больше не считает технический пустой `MAIN` допустимым ни при каком значении флага.
