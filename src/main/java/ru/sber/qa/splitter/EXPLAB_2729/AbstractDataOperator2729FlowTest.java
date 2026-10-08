package ru.sber.qa.splitter.EXPLAB_2729;

import constants.Endpoints;
import dto.dataoperator.DataOperatorRuleDto;
import dto.dataoperator.request.SplittingObjectIdsRequestDto;
import dto.dataoperator.request.SplittingObjectsRequestDto;
import dto.dataoperator.response.SplittingObjectsResponseDto;
import flow.Flows;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;
import util.dataoperator.DataOperatorResponseMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static request.dataoperator.DataOperatorTestDataFactory.DEFAULT_SPLITTING_POINT;
import static request.dataoperator.DataOperatorTestDataFactory.registryRequest;

public abstract class AbstractDataOperator2729FlowTest extends Flows {

    private static final Map<String, String> KNOWN_TYPES = knownTypes();

    @BeforeEach
    void addDataOperatorRuntimeMetadata() {
        Allure.parameter("dataOperator.splittingPointCode", DEFAULT_SPLITTING_POINT);
        Allure.parameter("dataOperator.objectIdsEndpoint", Endpoints.DataOperator.V2_SPLITTING_OBJECT_IDS);
        Allure.parameter("dataOperator.registryEndpoint", Endpoints.DataOperator.V2_SPLITTING_OBJECTS);
    }

    protected ValidatableResponseWrapper objectIds(FlowWithRest flow, SplittingObjectIdsRequestDto request) {
        return flow.restCustomSteps().dataOperatorSteps().getSplittingObjectIdsStatusOk(request);
    }

    protected ValidatableResponseWrapper objects(FlowWithRest flow, SplittingObjectsRequestDto request) {
        return flow.restCustomSteps().dataOperatorSteps().getSplittingObjectsStatusOk(request);
    }

    protected CandidateRule requireAnyCandidate(FlowWithRest flow) {
        return requireCandidate(flow, false);
    }

    protected CandidateRule requireStringCandidate(FlowWithRest flow) {
        return requireCandidate(flow, true);
    }

    protected CandidateRule requireNumericCandidate(FlowWithRest flow) {
        SplittingObjectsResponseDto dto = DataOperatorResponseMapper.objectsResponse(
                objects(flow, registryRequest(false, List.of())));
        Optional<CandidateRule> candidate = candidates(dto).stream()
                .filter(item -> List.of("INTEGER", "NUMBER").contains(item.dataType()))
                .filter(item -> isNumber(item.value()))
                .findFirst();
        Assumptions.assumeTrue(candidate.isPresent(),
                "Для проверки числовых операторов в Ignite нужен объект MAPPER с числовым отображаемым параметром");
        return candidate.orElseThrow();
    }

    protected List<CandidateRule> availableCandidates(FlowWithRest flow) {
        SplittingObjectsResponseDto dto = DataOperatorResponseMapper.objectsResponse(
                objects(flow, registryRequest(false, List.of())));
        return candidates(dto);
    }

    private CandidateRule requireCandidate(FlowWithRest flow, boolean stringOnly) {
        List<CandidateRule> candidates = availableCandidates(flow);
        Optional<CandidateRule> candidate = candidates.stream()
                .filter(item -> !stringOnly || "STRING".equals(item.dataType()))
                .findFirst();
        Assumptions.assumeTrue(candidate.isPresent(),
                "Для rule-сценария в Ignite нужен хотя бы один объект с отображаемым параметром"
                        + (stringOnly ? " типа STRING" : ""));
        return candidate.orElseThrow();
    }

    private List<CandidateRule> candidates(SplittingObjectsResponseDto dto) {
        if (dto.getContent() == null) {
            return List.of();
        }
        return dto.getContent().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .filter(param -> param.getCode() != null && param.getValues() != null)
                .flatMap(param -> param.getValues().stream()
                        .filter(Objects::nonNull)
                        .filter(value -> value.getValue() != null)
                        .map(value -> new CandidateRule(
                                param.getCode(),
                                inferType(param.getCode(), value.getValue()),
                                value.getValue())))
                .distinct()
                .toList();
    }

    protected static DataOperatorRuleDto equalRule(CandidateRule candidate) {
        return DataOperatorRuleDto.builder()
                .dataType(candidate.dataType())
                .parameterCode(candidate.parameterCode())
                .operatorCode("equal")
                .values(List.of(candidate.value()))
                .build();
    }

    protected static DataOperatorRuleDto candidateRule(CandidateRule candidate, String operatorCode, String... values) {
        return DataOperatorRuleDto.builder()
                .dataType(candidate.dataType())
                .parameterCode(candidate.parameterCode())
                .operatorCode(operatorCode)
                .values(values == null ? null : List.of(values))
                .build();
    }

    protected static BigDecimal numericValue(CandidateRule candidate) {
        return new BigDecimal(candidate.value());
    }

    private static String inferType(String parameterCode, String value) {
        String known = KNOWN_TYPES.get(parameterCode);
        if (known != null) {
            return known;
        }
        return isNumber(value) ? "NUMBER" : "STRING";
    }

    private static boolean isNumber(String value) {
        try {
            new BigDecimal(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Map<String, String> knownTypes() {
        Map<String, String> result = new LinkedHashMap<>();
        List.of("splitId", "cjId", "needId", "modelId", "channelId", "templateId", "configCommId",
                        "modelTemplate", "channel")
                .forEach(code -> result.put(code, "INTEGER"));
        List.of("cjName", "productCrmId", "segmentName", "sellingProductId", "modelSource", "productName")
                .forEach(code -> result.put(code, "STRING"));
        return Map.copyOf(result);
    }

    protected record CandidateRule(String parameterCode, String dataType, String value) {
        @Override
        public String toString() {
            return parameterCode + "(" + dataType + ")=" + value;
        }
    }
}
