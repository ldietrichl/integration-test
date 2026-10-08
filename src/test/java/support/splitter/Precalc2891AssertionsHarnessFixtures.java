package support.splitter;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;

import util.splittercheck.PrecalcResponseAssertions;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static steps.flow.splitter.mapper.MapperPrecalcSteps.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2891AssertionsHarnessFixtures {
    public static final String UUID_TEXT = "e0dece08-4f5f-4f08-bd91-431e1c050720";
    public static ObjectNode preRequest() { return JSON.createObjectNode().put("soConfigVersion", 101); }
    public static ObjectNode precalc() {
        ObjectNode r = JSON.createObjectNode().put("responseId", UUID_TEXT).put("soConfigVersion", 101);
        ObjectNode c = r.putObject("counter");
        for (String field : PrecalcResponseAssertions.COUNTERS) c.put(field, 0);
        return r;
    }
    public static ObjectNode splitRequest() {
        ObjectNode r = JSON.createObjectNode().put("requestId", "request").put("splittingId", S_B);
        r.putArray("splittingObjects").addObject().put("objectId", "O1");
        return r;
    }
    public static ObjectNode splitResponse() {
        ObjectNode r = JSON.createObjectNode().put("requestId", "request").put("splittingId", S_B)
                .put("responseId", UUID_TEXT).put("splittingConfigVersion", 123);
        var rules = r.putArray("splittingResults").addObject().put("objectId", "O1").putArray("objectResults");
        for (String code : List.of("MAIN", "ALL")) {
            var exp = rules.addObject().put("ruleCode", code).putArray("resultExps").addObject()
                    .put("expId", 101).put("conditionId", 10).put("layerId", 101)
                    .put("salt", SALT).put("expGroup", "A").put("finalExpGroup", "A");
            exp.putArray("groupResultParams").add(param("marker", "101-A-10"))
                    .add(param("actionType", "0").put("dataType", "INTEGER"));
        }
        return r;
    }
}
