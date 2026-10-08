package steps.rest.dataoperator;

import constants.Endpoints;
import dto.dataoperator.request.SplittingObjectIdsRequestDto;
import dto.dataoperator.request.SplittingObjectsRequestDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class DataOperatorSteps {

    private final RestClient client;

    public DataOperatorSteps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper getSplittingObjectIds(Object body) {
        return step("POST /api/v2/data-operator/splitting-objects-ids", () -> client.post(
                spec -> spec.body(body),
                Endpoints.DataOperator.V2_SPLITTING_OBJECT_IDS));
    }

    public ValidatableResponseWrapper getSplittingObjectIds(SplittingObjectIdsRequestDto body) {
        return getSplittingObjectIds((Object) body);
    }

    public ValidatableResponseWrapper getSplittingObjectIdsStatusOk(SplittingObjectIdsRequestDto body) {
        return step("Проверяем 200 OK метода получения идентификаторов объектов", () ->
                getSplittingObjectIds(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSplittingObjectIdsStatusBadRequest(Object body) {
        return step("Проверяем 400 BAD_REQUEST метода получения идентификаторов объектов", () ->
                getSplittingObjectIds(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getSplittingObjects(SplittingObjectsRequestDto body) {
        return step("POST /api/v2/data-operator/splitting-objects (контрольный метод)", () -> client.post(
                spec -> spec.body(body),
                Endpoints.DataOperator.V2_SPLITTING_OBJECTS));
    }

    public ValidatableResponseWrapper getSplittingObjectsStatusOk(SplittingObjectsRequestDto body) {
        return step("Проверяем 200 OK контрольного метода получения объектов", () ->
                getSplittingObjects(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }
}
