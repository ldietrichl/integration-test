package steps.rest.experiments.v2.registry;

import constants.Endpoints;
import dto.enums.SortsDirections;
import dto.experiments.v2.registry.ExperimentsV2RegistryPostRequestDto;
import dto.experiments.v2.registry.ExperimentsV2RegistryPostRequestDtoBuilder;
import org.apache.http.HttpStatus;
import util.TestAssertions;
import ru.sber.qa.matchers.RestMatchers;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.List;
import java.util.Map;

import static io.qameta.allure.Allure.step;

public class RegistryV2Steps {
    private final RestClient client;

    public RegistryV2Steps(RestClient client) {
        this.client = client;
    }

    public ValidatableResponseWrapper getExperimentsRegistry(ExperimentsV2RegistryPostRequestDto request) {
        return step("Запрос реестра экспериментов", () -> client
                .post(
                        spec -> spec
                                .body(ExperimentsV2RegistryPostRequestDto.toJson(request))
                        , Endpoints.ExperimentsV2.V2_EXPERIMENTS_REGISTRY));
    }

    public ValidatableResponseWrapper getExperimentsRegistryStatusOk(
            ExperimentsV2RegistryPostRequestDto request) {
        return step("Проверяем что запрос реестра экспериментов вернул 200 OK", () ->
                getExperimentsRegistry(request).should(RestMatchers.haveStatusCode(HttpStatus.SC_OK)));
    }

    public ValidatableResponseWrapper getExperimentsRegistryStatusBadRequest(
            ExperimentsV2RegistryPostRequestDto request) {
        return step("Проверяем что запрос реестра экспериментов вернул 400 BAD_REQUEST", () ->
                getExperimentsRegistry(request).should(RestMatchers.haveStatusCode(HttpStatus.SC_BAD_REQUEST)));
    }

    public void checkExperimentsRegistrySort(String sortField, SortsDirections sortsDirections) {
        step("Проверка сортировки по: '%s'".formatted(sortsDirections.getValue()), () -> {
            ExperimentsV2RegistryPostRequestDto request
                    = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithFilters()
                    .toBuilder()
                    .sorts(
                            List.of(
                                    ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault()
                                            .toBuilder()
                                            .paramCode(sortField)
                                            .direction(sortsDirections)
                                            .build()))
                    .build();

            ValidatableResponseWrapper responseWrapper = getExperimentsRegistryStatusOk(request)
                    .should(
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                            , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                    "schemes/experiments/experiments_registry_response_200_schema.json")
                    );

            checkContentSortDirection(responseWrapper.toJsonPath().getList("content"), sortsDirections, sortField);
        });
    }

    public void checkExperimentsRegistryPairSort(
            String sortField1
            , String sortField2
            , SortsDirections sortsDirections) {
        step("Проверка сортировки по: '%s'".formatted(sortsDirections.getValue()), () -> {
            ExperimentsV2RegistryPostRequestDto request
                    = ExperimentsV2RegistryPostRequestDtoBuilder.buildDtoDefaultWithFilters()
                    .toBuilder()
                    .sorts(
                            List.of(
                                    ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault()
                                            .toBuilder()
                                            .paramCode(sortField1)
                                            .direction(sortsDirections)
                                            .build()
                                    , ExperimentsV2RegistryPostRequestDtoBuilder.sortsDefault()
                                            .toBuilder()
                                            .paramCode(sortField2)
                                            .direction(sortsDirections)
                                            .build()
                            ))
                    .build();

            getExperimentsRegistryStatusOk(request)
                    .should(
                            RestMatchers.haveBodyWithEvaluatableJsonPathExpression("content.size() == 10")
                            , RestMatchers.haveBodyMatchesJsonSchemaInClasspath(
                                    "schemes/experiments/experiments_registry_response_200_schema.json")
                    );
        });
    }

    private void checkContentSortDirection(
            List<Map<String, Object>> content
            , SortsDirections sortsDirections
            , String... sortFields) {
        TestAssertions.assertFalse(content.isEmpty(), "Реестр экспериментов должен содержать записи для проверки сортировки");

        for (int i = 1; i < content.size(); i++) {
            int compare = compareRows(content.get(i - 1), content.get(i), sortFields);
            boolean sorted = sortsDirections == SortsDirections.DESC
                    ? compare >= 0
                    : compare <= 0;

            TestAssertions.assertTrue(
                    sorted
                    , ("Полученный реестр экспериментов должен быть отсортирован по '%s' в полях %s. "
                            + "Нарушение порядка между элементами %s и %s: %s / %s")
                            .formatted(
                                    sortsDirections.getValue()
                                    , List.of(sortFields)
                                    , i - 1
                                    , i
                                    , extractValues(content.get(i - 1), sortFields)
                                    , extractValues(content.get(i), sortFields)));
        }
    }

    private int compareRows(Map<String, Object> left, Map<String, Object> right, String... sortFields) {
        for (String sortField : sortFields) {
            int compare = compareValues(valueByPath(left, sortField), valueByPath(right, sortField));
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private List<Object> extractValues(Map<String, Object> contentItem, String... sortFields) {
        return List.of(sortFields).stream()
                .map(sortField -> valueByPath(contentItem, sortField))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Object valueByPath(Map<String, Object> contentItem, String jsonPath) {
        Object value = contentItem;
        for (String pathPart : jsonPath.split("\\.")) {
            if (!(value instanceof Map<?, ?>)) {
                return null;
            }
            value = ((Map<String, Object>) value).get(pathPart);
        }
        return value;
    }

    private int compareValues(Object left, Object right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        return left.toString().compareToIgnoreCase(right.toString());
    }
}

