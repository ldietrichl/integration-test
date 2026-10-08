package request.dataoperator.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.dataoperator.v2.SplittingObjectRuleDto;
import dto.dataoperator.v2.SplittingObjectsLinksRequestDto;

import java.util.List;

/** Request-only data: the links endpoint does not create experiments or objects. */
public final class DataOperatorLinksTestDataFactory {
    public static final long E1 = 101321L;
    public static final long E2 = 101322L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataOperatorLinksTestDataFactory() {
    }

    public static SplittingObjectsLinksRequestDto request(
            String point, SplittingObjectsLinksRequestDto.Experiment... experiments) {
        return SplittingObjectsLinksRequestDto.builder()
                .splittingPointCode(point).exps(List.of(experiments)).build();
    }

    public static SplittingObjectsLinksRequestDto.Experiment experiment(
            long expId, SplittingObjectsLinksRequestDto.Condition... conditions) {
        return SplittingObjectsLinksRequestDto.Experiment.builder()
                .expId(expId).objectsSelectConditions(List.of(conditions)).build();
    }

    public static SplittingObjectsLinksRequestDto.Condition condition(
            int number, List<List<SplittingObjectRuleDto>> rules) {
        return SplittingObjectsLinksRequestDto.Condition.builder().number(number).rules(rules).build();
    }

    public static SplittingObjectsLinksRequestDto baseline(String point) {
        return request(point, experiment(E1, condition(1, List.of(List.of(
                DataOperatorV2TestDataFactory.rule("STRING", "productName", "equal", List.of("Кредит")))))));
    }

    /** Tree mutations preserve explicit nulls and wrong JSON types in negative requests. */
    public static ObjectNode baselineTree(String point) {
        return MAPPER.valueToTree(baseline(point));
    }
}
