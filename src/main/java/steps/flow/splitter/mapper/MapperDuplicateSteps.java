package steps.flow.splitter.mapper;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperDuplicateSteps extends MapperPrecalcSteps {

    public static ObjectNode incidentObject() {
        ObjectNode object = JSON.createObjectNode().put("uniqueConfigurationId", "1646160");
        var values = object.putArray("objectParams");
        String[][] params = {{"cjId","105901421","INTEGER"},{"bbCjId","123456","INTEGER"},
                {"needCode","refuel","STRING"},{"modelId","16421","INTEGER"},
                {"modelTemplate","2","INTEGER"},{"modelSource","PIM","STRING"},
                {"sellingProductId","9-XRJSFRQX","STRING"},{"channel","50","INTEGER"},
                {"templateId","232673821","STRING"},{"configCommId","1646160","INTEGER"}};
        for (String[] p : params) values.add(param(p[0], p[1]).put("dataType", p[2]));
        return object;
    }
}
