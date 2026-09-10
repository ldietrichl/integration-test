package request.dataoperator.v2;

import org.junit.jupiter.api.Test;
import util.dataoperator.LinksFixtureData;
import util.dataoperator.DataOperatorLinksAssertions.Pair;
import util.dataoperator.DataOperatorLinksAssertions.Selection;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static request.dataoperator.v2.DataOperatorLinksFunctionalCases.DataSet;

class DataOperatorLinksFunctionalCasesTest {
    @Test void everyVariantHasAnIndependentRequestAndUniqueName(){
        var cases=DataOperatorLinksFunctionalCases.all();
        assertEquals(cases.size(),cases.stream().map(Object::toString).distinct().count());
        var one=cases.get(0);one.request("SP-A").put("exps","damaged");
        assertTrue(one.request("SP-B").path("exps").isArray());
        assertEquals("SP-B",one.request("SP-B").path("splittingPointCode").asText());
    }
    @Test void andOrAndFiltersHaveExplicitDifferentExpectedSets(){
        var cases=DataOperatorLinksFunctionalCases.all();
        var and=cases.stream().filter(c->c.id().equals("SL-16")).findFirst().orElseThrow();
        assertEquals(Set.of(new Pair("1001","102"),new Pair("1002","101")),and.expected().get(new Selection(101321,1)));
        var or=cases.stream().filter(c->c.id().equals("SL-17")).findFirst().orElseThrow();
        assertEquals(4,or.expected().get(new Selection(101321,1)).size());
        var filtered=cases.stream().filter(c->c.id().equals("SL-29")).findFirst().orElseThrow();
        assertEquals(Set.of(new Pair("1001","102")),filtered.expected().get(new Selection(101321,1)));
    }
    @Test void emptyConditionsRetainExperimentAndLongIdsRemainExact(){
        var cases=DataOperatorLinksFunctionalCases.all();
        var empty=cases.stream().filter(c->c.id().equals("SL-06")).findFirst().orElseThrow();
        assertEquals(Set.of(),empty.conditions().get(101321L));
        var max=cases.stream().filter(c->c.id().equals("SL-41")&&c.name().contains("9223372036854775807")).findFirst().orElseThrow();
        assertEquals(Long.MAX_VALUE,max.request("P").at("/exps/0/expId").longValue());
        assertEquals(Set.of(1),max.conditions().get(Long.MAX_VALUE));
    }
    @Test void allNineOptionalFilterCombinationsAreDistinct(){
        var requests=DataOperatorLinksFunctionalCases.all().stream().filter(c->c.id().equals("SL-32"))
                .map(c->c.request("P").toString()).collect(Collectors.toSet());
        assertEquals(11,requests.size());
    }
    @Test void fixtureOwnsTwoPointsAndPreservesD1MissingVersusEmptyValues(){
        var fixture=LinksFixtureData.create(DataSet.D1,"0123456789abcdef0123456789abcdef");
        var objects=fixture.path("expectedObjects");
        assertEquals(7,objects.size());
        assertEquals(46,fixture.path("expectedCounts").path("splitting_field_cache").asInt());
        assertTrue(objects.get(1).path("fields").has("note"));
        assertEquals("",objects.get(1).path("fields").path("note").asText());
        assertFalse(objects.get(2).path("fields").has("note"));
        assertFalse(objects.get(4).has("parentId"));
        assertNotEquals(objects.get(0).path("splittingPoint"),objects.get(6).path("splittingPoint"));
        assertThrows(IllegalArgumentException.class,()->LinksFixtureData.create(DataSet.D1,"MAPPER"));
    }
    @Test void stringIdsStayDistinctAndMissingKeyCasesDoNotLoadInvalidObjects(){
        var fixture=LinksFixtureData.create(DataSet.STRING_ID,"0123456789abcdef0123456789abcdef");
        assertEquals("001",fixture.path("expectedObjects").get(0).path("id").asText());
        assertEquals("1",fixture.path("expectedObjects").get(1).path("id").asText());
        assertEquals("STRING",fixture.path("params").get(0).path("params").get(0).path("type").asText());
        for(var kind:Set.of(DataSet.NO_OBJECT_KEY,DataSet.NULL_OBJECT_TYPE)){
            var metadataOnly=LinksFixtureData.create(kind,"0123456789abcdef0123456789abcdef");
            assertTrue(metadataOnly.path("payloads").isEmpty());
            assertEquals(0,metadataOnly.path("expectedCounts").path("splitting_object_cache").asInt());
        }
    }
}
