package ru.sber.qa.experiments.EXPLAB_2972;

import java.util.Map;

/** Scenario identities match the approved 60-row plan; no TestOps ids are fabricated. */
final class StatusChange2972Catalog {
    record Scenario(String title, String severity) { }
    private static final Map<Integer, Scenario> SCENARIOS = Map.ofEntries(
            Map.entry(1, new Scenario("Прямое согласование DRAFT→AGREED", "critical")),
            Map.entry(2, new Scenario("Согласование другим пользователем", "critical")),
            Map.entry(3, new Scenario("Без права согласования", "critical")),
            Map.entry(4, new Scenario("Прежний путь через AGREEMENT", "normal")),
            Map.entry(5, new Scenario("Сохранение прочих полей при согласовании", "normal")),
            Map.entry(6, new Scenario("Будущий автозапуск при прямом согласовании", "critical")),
            Map.entry(7, new Scenario("Выключенные/неуказанные автофлаги", "normal")),
            Map.entry(8, new Scenario("Просроченный автозапуск", "critical")),
            Map.entry(9, new Scenario("Граница даты автозапуска", "normal")),
            Map.entry(10, new Scenario("Просроченная автоостановка при согласовании", "normal")),
            Map.entry(11, new Scenario("Отмена обоих автодействий", "normal")),
            Map.entry(12, new Scenario("Будущая автоостановка", "normal")),
            Map.entry(13, new Scenario("Просроченная endDt при scheduleParam", "normal")),
            Map.entry(14, new Scenario("Ошибка создания автозапуска", "critical")),
            Map.entry(15, new Scenario("Автосогласование через bulk/processor", "normal")),
            Map.entry(16, new Scenario("Повтор прямого согласования", "normal")),
            Map.entry(17, new Scenario("Конкурентное согласование", "critical")),
            Map.entry(18, new Scenario("Недопустимые переходы", "normal")),
            Map.entry(19, new Scenario("Неизвестный/пустой целевой статус", "normal")),
            Map.entry(20, new Scenario("Несуществующий эксперимент и обязательные поля", "normal")),
            Map.entry(21, new Scenario("Неподходящая версия", "normal")),
            Map.entry(22, new Scenario("Терминальные/неразрешённые текущие статусы", "normal")),
            Map.entry(23, new Scenario("Возврат на доработку NOT_AGREED", "normal")),
            Map.entry(24, new Scenario("Удаление задач при возврате: ошибка/выключенный autoStart", "normal")),
            Map.entry(25, new Scenario("Отключение MAPPER не мешает согласованию", "normal")),
            Map.entry(26, new Scenario("Блокировка запуска/остановки MAPPER", "critical")),
            Map.entry(27, new Scenario("Mapper разрешён / другая точка", "normal")),
            Map.entry(28, new Scenario("Запуск после прямого согласования", "critical")),
            Map.entry(29, new Scenario("Корреляция четырёх действий запуска", "critical")),
            Map.entry(30, new Scenario("Создание автоостановки при STARTING", "normal")),
            Map.entry(31, new Scenario("Ошибка создания автоостановки", "normal")),
            Map.entry(32, new Scenario("Ошибка отправки конфигов запуска", "critical")),
            Map.entry(33, new Scenario("STARTING→IN_PROGRESS", "normal")),
            Map.entry(34, new Scenario("Остановка запущенного эксперимента", "critical")),
            Map.entry(35, new Scenario("Удаление только запланированных задач своего эксперимента", "normal")),
            Map.entry(36, new Scenario("Ошибка отправки конфигов остановки", "normal")),
            Map.entry(37, new Scenario("Ошибка удаления задач при STOPPING", "normal")),
            Map.entry(38, new Scenario("STOPPING→STOPPED", "normal")),
            Map.entry(39, new Scenario("EXP обрабатывается и подтверждается", "critical")),
            Map.entry(40, new Scenario("Конкуренция общего и EXP-обработчика", "critical")),
            Map.entry(41, new Scenario("Обработка новых SPLITTING и EXP_LINKS", "critical")),
            Map.entry(42, new Scenario("V1_EXP_CONFIG с новым expId", "critical")),
            Map.entry(43, new Scenario("EXP: ошибка enhance/пустой результат", "normal")),
            Map.entry(44, new Scenario("EXP: Kafka/подтверждение недоступно", "normal")),
            Map.entry(45, new Scenario("Нормализация источника и передача действия EXP в Kafka", "normal")),
            Map.entry(46, new Scenario("Callback: DONE и ERROR", "normal")),
            Map.entry(47, new Scenario("Callback: повтор и неизвестный requestId", "normal")),
            Map.entry(48, new Scenario("Callback не завершает статус сам", "normal")),
            Map.entry(49, new Scenario("Контракт приёма /request-configs", "normal")),
            Map.entry(50, new Scenario("Некорректные элементы /request-configs", "normal")),
            Map.entry(51, new Scenario("Атомарность пачки конфигов", "normal")),
            Map.entry(52, new Scenario("Повтор запроса конфигураций", "normal")),
            Map.entry(53, new Scenario("Ошибка сохранения/commit эксперимента", "critical")),
            Map.entry(54, new Scenario("Аудит прямого согласования", "critical")),
            Map.entry(55, new Scenario("Мониторинг результата", "normal")),
            Map.entry(56, new Scenario("Недоступность аудита", "normal")),
            Map.entry(57, new Scenario("Пакет статусов: успешный и ошибочный элементы", "normal")),
            Map.entry(58, new Scenario("Очередь статусов: отсутствующий эксперимент", "normal")),
            Map.entry(59, new Scenario("Полный жизненный цикл после DRAFT→AGREED", "normal")),
            Map.entry(60, new Scenario("Регрессия legacy V1_EXP_CONFIG и нового EXP", "normal"))
    );
    static Scenario get(int id) {
        Scenario scenario = SCENARIOS.get(id);
        if (scenario == null) throw new IllegalArgumentException("Unknown scenario: " + id);
        return scenario;
    }
}
