package util.dataoperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.builder.ResponseBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.sber.qa.services.rest.validation.ValidatableResponseWrapper;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static util.dataoperator.DataOperatorLinksAssertions.*;

class DataOperatorLinksAssertionsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SUCCESS = """
            {"objectIdType":"STRING","parentIdType":"INTEGER","links":[
              {"expId":9223372036854775807,"objectSelectConditions":[
                {"number":32767,"splittingObjects":[
                  {"parentId":"1001","ids":["001","1"]},
                  {"parentId":"1002","ids":["001"]},
                  {"ids":["CJ-01"]}]}]},
              {"expId":2,"objectSelectConditions":[]}]}
            """;
    private static final Map<Long, Set<Integer>> CONDITIONS = Map.of(
            Long.MAX_VALUE, Set.of(32767), 2L, Set.of());
    private static final Map<Selection, Set<Pair>> LINKS = Map.of(
            new Selection(Long.MAX_VALUE, 32767), Set.of(new Pair("1001", "001"), new Pair("1001", "1"),
                    new Pair("1002", "001"), new Pair(null, "CJ-01")));

    @Test
    void comparesPairsIncludingSameChildInDifferentParentsWithoutLosingLeadingZeroes() {
        assertDoesNotThrow(() -> shouldHaveLinks(response(200, SUCCESS), "STRING", "INTEGER", CONDITIONS, LINKS));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "duplicatePair", "duplicateParent", "duplicateExperiment", "duplicateCondition", "parentNull",
            "numericId", "stringExpId", "fractionNumber", "largeNumber", "alias", "missingExperiment", "emptyGroup"
    })
    void detectsMutationsThatSetComparisonOrDtoCoercionWouldHide(String mutation) throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(SUCCESS);
        ObjectNode experiment = (ObjectNode) root.at("/links/0");
        ObjectNode condition = (ObjectNode) root.at("/links/0/objectSelectConditions/0");
        ObjectNode group = (ObjectNode) condition.at("/splittingObjects/0");
        switch (mutation) {
            case "duplicatePair" -> group.withArray("ids").add("001");
            case "duplicateParent" -> condition.withArray("splittingObjects").add(group.deepCopy());
            case "duplicateExperiment" -> root.withArray("links").add(experiment.deepCopy());
            case "duplicateCondition" -> experiment.withArray("objectSelectConditions").add(condition.deepCopy());
            case "parentNull" -> ((ObjectNode) condition.at("/splittingObjects/2")).putNull("parentId");
            case "numericId" -> group.withArray("ids").set(0, MAPPER.getNodeFactory().numberNode(1));
            case "stringExpId" -> experiment.put("expId", Long.toString(Long.MAX_VALUE));
            case "fractionNumber" -> condition.put("number", 32767.0);
            case "largeNumber" -> condition.put("number", 65535);
            case "alias" -> condition.set("splittingObjectIds", condition.remove("splittingObjects"));
            case "missingExperiment" -> root.withArray("links").remove(1);
            case "emptyGroup" -> condition.withArray("splittingObjects").addObject().putArray("ids");
            default -> throw new AssertionError(mutation);
        }
        assertThrows(AssertionError.class, () -> shouldHaveLinks(response(200, root.toString()),
                "STRING", "INTEGER", CONDITIONS, LINKS));
    }

    @Test
    void keepsEmptyConditionDifferentFromEmptyRootLinks() {
        String body = """
                {"objectIdType":"INTEGER","parentIdType":"INTEGER","links":[
                 {"expId":1,"objectSelectConditions":[{"number":1,"splittingObjects":[]}]}]}
                """;
        assertDoesNotThrow(() -> shouldHaveLinks(response(200, body), "INTEGER", "INTEGER",
                Map.of(1L, Set.of(1)), Map.of(new Selection(1, 1), Set.of())));
        assertThrows(AssertionError.class, () -> shouldHaveLinks(response(200,
                "{\"objectIdType\":\"INTEGER\",\"parentIdType\":\"INTEGER\",\"links\":[]}"),
                "INTEGER", "INTEGER", Map.of(1L, Set.of(1)), Map.of(new Selection(1, 1), Set.of())));
    }

    @Test
    void acceptsExplicitEarlyNullTypesAndEmptyLinks() {
        assertDoesNotThrow(() -> shouldHaveLinks(response(200,
                "{\"objectIdType\":null,\"parentIdType\":null,\"links\":[]}"),
                null, null, Map.of(), Map.of()));
    }

    @Test
    void errorChecksRealStatusAndStrictUuidRatherThanNumberInsideBody() {
        String body = """
                {"id":"a6520df7-160f-4414-a503-9a13ad0d7a17","message":"Некорректный запрос","params":{}}
                """;
        assertDoesNotThrow(() -> shouldHaveError(response(400, body), 400, "Некорректный запрос"));
        assertThrows(AssertionError.class, () -> shouldHaveError(response(500, body), 400, "Некорректный запрос"));
        assertThrows(AssertionError.class, () -> shouldHaveError(response(400,
                body.replace("a6520df7", "A6520DF7")), 400, "Некорректный запрос"));
        assertThrows(AssertionError.class, () -> shouldHaveError(response(400,
                body.replace("\"params\":{}", "\"params\":null")), 400, "Некорректный запрос"));
    }

    @Test
    void rejectsNumericMessageAndNonJsonContentType() {
        String body = "{\"id\":\"a6520df7-160f-4414-a503-9a13ad0d7a17\",\"message\":123}";
        assertThrows(AssertionError.class, () -> shouldHaveError(response(400, body), 400, null));
        ValidatableResponseWrapper wrapper = mock(ValidatableResponseWrapper.class);
        when(wrapper.toResponse()).thenReturn(new ResponseBuilder().setStatusCode(200)
                .setContentType("text/html").setBody(SUCCESS).build());
        assertThrows(AssertionError.class, () -> shouldHaveLinks(wrapper, "STRING", "INTEGER", CONDITIONS, LINKS));
    }

    private static ValidatableResponseWrapper response(int status, String body) {
        ValidatableResponseWrapper wrapper = mock(ValidatableResponseWrapper.class);
        when(wrapper.toResponse()).thenReturn(new ResponseBuilder().setStatusCode(status)
                .setContentType("application/json; charset=UTF-8").setBody(body).build());
        return wrapper;
    }
}
