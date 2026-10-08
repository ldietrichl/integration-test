# Пользователи и токены EXPLAB-2972

Файл secure.users.local.override.properties расположен в корне проекта и исключён из Git.
Его пустой шаблон secure.users.example.properties можно хранить в репозитории.
Owner объединяет его с secure.local.override.properties, у которого выше приоритет.
Не дублировать пользовательские ключи в двух файлах. После обновления токена запускать новый JVM.

env в test.properties выбирает стенд. Ссылки explab2972.ift.auth.<role>.token/login/sub
ведут в приватный пользовательский файл. Явный primary-токен EXPLAB-2972 имеет приоритет
над rest.explab-gateway.token. Пустой или неразрешённый явно заданный токен блокирует запуск,
а не переключает его на другого пользователя. Configuration-service использует свой прежний
rest.configuration-service.token. User-service использует launch-plan.users.token или primary.

Начальные IFT-ссылки primary направлены на MAPPER ADMIN для обычного набора.
Для ролевых UC заполнить CREATOR и заменить ссылки primary с ADMIN на CREATOR;
approver/admin/operator используют отдельные записи. Глобальный env не меняется.
ift-dm не наследует эти подключения. В USER_ID хранится справочное значение:
ожидаемый числовой ID при необходимости задаётся в auth.<role>.user-id в test.properties.
LOGIN, SUB и userId нельзя взаимозаменять.

Заполненный локальный файл содержит три просроченных access_token из предоставленного PDF.
Обновить TOKEN до сетевого запуска; при изменении subject обновить SUB. В файле нет паролей.
Для CREATOR в PDF есть только логин, TOKEN/SUB оставлены пустыми. EMPLOYEE_ID/EMAIL
не выдумывались: для ролевого UC взять их из реальной записи user-service.
Режим пользователей prepared не меняет существующие учётные записи или их права.
