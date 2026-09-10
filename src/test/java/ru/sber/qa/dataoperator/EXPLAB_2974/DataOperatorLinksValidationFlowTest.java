package ru.sber.qa.dataoperator.EXPLAB_2974;

import config.environment.EnvironmentConfigWithRest;
import flow.RestFlows;
import io.perfeccionista.framework.SetEnvironmentConfiguration;
import io.perfeccionista.framework.extension.PerfeccionistaExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import util.dataoperator.LinksHttpEvidence;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import request.dataoperator.v2.DataOperatorLinksInvalidRequests;
import request.dataoperator.v2.DataOperatorLinksInvalidRequests.InvalidRequest;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.stream.Stream;

import static util.dataoperator.DataOperatorLinksAssertions.shouldHaveError;

/** Request-only scenarios: no objects/experiments are persisted, so no data teardown is required. */
@ExtendWith(PerfeccionistaExtension.class)
@SetEnvironmentConfiguration(EnvironmentConfigWithRest.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("data-operator-EXPLAB-2974")
public class DataOperatorLinksValidationFlowTest extends RestFlows {

    @RegisterExtension
    final LinksHttpEvidence evidence = new LinksHttpEvidence();

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRoot")
    @DisplayName("EXPLAB-2974 SL-35. Обязательные корневые поля")
    void shouldRejectMissingRootFields(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyExperiments")
    @DisplayName("EXPLAB-2974 SL-36. Пустой exps")
    void shouldRejectEmptyExperiments(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingNested")
    @DisplayName("EXPLAB-2974 SL-37. Обязательные вложенные поля")
    void shouldRejectMissingNestedFields(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRuleFields")
    @DisplayName("EXPLAB-2974 SL-38. Обязательные поля правила")
    void shouldRejectMissingRuleFields(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStructure")
    @DisplayName("EXPLAB-2974 SL-39. Типы полей и вложенная структура")
    void shouldRejectInvalidStructure(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArrays")
    @DisplayName("EXPLAB-2974 SL-40. Массивы строк")
    void shouldRejectInvalidStringArrays(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidExperimentIds")
    @DisplayName("EXPLAB-2974 SL-41. Некорректный expId и переполнение int64")
    void shouldRejectInvalidExperimentIds(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNumbers")
    @DisplayName("EXPLAB-2974 SL-42. Некорректный number и переполнение int16")
    void shouldRejectInvalidConditionNumbers(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEnums")
    @DisplayName("EXPLAB-2974 SL-43. Неизвестные и нестроковые enum")
    void shouldRejectInvalidEnums(InvalidRequest request) {
        assertBadRequest(request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("EXPLAB-2974 SL-45. Невалидное или отсутствующее тело")
    void shouldRejectInvalidBodies(InvalidRequest request) {
        assertBadRequest(request);
    }

    private void assertBadRequest(InvalidRequest request) {
        getFlowWithRest()
                .step("Отправляем " + request + " и проверяем HTTP 400 и контракт ошибки", flow -> {
                    // Raw JSON preserves missing/null fields, wrong types and out-of-range integers.
                    ValidatableResponseWrapper response = request.withoutBody()
                            ? flow.restCustomSteps().dataOperatorV2Steps().getSplittingObjectLinksWithoutBody()
                            : flow.restCustomSteps().dataOperatorV2Steps().getSplittingObjectLinks(request.body());
                    evidence.record(request, response);
                    shouldHaveError(response, 400, "Некорректный запрос");
                })
                .run();
    }

    static Stream<InvalidRequest> missingRoot() { return variants("SL-35"); }
    static Stream<InvalidRequest> emptyExperiments() { return variants("SL-36"); }
    static Stream<InvalidRequest> missingNested() { return variants("SL-37"); }
    static Stream<InvalidRequest> missingRuleFields() { return variants("SL-38"); }
    static Stream<InvalidRequest> invalidStructure() { return variants("SL-39"); }
    static Stream<InvalidRequest> invalidArrays() { return variants("SL-40"); }
    static Stream<InvalidRequest> invalidExperimentIds() { return variants("SL-41"); }
    static Stream<InvalidRequest> invalidNumbers() { return variants("SL-42"); }
    static Stream<InvalidRequest> invalidEnums() { return variants("SL-43"); }
    static Stream<InvalidRequest> invalidBodies() { return variants("SL-45"); }

    private static Stream<InvalidRequest> variants(String id) {
        return DataOperatorLinksInvalidRequests.forScenario(id).stream();
    }
}
