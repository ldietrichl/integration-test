package request.dataoperator.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import util.dataoperator.DataOperatorLinksAssertions.Pair;
import util.dataoperator.DataOperatorLinksAssertions.Selection;
import java.util.*;

/** Expected pairs are explicit values from endpoint-v7 D1, never calculated by the service. */
public final class DataOperatorLinksFunctionalCases {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long E1 = 101321L, E2 = 101322L;
    public enum DataSet { D1, STRING_ID, STRING_PARENT, NO_OBJECT_KEY, NULL_OBJECT_TYPE, NO_PARENT_KEY }

    public record Scenario(String id, String name, DataSet dataSet, String requestJson,
                           String objectType, String parentType, Map<Long, Set<Integer>> conditions,
                           Map<Selection, Set<Pair>> expected) {
        public ObjectNode request(String point) {
            try {
                ObjectNode node = (ObjectNode) JSON.readTree(requestJson);
                node.put("splittingPointCode", point);
                return node;
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }
        @Override public String toString() { return id + ": " + name; }
    }
    private DataOperatorLinksFunctionalCases() { }

    public static List<Scenario> all() {
        List<Scenario> out = new ArrayList<>();
        add(out,"SL-01","Базовый расчёт; SP2 исключена",base(),"ABD");
        var multi = base();
        multi.withArray("exps").add(experiment(E2, condition(1, rule("STRING","productName","equal","Вклад"))));
        add(out,"SL-02","Два эксперимента с одинаковым номером условия",multi, Map.of(E1,Map.of(1,"ABD"),E2,Map.of(1,"C")));
        var several = base();
        ((ObjectNode) several.at("/exps/0")).withArray("objectsSelectConditions")
                .add(condition(7,rule("INTEGER","channelId","equal","20")));
        add(out,"SL-03","Раздельные условия 1 и 7",several,Map.of(E1,Map.of(1,"ABD",7,"BC")));
        add(out,"SL-04","Нет совпадений; эксперимент и условие сохранены",withRule("STRING","productName","equal","НЕСУЩЕСТВУЮЩИЙ_ПРОДУКТ"),"");
        var mixed = base();
        var experiments = mixed.putArray("exps");
        for (long id : new long[]{E1,E2}) {
            var exp = experiment(id,condition(1,rule("STRING","productName","equal","Кредит")));
            exp.withArray("objectsSelectConditions").add(condition(2,rule("STRING","productName","equal","НЕСУЩЕСТВУЮЩИЙ_ПРОДУКТ")));
            experiments.add(exp);
        }
        add(out,"SL-05","Пустое и непустое условия в двух экспериментах",mixed,
                Map.of(E1,Map.of(1,"ABD",2,""),E2,Map.of(1,"ABD",2,"")));
        var empty = base();
        ((ObjectNode) empty.at("/exps/0")).putArray("objectsSelectConditions");
        add(out,"SL-06","Один эксперимент с пустыми условиями",empty,Map.of(E1,Map.of()));
        empty.withArray("exps").add(experiment(E2,condition(1,rule("STRING","productName","equal","Кредит"))));
        add(out,"SL-06","Пустые условия вместе с непустым экспериментом",empty,Map.of(E1,Map.of(),E2,Map.of(1,"ABD")));
        add(out,"SL-07","Две группы родителей, полные множества ids",base(),"ABD");
        var union = base();
        var orGroups = ((ObjectNode) union.at("/exps/0/objectsSelectConditions/0")).withArray("rules");
        orGroups.addArray().add(rule("INTEGER","channelId","equal","10"));
        add(out,"SL-08","Пересекающиеся OR-группы без дублей",union,"ABDF");
        orGroups.add(orGroups.get(0).deepCopy());
        add(out,"SL-08","Повтор OR-группы без дублей",union,"ABDF");
        var sameId = base(); sameId.putArray("objectIds").add("101");
        add(out,"SL-09","Один id у двух родителей",sameId,"AD");
        add(out,"SL-10","Объект без parentId",withRule("STRING","productName","equal","Страхование"),"E");
        custom(out,"SL-11","Строковые ids 001, 1 и CJ-01",DataSet.STRING_ID,base(),"STRING","INTEGER",
                Set.of(new Pair("1001","001"),new Pair("1001","1"),new Pair("1002","CJ-01")));
        custom(out,"SL-11","Строковые родители 001 и 1",DataSet.STRING_PARENT,base(),"INTEGER","STRING",
                Set.of(new Pair("001","101"),new Pair("1","102")));
        for (DataSet data : List.of(DataSet.NO_OBJECT_KEY,DataSet.NULL_OBJECT_TYPE))
            out.add(new Scenario("SL-12",data == DataSet.NO_OBJECT_KEY ? "Нет key=true" : "Тип key=null",
                    data,base().toString(),null,null,Map.of(),Map.of()));
        custom(out,"SL-13","Нет родительского ключа",DataSet.NO_PARENT_KEY,
                withRule("STRING","productName","equal","Страхование"),"INTEGER",null,Set.of(new Pair(null,"104")));
        var and = base();
        ((com.fasterxml.jackson.databind.node.ArrayNode) and.at("/exps/0/objectsSelectConditions/0/rules/0"))
                .add(rule("INTEGER","channelId","more","10"));
        add(out,"SL-16","AND: Кредит и channelId > 10",and,"BD");
        var or = base();
        ((com.fasterxml.jackson.databind.node.ArrayNode) or.at("/exps/0/objectsSelectConditions/0/rules"))
                .addArray().add(rule("INTEGER","channelId","equal","20"));
        add(out,"SL-17","OR: Кредит или channelId=20",or,"ABCD");
        var nested = base();
        var groups = ((ObjectNode) nested.at("/exps/0/objectsSelectConditions/0")).putArray("rules");
        for (String product : List.of("Кредит","Вклад")) {
            groups.addArray().add(rule("STRING","productName","equal",product)).add(rule("INTEGER","channelId","equal","20"));
        }
        add(out,"SL-18","OR из двух AND-групп",nested,"BC");
        var contradiction = withRule("INTEGER","channelId","equal","10");
        ((com.fasterxml.jackson.databind.node.ArrayNode) contradiction.at("/exps/0/objectsSelectConditions/0/rules/0"))
                .add(rule("INTEGER","channelId","equal","20"));
        add(out,"SL-19","Несовместимые условия AND дают пустой результат",contradiction,"");
        String[] comparisons={"more","less","more_equal","less_equal"};
        String[] integerResults={"DE","AF","BCDE","ABCF"};
        for (int i=0;i<comparisons.length;i++) add(out,"SL-20","INTEGER "+comparisons[i]+" 20",
                withRule("INTEGER","channelId",comparisons[i],"20"),integerResults[i]);
        String[] numericOps={"equal","not_equal","more","less","more_equal","less_equal"};
        String[] numberResults={"B","ACDEF","CD","AEF","BCD","ABEF"};
        for (int i=0;i<numericOps.length;i++) add(out,"SL-21","NUMBER "+numericOps[i]+" 10.0",
                withRule("NUMBER","amount",numericOps[i],"10.0"),numberResults[i]);
        add(out,"SL-21","NUMBER: отрицательное значение",withRule("NUMBER","amount","less","0"),"F");
        add(out,"SL-22","STRING equal всей строки",base(),"ABD");
        add(out,"SL-22","STRING not_equal всей строки",withRule("STRING","productName","not_equal","Кредит"),"CEF");
        add(out,"SL-25","BOOLEAN equal true",withRule("BOOLEAN","enabled","equal","true"),"ABE");
        add(out,"SL-25","BOOLEAN equal false",withRule("BOOLEAN","enabled","equal","false"),"CDF");
        add(out,"SL-25","BOOLEAN not_equal true",withRule("BOOLEAN","enabled","not_equal","true"),"CDF");
        add(out,"SL-26","IN 10,20",withRule("INTEGER","channelId","in","10","20"),"ABCF");
        add(out,"SL-26","NOT IN 10,20",withRule("INTEGER","channelId","not_in","10","20"),"DE");
        add(out,"SL-26","IN с повтором значения",withRule("INTEGER","channelId","in","10","20","10"),"ABCF");
        for (String op : List.of("is_null","is_not_null")) for (String form : List.of("absent","null","empty")) {
            var unary = withRule("STRING","note",op);
            var r=(ObjectNode) unary.at("/exps/0/objectsSelectConditions/0/rules/0/0");
            if (form.equals("absent")) r.remove("values");
            if (form.equals("null")) r.putNull("values");
            add(out,"SL-27",op+": values="+form,unary,op.equals("is_null")?"CE":"ABDF");
        }
        var objectFilter=base();objectFilter.putArray("objectIds").add("102").add("103");
        add(out,"SL-29","objectIds пересекается с правилом",objectFilter,"B");
        var parentFilter=base();parentFilter.putArray("parentObjectIds").add("1002");
        add(out,"SL-30","parentObjectIds пересекается с правилом",parentFilter,"D");
        var both=base();both.putArray("objectIds").add("101");both.putArray("parentObjectIds").add("1001");
        add(out,"SL-31","Оба фильтра: совпадение",both,"A");
        both.putArray("objectIds").add("102");both.putArray("parentObjectIds").add("1002");
        add(out,"SL-31","Оба фильтра: несовместимые ограничения",both,"");
        for(String of:List.of("absent","null","empty"))for(String pf:List.of("absent","null","empty")){
            var filters=base();filter(filters,"objectIds",of);filter(filters,"parentObjectIds",pf);
            add(out,"SL-32","objectIds="+of+", parentObjectIds="+pf,filters,"ABD");
        }
        var onlyParent=base();onlyParent.putArray("objectIds");onlyParent.putArray("parentObjectIds").add("1002");
        add(out,"SL-32","Пустой objectIds и непустой parentObjectIds",onlyParent,"D");
        var onlyObject=base();onlyObject.putArray("parentObjectIds");onlyObject.putArray("objectIds").add("102");
        add(out,"SL-32","Непустой objectIds и пустой parentObjectIds",onlyObject,"B");
        for(String field:List.of("objectIds","parentObjectIds")){
            var repeated=base();String known=field.equals("objectIds")?"102":"1002";
            repeated.putArray(field).add(known).add(known).add("99999");
            add(out,"SL-33",field+": повторы и неизвестное значение",repeated,field.equals("objectIds")?"B":"D");
            repeated.putArray(field).add("99999");
            add(out,"SL-33",field+": только неизвестное значение",repeated,"");
        }
        var noParent=withRule("STRING","productName","equal","Страхование");
        add(out,"SL-34","Объект без родителя: контроль без фильтра",noParent,"E");
        noParent.putArray("parentObjectIds").add("1001");
        add(out,"SL-34","Родительский фильтр исключает объект без родителя",noParent,"");
        for(long id:new long[]{2147483648L,Long.MAX_VALUE}){
            var large=base();((ObjectNode)large.at("/exps/0")).put("expId",id);
            add(out,"SL-41","Точный expId="+id,large,Map.of(id,Map.of(1,"ABD")));
        }
        var maxNumber=base();((ObjectNode)maxNumber.at("/exps/0/objectsSelectConditions/0")).put("number",32767);
        add(out,"SL-42","Точная верхняя граница number=32767",maxNumber,Map.of(E1,Map.of(32767,"ABD")));
        if(out.stream().map(Object::toString).distinct().count()!=out.size()) throw new IllegalStateException("Duplicate scenario names");
        return List.copyOf(out);
    }

    private static ObjectNode base(){return DataOperatorLinksTestDataFactory.baselineTree("__SP1__");}
    private static ObjectNode rule(String type,String code,String op,String...values){
        var r=JSON.createObjectNode().put("dataType",type).put("parameterCode",code).put("operatorCode",op);
        var a=r.putArray("values");for(String value:values)a.add(value);return r;
    }
    private static ObjectNode condition(int number,ObjectNode rule){
        var c=JSON.createObjectNode().put("number",number);c.putArray("rules").addArray().add(rule);return c;
    }
    private static ObjectNode experiment(long id,ObjectNode condition){
        var e=JSON.createObjectNode().put("expId",id);e.putArray("objectsSelectConditions").add(condition);return e;
    }
    private static ObjectNode withRule(String type,String code,String op,String...values){
        var b=base();((ObjectNode)b.at("/exps/0/objectsSelectConditions/0")).putArray("rules").addArray().add(rule(type,code,op,values));return b;
    }
    private static void filter(ObjectNode body,String key,String form){
        if(form.equals("null"))body.putNull(key);if(form.equals("empty"))body.putArray(key);
    }
    private static Set<Pair> pairs(String labels){
        Map<Character,Pair> rows=Map.of('A',new Pair("1001","101"),'B',new Pair("1001","102"),
                'C',new Pair("1002","103"),'D',new Pair("1002","101"),'E',new Pair(null,"104"),'F',new Pair("1002","105"));
        Set<Pair> result=new LinkedHashSet<>();for(char label:labels.toCharArray())result.add(Objects.requireNonNull(rows.get(label)));return Set.copyOf(result);
    }
    private static void add(List<Scenario> out,String id,String name,ObjectNode body,String labels){add(out,id,name,body,Map.of(E1,Map.of(1,labels)));}
    private static void add(List<Scenario> out,String id,String name,ObjectNode body,Map<Long,Map<Integer,String>> sets){
        Map<Long,Set<Integer>> conditions=new LinkedHashMap<>();Map<Selection,Set<Pair>> expected=new LinkedHashMap<>();
        sets.forEach((exp,cs)->{conditions.put(exp,Set.copyOf(cs.keySet()));cs.forEach((number,labels)->expected.put(new Selection(exp,number),pairs(labels)));});
        out.add(new Scenario(id,name,DataSet.D1,body.toString(),"INTEGER","INTEGER",Map.copyOf(conditions),Map.copyOf(expected)));
    }
    private static void custom(List<Scenario> out,String id,String name,DataSet data,ObjectNode body,String objectType,String parentType,Set<Pair> expected){
        out.add(new Scenario(id,name,data,body.toString(),objectType,parentType,Map.of(E1,Set.of(1)),Map.of(new Selection(E1,1),expected)));
    }
}
