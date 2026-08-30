package util.explab2539;

import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

public final class LayerV2ByIdAssertions {

    private LayerV2ByIdAssertions() {
    }

    public static void shouldHaveSplittingPointScalarFields(ValidatableResponseWrapper response,
                                                            Long expectedLayerId,
                                                            String expectedCode,
                                                            String expectedName) {
        response.should(
                RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("id == " + expectedLayerId),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingPointCode == '" + expectedCode + "'"),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("splittingPointName == '" + expectedName + "'")
        );
    }

    public static void shouldNotExposeSplittingPointObject(ValidatableResponseWrapper response) {
        response.should(
                RestMatchers.haveStatusCode(HttpStatus.SC_OK),
                RestMatchers.haveBodyWithEvaluatableJsonPathExpression("containsKey('splittingPoint') == false")
        );
    }
}
