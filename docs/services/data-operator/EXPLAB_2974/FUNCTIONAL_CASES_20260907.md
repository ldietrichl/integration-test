# EXPLAB-2974: матрица функциональных проверок

70 вариантов по 31 идентификатору плана. D1 создаётся заново для каждого варианта; ожидания заданы независимо от реализации сервиса.

Локальный прогон 07.09.2026: 52 passed, 18 failed; результаты корпоративного стенда необходимо получить отдельно.

| ID | Вариант | Набор | Ожидаемые пары по expId/number | Локальный итог |
|---|---|---|---|---|
| SL-01 | Базовый расчёт; SP2 исключена | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-02 | Два эксперимента с одинаковым номером условия | D1 | conditions={"101321": [1], "101322": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101); Selection[expId=101322, number=1]: (1002, 103) | passed |
| SL-03 | Раздельные условия 1 и 7 | D1 | conditions={"101321": [7, 1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101); Selection[expId=101321, number=7]: (1002, 103), (1001, 102) | passed |
| SL-04 | Нет совпадений; эксперимент и условие сохранены | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | passed |
| SL-05 | Пустое и непустое условия в двух экспериментах | D1 | conditions={"101321": [2, 1], "101322": [2, 1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101); Selection[expId=101321, number=2]: пусто; Selection[expId=101322, number=2]: пусто; Selection[expId=101322, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-06 | Один эксперимент с пустыми условиями | D1 | conditions={"101321": []} | failed |
| SL-06 | Пустые условия вместе с непустым экспериментом | D1 | conditions={"101321": [], "101322": [1]}; Selection[expId=101322, number=1]: (1001, 102), (1001, 101), (1002, 101) | failed |
| SL-07 | Две группы родителей, полные множества ids | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-08 | Пересекающиеся OR-группы без дублей | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 105) | passed |
| SL-08 | Повтор OR-группы без дублей | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 105) | passed |
| SL-09 | Один id у двух родителей | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101), (1002, 101) | passed |
| SL-10 | Объект без parentId | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (без parentId, 104) | failed |
| SL-11 | Строковые ids 001, 1 и CJ-01 | STRING_ID | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 001), (1001, 1), (1002, CJ-01) | passed |
| SL-11 | Строковые родители 001 и 1 | STRING_PARENT | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1, 102), (001, 101) | passed |
| SL-12 | Нет key=true | NO_OBJECT_KEY | conditions={} | passed |
| SL-12 | Тип key=null | NULL_OBJECT_TYPE | conditions={} | failed |
| SL-13 | Нет родительского ключа | NO_PARENT_KEY | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (без parentId, 104) | failed |
| SL-16 | AND: Кредит и channelId > 10 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1002, 101) | passed |
| SL-17 | OR: Кредит или channelId=20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 103) | passed |
| SL-18 | OR из двух AND-групп | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (1001, 102) | passed |
| SL-19 | Несовместимые условия AND дают пустой результат | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | failed |
| SL-20 | INTEGER more 20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (без parentId, 104) | failed |
| SL-20 | INTEGER less 20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 105), (1001, 101) | passed |
| SL-20 | INTEGER more_equal 20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 102), (1002, 103), (без parentId, 104) | failed |
| SL-20 | INTEGER less_equal 20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101), (1001, 102), (1002, 103), (1002, 105) | passed |
| SL-21 | NUMBER equal 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102) | passed |
| SL-21 | NUMBER not_equal 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (без parentId, 104), (1001, 101), (1002, 101), (1002, 103), (1002, 105) | failed |
| SL-21 | NUMBER more 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (1002, 101) | passed |
| SL-21 | NUMBER less 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 105), (1001, 101), (без parentId, 104) | failed |
| SL-21 | NUMBER more_equal 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1002, 103), (1002, 101) | passed |
| SL-21 | NUMBER less_equal 10.0 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101), (1001, 102), (без parentId, 104), (1002, 105) | failed |
| SL-21 | NUMBER: отрицательное значение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 105) | passed |
| SL-22 | STRING equal всей строки | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-22 | STRING not_equal всей строки | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (1002, 105), (без parentId, 104) | failed |
| SL-25 | BOOLEAN equal true | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (без parentId, 104) | failed |
| SL-25 | BOOLEAN equal false | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (1002, 105), (1002, 101) | passed |
| SL-25 | BOOLEAN not_equal true | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (1002, 105), (1002, 101) | passed |
| SL-26 | IN 10,20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101), (1001, 102), (1002, 103), (1002, 105) | passed |
| SL-26 | NOT IN 10,20 | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (без parentId, 104) | failed |
| SL-26 | IN с повтором значения | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101), (1001, 102), (1002, 103), (1002, 105) | passed |
| SL-27 | is_null: values=absent | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (без parentId, 104) | failed |
| SL-27 | is_null: values=null | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (без parentId, 104) | failed |
| SL-27 | is_null: values=empty | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 103), (без parentId, 104) | failed |
| SL-27 | is_not_null: values=absent | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 105) | passed |
| SL-27 | is_not_null: values=null | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 105) | passed |
| SL-27 | is_not_null: values=empty | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101), (1001, 101), (1001, 102), (1002, 105) | passed |
| SL-29 | objectIds пересекается с правилом | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102) | passed |
| SL-30 | parentObjectIds пересекается с правилом | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101) | passed |
| SL-31 | Оба фильтра: совпадение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 101) | passed |
| SL-31 | Оба фильтра: несовместимые ограничения | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | passed |
| SL-32 | objectIds=absent, parentObjectIds=absent | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=absent, parentObjectIds=null | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=absent, parentObjectIds=empty | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=null, parentObjectIds=absent | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=null, parentObjectIds=null | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=null, parentObjectIds=empty | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=empty, parentObjectIds=absent | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=empty, parentObjectIds=null | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | objectIds=empty, parentObjectIds=empty | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-32 | Пустой objectIds и непустой parentObjectIds | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101) | passed |
| SL-32 | Непустой objectIds и пустой parentObjectIds | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102) | passed |
| SL-33 | objectIds: повторы и неизвестное значение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1001, 102) | passed |
| SL-33 | objectIds: только неизвестное значение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | passed |
| SL-33 | parentObjectIds: повторы и неизвестное значение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (1002, 101) | passed |
| SL-33 | parentObjectIds: только неизвестное значение | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | passed |
| SL-34 | Объект без родителя: контроль без фильтра | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: (без parentId, 104) | failed |
| SL-34 | Родительский фильтр исключает объект без родителя | D1 | conditions={"101321": [1]}; Selection[expId=101321, number=1]: пусто | passed |
| SL-41 | Точный expId=2147483648 | D1 | conditions={"2147483648": [1]}; Selection[expId=2147483648, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-41 | Точный expId=9223372036854775807 | D1 | conditions={"9223372036854775807": [1]}; Selection[expId=9223372036854775807, number=1]: (1001, 102), (1001, 101), (1002, 101) | passed |
| SL-42 | Точная верхняя граница number=32767 | D1 | conditions={"101321": [32767]}; Selection[expId=101321, number=32767]: (1001, 102), (1001, 101), (1002, 101) | passed |
