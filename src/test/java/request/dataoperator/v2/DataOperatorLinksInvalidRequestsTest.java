package request.dataoperator.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dto.dataoperator.v2.SplittingObjectsLinksRequestDto;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DataOperatorLinksInvalidRequestsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void absentAndExplicitNullStayDifferentOnEveryNestedLevel() throws Exception {
        for (String id : List.of("SL-35", "SL-37", "SL-38")) {
            var variants = DataOperatorLinksInvalidRequests.forScenario(id);
            for (int i = 0; i < variants.size(); i += 2) {
                String path = variants.get(i).variant().split(" ")[0];
                assertTrue(MAPPER.readTree(variants.get(i).body()).at(path).isMissingNode());
                assertTrue(MAPPER.readTree(variants.get(i + 1).body()).at(path).isNull());
            }
        }
    }

    @Test
    void overflowValuesAreTransmittedExactlyAsJsonIntegers() throws Exception {
        var overflow = DataOperatorLinksInvalidRequests.forScenario("SL-41").get(0);
        var id = MAPPER.readTree(overflow.body()).at("/exps/0/expId");
        assertTrue(id.isIntegralNumber());
        assertEquals(new BigInteger("9223372036854775808"), id.bigIntegerValue());
        assertFalse(id.canConvertToLong());
    }

    @Test
    void mutationsAreIndependentAndEveryVariantIsUniquelyTraceable() {
        var first = DataOperatorLinksInvalidRequests.all();
        var second = DataOperatorLinksInvalidRequests.all();
        assertEquals(first, second);
        assertEquals(first.size(), first.stream().map(Object::toString).distinct().count());
        assertEquals(Set.of("SL-35", "SL-36", "SL-37", "SL-38", "SL-39", "SL-40", "SL-41", "SL-42", "SL-43", "SL-45"),
                first.stream().map(DataOperatorLinksInvalidRequests.InvalidRequest::scenario).collect(Collectors.toSet()));
        ObjectNode tree = DataOperatorLinksTestDataFactory.baselineTree("SP1");
        tree.remove("exps");
        assertTrue(DataOperatorLinksTestDataFactory.baselineTree("SP1").path("exps").isArray());
    }

    @Test
    void absentHttpBodyAndJsonNullAreSeparateCases() {
        var bodies = DataOperatorLinksInvalidRequests.forScenario("SL-45");
        assertEquals(1, bodies.stream().filter(DataOperatorLinksInvalidRequests.InvalidRequest::withoutBody).count());
        assertTrue(bodies.stream().anyMatch(body -> !body.withoutBody() && "null".equals(body.body())));
    }

    @Test
    void validDtoKeepsWorkingFieldNamesEmptyArraysAndInt64Precision() throws Exception {
        var dto = DataOperatorLinksTestDataFactory.request("SP1",
                DataOperatorLinksTestDataFactory.experiment(Long.MAX_VALUE));
        var tree = MAPPER.readTree(SplittingObjectsLinksRequestDto.toJson(dto));
        assertEquals(Long.MAX_VALUE, tree.at("/exps/0/expId").longValue());
        assertTrue(tree.at("/exps/0/objectsSelectConditions").isArray());
        assertTrue(tree.at("/exps/0/objectsSelectConditions").isEmpty());
        assertTrue(tree.at("/exps/0/objectSelectConditions").isMissingNode());
        assertTrue(tree.path("objectIds").isMissingNode());
    }
}
