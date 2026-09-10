package util.dataoperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import request.dataoperator.v2.DataOperatorLinksFunctionalCases.DataSet;
import java.util.*;

/** Data and metadata for unique test points; contains no response implementation. */
public final class LinksFixtureData {
    private static final ObjectMapper JSON = new ObjectMapper();
    private LinksFixtureData() { }

    public static ObjectNode create(DataSet kind, String lease) {
        if (!lease.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid fixture ownership token");
        var fixture=JSON.createObjectNode().put("leaseId",lease).put("dataSet",kind.name());
        var points=fixture.putObject("points");
        points.put("SP1","EXPLAB2974_"+lease+"_SP1");points.put("SP2","EXPLAB2974_"+lease+"_SP2");
        ArrayNode sp1=JSON.createArrayNode();
        if(kind==DataSet.D1){
            sp1.add(row(1001,101,"Кредит",10,"9.50",true,"A"));
            sp1.add(row(1001,102,"Кредит",20,"10.00",true,""));
            sp1.add(row(1002,103,"Вклад",20,"10.50",false,null));
            sp1.add(row(1002,101,"Кредит",30,"20.00",false,"D"));
            sp1.add(row(null,104,"Страхование",30,"0.00",true,null));
            sp1.add(row(1002,105,"Авто Кредит VIP",10,"-1.00",false,"F"));
        } else if(kind==DataSet.STRING_ID){
            sp1.add(row(1001,"001","Кредит",10,"9.50",true,"A"));
            sp1.add(row(1001,"1","Кредит",20,"10.00",true,"B"));
            sp1.add(row(1002,"CJ-01","Кредит",30,"20.00",false,"D"));
        } else if(kind==DataSet.STRING_PARENT){
            sp1.add(row("001",101,"Кредит",10,"9.50",true,"A"));
            sp1.add(row("1",102,"Кредит",20,"10.00",true,"B"));
        } else if(kind==DataSet.NO_PARENT_KEY) {
            sp1.add(row(null,104,"Страхование",30,"0.00",true,null));
        }
        Map<String,ArrayNode> rows=new LinkedHashMap<>();
        rows.put(points.path("SP1").asText(),sp1);
        var sp2=JSON.createArrayNode();
        if(kind!=DataSet.NO_OBJECT_KEY && kind!=DataSet.NULL_OBJECT_TYPE)
            sp2.add(row(9001,999,"Кредит",20,"10.00",true,"X"));
        rows.put(points.path("SP2").asText(),sp2);
        var params=fixture.putArray("params");var sources=fixture.putArray("archiveSources");
        var payloads=fixture.putObject("payloads");var expectedObjects=fixture.putArray("expectedObjects");
        int objects=0,fields=0,actualization=0;
        for(var entry:rows.entrySet()){
            String point=entry.getKey();boolean primary=point.equals(points.path("SP1").asText());
            Map<String,String> types=new LinkedHashMap<>();
            types.put("id",primary && kind==DataSet.STRING_ID?"STRING":"INTEGER");
            if(!primary || kind!=DataSet.NO_PARENT_KEY) types.put("parentId",primary && kind==DataSet.STRING_PARENT?"STRING":"INTEGER");
            types.put("productName","STRING");types.put("channelId","INTEGER");types.put("amount","NUMBER");types.put("enabled","BOOLEAN");types.put("note","STRING");
            var dictionary=params.addObject().put("splittingPoint",point).putArray("params");
            int order=1;
            for(var field:types.entrySet()){
                var p=dictionary.addObject().put("paramPath","$.objects[]."+field.getKey()).put("code",field.getKey())
                        .put("name",field.getKey()).put("type",field.getValue()).put("order",order++)
                        .put("key",field.getKey().equals("id") && !(primary && kind==DataSet.NO_OBJECT_KEY))
                        .put("parentKey",field.getKey().equals("parentId"));
                if(primary && kind==DataSet.NULL_OBJECT_TYPE && field.getKey().equals("id"))p.putNull("type");
            }
            var schema=JSON.createObjectNode().put("type","object");
            schema.putArray("required").add("objects");
            var item=schema.putObject("properties").putObject("objects").put("type","array").putObject("items").put("type","object");
            item.putArray("required").add("id").add("productName").add("channelId").add("amount").add("enabled");
            var properties=item.putObject("properties");
            types.forEach((code,type)->properties.putObject(code).put("type",switch(type){case "INTEGER"->"integer";case "NUMBER"->"number";case "BOOLEAN"->"boolean";default->"string";}));
            sources.addObject().put("id",Long.parseUnsignedLong(UUID.nameUUIDFromBytes(point.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","").substring(0,15),16))
                    .put("splittingPoint",point).put("sourceType","KAFKA").put("dataUpdateType","ON_UPDATE").put("universalClass",true)
                    .put("refillTopic",point).put("updateTopic",point).put("dataSchema",schema.toString()).put("updateDataSchema",schema.toString());
            if(!entry.getValue().isEmpty()){
                payloads.putObject(point).putObject("cjConfig").set("objects",entry.getValue());actualization++;
            }
            for(var row:entry.getValue()){
                objects++;fields+=row.size();
                var record=expectedObjects.addObject().put("splittingPoint",point).put("id",row.path("id").asText());
                if(row.has("parentId"))record.put("parentId",row.path("parentId").asText());
                record.set("fields",row.deepCopy());
            }
        }
        fixture.putObject("expectedCounts").put("splitting_object_cache",objects).put("splitting_field_cache",fields)
                .put("actualization_cache",actualization).put("param_cache",2).put("data_source_cache",2);
        return fixture;
    }
    private static ObjectNode row(Object parent,Object id,String product,int channel,String amount,boolean enabled,String note){
        var row=JSON.createObjectNode();
        if(parent!=null)row.set("parentId",JSON.valueToTree(parent));row.set("id",JSON.valueToTree(id));
        row.put("productName",product).put("channelId",channel).put("amount",new java.math.BigDecimal(amount)).put("enabled",enabled);
        if(note!=null)row.put("note",note);return row;
    }
}
