import com.fasterxml.jackson.databind.JsonNode;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.binary.BinaryObjectBuilder;
import org.apache.ignite.binary.BinaryType;
import org.apache.ignite.client.IgniteClient;
import java.util.*;

/** Fixed storage contract from EXPLAB-2974. No application classes or service JAR are loaded. */
final class LinksCacheSchema {
    static final String CONTRACT = "explab-2974-storage-v1";
    static final String PARAMS = "explab.dataoperator.model.ParamDictCacheEntry";
    static final String PARAM = "explab.dataoperator.client.dictionary.dto.ParamDto";
    static final String SOURCE = "explab.dataoperator.model.DataSourceDictCacheEntry";
    static final String TYPE = "explab.dataoperator.enums.DataTypeParam";
    static final String SOURCE_TYPE = "explab.dataoperator.enums.SourceType";
    static final String UPDATE_TYPE = "explab.dataoperator.enums.DataUpdateType";
    private final IgniteClient client;

    LinksCacheSchema(IgniteClient client) {
        this.client = client;
        fields(PARAMS, "params:Object splittingPoint:String");
        fields(PARAM, "code:String desc:String display_style:String key:boolean name:String order:int paramPath:String "
                + "parentKey:boolean parentOrder:int type:Enum updateParamPath:String valueType:String");
        fields(SOURCE, "actionDeleteParam:String actionDeleteValue:String dataRequestURL:String dataSchema:String "
                + "dataUpdateCheckURL:String dataUpdateType:Enum demandActualPeriod:long id:long refillPeriod:long "
                + "refillTopic:String schedulePeriod:long sourceType:Enum splittingPoint:String universalClass:boolean updateDataSchema:String updateTopic:String");
        enumeration(TYPE, "INTEGER LONG NUMBER ENUM STRING DATE DATETIME BOOLEAN ARRAY OBJECT");
        enumeration(SOURCE_TYPE, "KAFKA REST");
        enumeration(UPDATE_TYPE, "ON_DEMAND ON_SCHEDULE ON_OPDATE_CHECK ON_UPDATE");
        // Empty, newly initialized object caches may not have registered their key types yet.
        optionalFields("explab.dataoperator.model.FlatKey", "id:String parentId:String splittingPoint:String");
        optionalFields("explab.dataoperator.model.FlatKeyPath", "id:String parentId:String path:String splittingPoint:String");
    }

    private void optionalFields(String type, String schema) {
        if (client.binary().type(type) != null) fields(type, schema);
    }

    private BinaryType required(String name) {
        BinaryType type = client.binary().type(name);
        if (type == null) throw new IllegalStateException("Storage contract " + CONTRACT + ": missing binary type " + name
                + ". Initialize the real service dictionaries or use a fixture client matching this storage contract.");
        return type;
    }

    private void fields(String name, String schema) {
        BinaryType type = required(name);
        Map<String, String> expected = new TreeMap<>();
        for (String pair : schema.split(" ")) { String[] parts = pair.split(":"); expected.put(parts[0], parts[1]); }
        Map<String, String> actual = new TreeMap<>();
        for (String field : type.fieldNames()) actual.put(field, type.fieldTypeName(field));
        if (!expected.equals(actual)) throw new IllegalStateException("Storage contract changed for " + name
                + "; expected fields=" + expected + ", actual=" + actual + ". No fixture rows have been written.");
    }

    private void enumeration(String name, String values) {
        BinaryType type = required(name);
        if (!type.isEnum()) throw new IllegalStateException("Expected enum binary type " + name);
        Map<String, Integer> actual = new HashMap<>();
        for (BinaryObject value : type.enumValues()) actual.put(value.enumName(), value.enumOrdinal());
        String[] expected = values.split(" ");
        for (int index = 0; index < expected.length; index++)
            if (!Objects.equals(index, actual.get(expected[index])))
                throw new IllegalStateException("Storage enum contract changed: " + name + "." + expected[index]);
    }

    private BinaryObjectBuilder builder(String type) { return client.binary().builder(type); }

    private static void string(BinaryObjectBuilder builder, JsonNode json, String field) {
        JsonNode value = json.get(field);
        builder.setField(field, value == null || value.isNull() ? null : value.asText(), String.class);
    }

    private static void number(BinaryObjectBuilder builder, JsonNode json, String field, boolean wide) {
        JsonNode value = json.get(field);
        if (wide) builder.setField(field, value == null || value.isNull() ? null : value.longValue(), Long.class);
        else builder.setField(field, value == null || value.isNull() ? null : value.intValue(), Integer.class);
    }

    private static void bool(BinaryObjectBuilder builder, JsonNode json, String field) {
        JsonNode value = json.get(field);
        builder.setField(field, value == null || value.isNull() ? null : value.booleanValue(), Boolean.class);
    }

    private enum NullEnum { }

    private void enumeration(BinaryObjectBuilder builder, JsonNode json, String field, String type) {
        JsonNode value = json.get(field);
        Object binary = value == null || value.isNull() ? null : client.binary().buildEnum(type, value.asText());
        if(binary==null)builder.setField(field, null, NullEnum.class);
        else builder.setField(field, binary);
    }

    BinaryObject parameters(JsonNode row) {
        List<BinaryObject> params = new ArrayList<>();
        for (JsonNode param : row.path("params")) {
            BinaryObjectBuilder item = builder(PARAM);
            for (String field : List.of("code", "desc", "display_style", "name", "paramPath", "updateParamPath", "valueType")) string(item, param, field);
            number(item, param, "order", false); number(item, param, "parentOrder", false);
            bool(item, param, "key"); bool(item, param, "parentKey"); enumeration(item, param, "type", TYPE);
            params.add(item.build());
        }
        return builder(PARAMS).setField("splittingPoint", row.path("splittingPoint").asText(), String.class)
                .setField("params", params, Object.class).build();
    }

    BinaryObject source(JsonNode row) {
        BinaryObjectBuilder item = builder(SOURCE);
        for (String field : List.of("actionDeleteParam", "actionDeleteValue", "dataRequestURL", "dataSchema", "dataUpdateCheckURL",
                "refillTopic", "splittingPoint", "updateDataSchema", "updateTopic")) string(item, row, field);
        for (String field : List.of("demandActualPeriod", "id", "refillPeriod", "schedulePeriod")) number(item, row, field, true);
        bool(item, row, "universalClass"); enumeration(item, row, "sourceType", SOURCE_TYPE); enumeration(item, row, "dataUpdateType", UPDATE_TYPE);
        return item.build();
    }
}
