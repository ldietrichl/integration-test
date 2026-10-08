# tests_v9/strict

Пакет содержит строгие проверки полного документного профиля `Тесты-v9`.

Эти сценарии не требуют системного флага включения и могут запускаться вручную из IDE. Перед запуском важно руками подготовить стенд под document-profile, иначе сценарии могут падать ожидаемо, потому что current ConfigMap отличается от полного документного режима.

## Что проверяется

- REST `ALL` содержит только сработавшие группы (`expGroup == finalExpGroup`);
- REACTIONS возвращает `ALL` по документной матрице;
- REACTIONS поддерживает множественный `MAIN`, если это требуется документом;
- REST response содержит `resultDt`, если реализация приведена к документному контракту;
- отдельный DENY-профиль пустого `MAIN`.

## Требуемые ресурсы

Примеры профилей находятся в:

- `src/test/resources/splitter/tests_v9/configmaps/document-profile/application-overrides.yml`
- `src/test/resources/splitter/tests_v9/configmaps/document-profile/application-deny-main-overrides.yml`
- `src/test/resources/splitter/tests_v9/configmaps/document-profile/reactions-rules-with-main.yml`

## Как запускать из IDE

1. Для ALLOW-сценариев применить:
   - `document-profile/application-overrides.yml`;
   - `document-profile/reactions-rules-with-main.yml`.
2. Перезапустить pod MAPPER/REACTIONS.
3. Запустить класс `SplitterV9StrictDocumentProfileFlowTest` или нужный метод из IDE.
4. Для DENY-сценария заменить профиль приложения на `application-deny-main-overrides.yml` и снова перезапустить pod.
5. После проверки вернуть current ConfigMap.

Регрессионные и critical-regression лейблы не используются.
