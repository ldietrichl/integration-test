package ru.sber.qa.experiments.EXPLAB_3109;

import io.qameta.allure.Description;
import io.qameta.allure.Issue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.sber.qa.allure.Regression;

@config.extensions.scheduler.UsesScenarioSteps(steps.flow.experiments.links.ExperimentLinksScenarioSteps.class)
@Issue("EXPLAB-3109")
@DisplayName("EXPLAB-3109: два API и события Kafka ExpLab на ИФТ")
public class ExperimentLinksCore3109FlowTest extends ExperimentLinks3109TestBase {

    @Test
    @Regression
    @DisplayName("[3109-01/experiment] Ручной endpoint создаёт задачу с параметрами запроса")
    @Description("Ограниченный запрос по собственному эксперименту и реальной кампании; задача сверяется через GET очереди и БД до обработки.")
    void test01() throws Exception { scenario.test01(); }

    @Test
    @Regression
    @DisplayName("[3109-09/START-02] Последовательный повтор ожидающей задачи не создаёт дубль")
    @Description("Повтор идентичного запроса проверяется, пока собственная задача остаётся в очереди. Конкурентная и вечная идемпотентность не предполагаются.")
    void test09() throws Exception { scenario.test09(); }

    @Test
    @Regression
    @DisplayName("[3109-18/START-03] POST без тела возвращает 400 и не меняет очередь")
    @Description("Нет тела, а не пустой JSON-объект. Campaign, Kafka, Kubernetes и создание фикстур не требуются.")
    void test18() throws Exception { scenario.test18(); }

    @Test
    @Regression
    @DisplayName("[TASKS-01] GET возвращает пустой массив для пустой очереди")
    @Description("HTTP-чтение текущих задач без параметров. Пустая очередь подтверждена БД до подготовки; фикстуры и барьер не создаются.")
    void test60() throws Exception { scenario.test60(); }

    @Test
    @Regression
    @DisplayName("[TASKS-02] GET показывает обе свои задачи с правильными полями и порядком")
    @Description("Весь ответ API сверяется с текущей очередью БД, включая технический барьер. Проверяются обе собственные задачи и id ASC.")
    void test61() throws Exception { scenario.test61(); }

    @Test
    @Regression
    @DisplayName("[3109-22-summary] Итог реальной задачи поступает в Kafka")
    @Description("Итог и фактическая доставка в настроенный топик ExpLab: taskId, область запроса, configIds, время, результат и Kafka metadata. Завершённая задача отсутствует в текущем GET очереди.")
    void test22() throws Exception { scenario.test22(); }

    @Test
    @Regression
    @DisplayName("[3109-23-chunks-pages] Журнал содержит все чанки по одному конфигу и все страницы")
    @Description("Два конфига — два чанка по одному. Проверяются все фактические страницы; одна страница допустима. Размеры не меняются, ответы сравниваются с независимым Campaign Service.")
    void test23() throws Exception { scenario.test23(); }

    @Test
    @Regression
    @DisplayName("[3109-26-create] Созданная связь отражена в журнале и БД")
    @Description("Созданная связь отражена в журнале и БД. Настоящие сервисы ИФТ, размер чанка 1; сверка с независимым источником и БД.")
    void test26() throws Exception { scenario.test26(); }

    @Test
    @Regression
    @DisplayName("[3109-27-update-snapshot] Старый и новый снимки обновления достоверны и независимы")
    @Description("Достаточно одного реального изменяемого набора. oldLink сверяется с БД до запуска, updatedLink — с независимым источником и БД после.")
    void test27() throws Exception { scenario.test27(); }

    @Test
    @Regression
    @DisplayName("[3109-28-no-change-repeat] Повтор завершённой задачи не создаёт ложных изменений связей")
    @Description("Реальная первая задача определяет фактический размер страницы. Повтор получает новый taskId, сохраняет ID и состав связей; все три массива действий сверяются с пустой дельтой БД и независимым стабильным Campaign.")
    void test28() throws Exception { scenario.test28(); }

    @Test
    @Regression
    @DisplayName("[3109-29-delete-snapshot] Удаление собственной связи публикует достоверный deletedLinks")
    @Description("Валидное исключение реальных WORKING communication IDs должно дать HTTP 200 с собственным configScope и cjs=[]. Собственная связь подготавливается из реальных данных. deletedLinks обязан содержать ID и все наборы исходного снимка, а БД после — не содержать связь.")
    void test29() throws Exception { scenario.test29(); }

    @Test
    @Regression
    @DisplayName("[3109-30-distinct-tasks] События двух задач с разными реальными данными не смешиваются")
    @Description("Два разных собственных эксперимента и фильтра; независимый Campaign дважды подтверждает разные непустые наборы. Обе задачи наблюдаются до release барьера; task/config/request/response и полная дельта БД проверяются отдельно.")
    void test30() throws Exception { scenario.test30(); }
}
