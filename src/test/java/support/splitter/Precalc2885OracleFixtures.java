package support.splitter;
import static dto.splitter.precalc.ReactionsPrecalcRequests.*;
import static util.splittercheck.ReactionsPrecalcAssertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static steps.flow.splitter.reactions.ReactionsPrecalcSteps.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2885OracleFixtures {
    public static ObjectNode response() {
        ObjectNode root = JSON.createObjectNode();
        ObjectNode object = root.putArray("splittingResults").addObject().put("objectId", "O1");
        ObjectNode e = object.putArray("objectResults").addObject().put("ruleCode", "MAIN")
                .putArray("resultExps").addObject().put("expId", 101).put("expGroup", "A")
                .put("finalExpGroup", "A").put("conditionId", 10).put("salt", SALT).put("layerId", 101);
        e.putArray("groupResultParams").add(param("marker", "101-A-10"));
        return root;
    }
}
