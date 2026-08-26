package steps.rest.dataoperator.v2;

import constants.Endpoints;
import dto.dataoperator.v2.ExplicitNullReturnIdsRequestDto;
import dto.dataoperator.v2.InvalidSplittingObjectsRequestDto;
import dto.dataoperator.v2.SoDictByParamsRequestDto;
import dto.dataoperator.v2.SoFieldValuesDictRequestDto;
import dto.dataoperator.v2.SplittingObjectsIdsRequestDto;
import dto.dataoperator.v2.SplittingObjectsRequestDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class DataOperatorV2Steps {

    private final RestClient client;

    public DataOperatorV2Steps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper getSplittingObjects(SplittingObjectsRequestDto body) {
        return step("Получаем данные объектов сплиттования", () -> client.post(
                specification -> specification.body(SplittingObjectsRequestDto.toJson(body)),
                Endpoints.DataOperatorV2.SPLITTING_OBJECTS));
    }


    public ValidatableResponseWrapper getSplittingObjects(ExplicitNullReturnIdsRequestDto body) {
        return step("Отправляем запрос получения объектов с явным returnIds=null", () -> client.post(
                specification -> specification.body(ExplicitNullReturnIdsRequestDto.toJson(body)),
                Endpoints.DataOperatorV2.SPLITTING_OBJECTS));
    }

    public ValidatableResponseWrapper getSplittingObjects(InvalidSplittingObjectsRequestDto body) {
        return step("Отправляем negative-contract запрос получения объектов сплиттования", () -> client.post(
                specification -> specification.body(InvalidSplittingObjectsRequestDto.toJson(body)),
                Endpoints.DataOperatorV2.SPLITTING_OBJECTS));
    }


    public ValidatableResponseWrapper getSplittingObjectsStatusOk(ExplicitNullReturnIdsRequestDto body) {
        return step("Проверяем 200 OK метода splitting-objects при returnIds=null", () -> getSplittingObjects(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSplittingObjectsStatusOk(SplittingObjectsRequestDto body) {
        return step("Проверяем 200 OK метода splitting-objects", () -> getSplittingObjects(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSplittingObjectsStatusBadRequest(SplittingObjectsRequestDto body) {
        return step("Проверяем 400 BAD_REQUEST метода splitting-objects", () -> getSplittingObjects(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getSplittingObjectsStatusBadRequest(InvalidSplittingObjectsRequestDto body) {
        return step("Проверяем 400 BAD_REQUEST для returnIds неверного типа", () -> getSplittingObjects(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public ValidatableResponseWrapper getSplittingObjectIds(SplittingObjectsIdsRequestDto body) {
        return step("Получаем идентификаторы объектов старым методом splitting-objects-ids", () -> client.post(
                specification -> specification.body(SplittingObjectsIdsRequestDto.toJson(body)),
                Endpoints.DataOperatorV2.SPLITTING_OBJECTS_IDS));
    }

    public ValidatableResponseWrapper getSplittingObjectIdsStatusOk(SplittingObjectsIdsRequestDto body) {
        return step("Проверяем 200 OK метода splitting-objects-ids", () -> getSplittingObjectIds(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }


    public ValidatableResponseWrapper getObjectByIdStatusOk(String splittingPoint, String id) {
        return step("Получаем объект data-operator по id", () -> client.get(
                specification -> specification
                        .pathParam("splittingPoint", splittingPoint)
                        .pathParam("id", id),
                Endpoints.DataOperatorV2.OBJECT_BY_ID)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSoFieldValuesDict(SoFieldValuesDictRequestDto request) {
        return step("Получаем справочник значений объектов сплиттования", () -> client.get(
                specification -> {
                    specification
                            .queryParam("splittingPointCode", request.getSplittingPointCode())
                            .queryParam("dictParamCode", request.getDictParamCode());
                    if (request.getSearch() != null) {
                        specification.queryParam("search", request.getSearch());
                    }
                    if (request.getResponseLimit() != null) {
                        specification.queryParam("responseLimit", request.getResponseLimit());
                    }
                    return specification;
                },
                Endpoints.DataOperatorV2.SO_FIELD_VALUES_DICTS));
    }

    public ValidatableResponseWrapper getSoFieldValuesDictStatusOk(SoFieldValuesDictRequestDto request) {
        return step("Проверяем 200 OK метода so-field-values-dicts", () -> getSoFieldValuesDict(request)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSoDictByParams(SoDictByParamsRequestDto body) {
        return step("Получаем справочник по набору параметров", () -> client.post(
                specification -> specification.body(SoDictByParamsRequestDto.toJson(body)),
                Endpoints.DataOperatorV2.SO_DICT_BY_PARAMS));
    }

    public ValidatableResponseWrapper getSoDictByParamsStatusOk(SoDictByParamsRequestDto body) {
        return step("Проверяем 200 OK метода so-dict-by-params", () -> getSoDictByParams(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getSplittingObjectLinks(Object body) {
        return step("Вызываем back-метод расчета связей объектов", () -> client.post(
                specification -> specification.body(body),
                Endpoints.DataOperatorV2.SPLITTING_OBJECTS_LINKS));
    }

    public ValidatableResponseWrapper getSplittingObjectLinksStatusBadRequest(Object body) {
        return step("Проверяем 400 BAD_REQUEST метода splitting-objects-links", () -> getSplittingObjectLinks(body)
                .should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }
}
