package dto.splitter.precalc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Mapper preliminary calculation request fixtures. */
public final class MapperPrecalcRequests {
    private MapperPrecalcRequests() { }

    public static final ObjectMapper JSON = new ObjectMapper();
    public static final String SALT = "ICP4GROUPD";
    public static final String S_B = "1129464980047006855";
    /** Preserve the same object keys/parameters used by the normal split DTO. */
    public static ObjectNode fromSplit(int version, dto.splitter.split.SplitRequestDto request) {
        ObjectNode result = JSON.createObjectNode().put("requestId", UUID.randomUUID().toString())
                .put("soConfigVersion", version);
        var objects = result.putArray("splittingObjects");
        request.getSplittingObjects().forEach(object -> {
            var node = objects.addObject().put("uniqueConfigurationId", object.getUniqueConfigurationId());
            node.set("objectParams", JSON.valueToTree(object.getObjectParams()));
        });
        return result;
    }
    public static ObjectNode param(String code, String value) {
        ObjectNode p = JSON.createObjectNode().put("paramCode", code).put("dataType", "STRING");
        p.putArray("paramValues").add(value);
        return p;
    }
    public static ObjectNode condition(int id, String value) {
        ObjectNode c = JSON.createObjectNode().put("id", id);
        ObjectNode rule = JSON.createObjectNode().put("dataType", "STRING").put("paramCode", "segment")
                .put("paramSource", "SPLITTING_OBJECTS").put("operatorCode", "equal");
        rule.putArray("values").add(value);
        c.putArray("rules").addArray().add(rule);
        return c;
    }
    public static ObjectNode group(String code, int from, int to, int condition, String marker) {
        ObjectNode g = JSON.createObjectNode().put("code", code);
        g.putArray("shares").addObject().put("shareFrom", from).put("shareTo", to);
        ObjectNode result = g.putArray("splittingResults").addObject().put("conditionId", condition);
        result.putArray("resultParams").add(param("marker", marker))
                .add(param("actionType", "0").put("dataType", "INTEGER"));
        return g;
    }
    public static ObjectNode exp(int id, int priority) {
        ObjectNode e = JSON.createObjectNode().put("id", id).put("purpose", "DCG").put("salt", SALT)
                .put("layerId", id).put("layerPriority", priority);
        e.putArray("objectSelectConditions").add(condition(10, "gold"));
        e.putArray("groups").add(group("A", 0, 10000, 10, id + "-A-10"));
        return e;
    }
    public static ObjectNode config(long v, ObjectNode... experiments) {
        ObjectNode c = JSON.createObjectNode().put("requestId", UUID.randomUUID().toString())
                .put("messageId", UUID.randomUUID().toString()).put("configVersion", v)
                .put("forceConfigLoad", true).put("splittingPointCode", "MAPPER");
        ArrayNode es = c.putObject("splittingConfig").putArray("experiments");
        Arrays.stream(experiments).forEach(es::add);
        return c;
    }
    public static ObjectNode request(String splittingId, ObjectNode... objects) {
        ObjectNode r = JSON.createObjectNode().put("requestId", UUID.randomUUID().toString())
                .put("splittingId", splittingId);
        r.putArray("requestParams");
        ArrayNode os = r.putArray("splittingObjects");
        Arrays.stream(objects).forEach(os::add);
        return r;
    }
}
