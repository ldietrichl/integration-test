package steps.rest.dictionaries.v1;

import constants.Endpoints;
import dto.dictionaries.request.ExpressionParameterDictReqDto;
import org.apache.http.HttpStatus;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import static io.qameta.allure.Allure.step;

public class DictionariesV1Steps {

    private final RestClient client;

    public DictionariesV1Steps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper getExpressionParameterDict(ExpressionParameterDictReqDto body) {
        return step("Получаем справочник параметров выражений", () -> client.post(
                spec -> spec.body(body),
                Endpoints.DictionariesV2.V2_EXPRESSION_PARAMETER_DICT));
    }

    public ValidatableResponseWrapper getExpressionParameterDictStatusOk(ExpressionParameterDictReqDto body) {
        return step("Проверяем, что справочник параметров выражений вернул 200 OK", () ->
                getExpressionParameterDict(body).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }
}
