package ru.sber.qa.examples;

import io.perfeccionista.framework.Environment;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.sber.qa.config.ApiEnvironmentConfiguration;
import ru.sber.qa.config.entity.AuditTestFixture;
import ru.sber.qa.matchers.conditions.TextConditions;
import ru.sber.qa.services.audit.AuditService;
import ru.sber.qa.services.pvm.api.unofficial.dto.InternalSourceKafkaTopicsFetchRq;
import ru.sber.qa.validation.ValidatableJson;

import java.util.List;

import static ru.sber.qa.matchers.JsonMatchers.evaluateJsonPathExpression;
import static ru.sber.qa.matchers.JsonMatchers.evaluateJsonPathExpressions;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValue;
import static ru.sber.qa.matchers.JsonMatchers.haveJsonValueEqualTo;

@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(ApiEnvironmentConfiguration.class)
public class AuditTest extends AuditTestFixture {

    @Test
    void exampleTest() {
        AuditService auditService = Environment.getForCurrentThread().getService(AuditService.class);
        ValidatableJson actual = auditService.unifiedAuditClient("testvalue")
                .getRequiredValues(InternalSourceKafkaTopicsFetchRq.FilterType.CONTAIN, "INHERITANCE_PAY_INHERITANCE_PAYOUT_SELECT_HEIR_DOC");

        actual
                .should(
                        // Проверка наличия одного события в ответе
                        evaluateJsonPathExpression(
                                // Выражения данного блока эквивалентны
                                "find{ it.name == 'INHERITANCE_PAY_INHERITANCE_PAYOUT_SELECT_HEIR_DOC' } != null",
                                "find{ it.find { it.getKey() == 'name' && it.getValue() == 'INHERITANCE_PAY_INHERITANCE_PAYOUT_SELECT_HEIR_DOC' }} != null"
                        )
                );

        actual
                // Отбор события по имени
                .filter("find{ it.name == 'INHERITANCE_PAY_INHERITANCE_PAYOUT_SELECT_HEIR_DOC' }")
                // Переход к списку параметров события
                .filter("params")
                // Проверка параметров события
                .should(
                        // Нижеуказанные выражения выполняют одинаковую проверку
                        evaluateJsonPathExpressions(List.of(
                                "find{ it.name == 'EMPLOYEE_FIRST_NAME' }.value == 'Василий'",
                                "find{ it.find { it.getKey() == 'name' && it.getValue() == 'EMPLOYEE_FIRST_NAME' }}.value == 'Василий'"
                        )),
                        haveJsonValue("find{ it.name == 'EMPLOYEE_FIRST_NAME' }.value",
                                TextConditions.equalToText("Василий")),
                        haveJsonValueEqualTo("find{ it.name == 'EMPLOYEE_FIRST_NAME' }.value",
                                "Василий")
                );
    }
}
