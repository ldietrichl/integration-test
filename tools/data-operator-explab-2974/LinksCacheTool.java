import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.client.SslMode;
import org.apache.ignite.configuration.ClientConfiguration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.*;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Real Ignite fallback for owned metadata and cleanup, using an isolated fixed client. */
public class LinksCacheTool {
    static final ObjectMapper JSON=new ObjectMapper();
    static final int SESSION_PROTOCOL_VERSION = 1;
    static final String SESSION_PREFIX = "IGNITE_SESSION=";
    static final List<String> CACHES=List.of("splitting_object_cache","splitting_field_cache","actualization_cache","param_cache","data_source_cache");
    record Key(String point,String parent,String id) { }
    static String owner(Object key){
        if(key instanceof String s)return s;
        return key instanceof BinaryObject b && b.hasField("splittingPoint")?b.field("splittingPoint"):null;
    }
    static String env(String key){return System.getenv("LINKS_"+key.replace('.','_').toUpperCase(Locale.ROOT));}
    static ClientConfiguration configuration(String addresses){
        if(addresses.isBlank())throw new IllegalArgumentException("Ignite addresses missing");
        var c=new ClientConfiguration().setAddresses(Arrays.stream(addresses.split(",")).map(String::trim).toArray(String[]::new))
                .setPartitionAwarenessEnabled(false).setClusterDiscoveryEnabled(false).setTimeout(10000);
        if(env("ignite.username")!=null)c.setUserName(env("ignite.username"));
        if(env("ignite.password")!=null)c.setUserPassword(env("ignite.password"));
        String ssl=env("ignite.ssl.enabled");
        if(!Set.of("true","false").contains(ssl==null?"":ssl))throw new IllegalArgumentException("Explicit SSL mode required");
        c.setSslMode(Boolean.parseBoolean(ssl)?SslMode.REQUIRED:SslMode.DISABLED).setSslTrustAll(false);
        if(Boolean.parseBoolean(ssl))c.setSslContextFactory(LinksCacheTool::sslContext);
        return c;
    }
    static String sslSetting(String name){
        String value=env("ignite.ssl."+name);
        if(value==null || value.isBlank())value=System.getProperty("javax.net.ssl."+name);
        return value==null || value.isBlank()?null:value;
    }
    static KeyStore sslStore(String path,String type,char[] password)throws Exception{
        var store=KeyStore.getInstance(type==null?"JKS":type);
        try(var input=Files.newInputStream(Path.of(path))){store.load(input,password);}
        return store;
    }
    /** Uses initialized JDK managers; no client identity is invented when a keystore is absent. */
    static SSLContext sslContext(){
        String keyPath=sslSetting("keyStore"),trustPath=sslSetting("trustStore");
        String keyPassword=sslSetting("keyStorePassword"),trustPassword=sslSetting("trustStorePassword");
        char[] keyChars=keyPassword==null?new char[0]:keyPassword.toCharArray();
        char[] trustChars=trustPassword==null?new char[0]:trustPassword.toCharArray();
        try{
            KeyManager[] keys=new KeyManager[0];
            if(keyPath!=null){
                var keyFactory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyFactory.init(sslStore(keyPath,sslSetting("keyStoreType"),keyChars),keyChars);
                keys=keyFactory.getKeyManagers();
            }
            var trustFactory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            // A null store selects the JDK's configured/default trust anchors, with normal validation.
            trustFactory.init(trustPath==null?(KeyStore)null:sslStore(trustPath,sslSetting("trustStoreType"),trustChars));
            var context=SSLContext.getInstance("TLS");
            context.init(keys,trustFactory.getTrustManagers(),null);
            System.out.println("FIXTURE_TLS_CONTEXT=initialized; clientKeyStoreConfigured="+(keyPath!=null)
                    +"; trustStore="+(trustPath==null?"jdk-default":"configured")+"; certificateValidation=enabled");
            return context;
        }catch(Exception error){
            throw new IllegalStateException("Fixture TLS context initialization failed; check keystore/truststore settings",error);
        }finally{
            Arrays.fill(keyChars,'\0');Arrays.fill(trustChars,'\0');
        }
    }
    static Map<String,Long> counts(IgniteClient client,Set<String> points,boolean remove){
        return counts(client,points,remove,null);
    }
    static Map<String,Long> counts(IgniteClient client,Set<String> points,boolean remove,ObjectVerification verification){
        Map<String,Long> result=new LinkedHashMap<>();
        for(String name:CACHES){
            var cache=client.cache(name).withKeepBinary();long found=0;
            // DictionaryCacheServiceImpl stores both metadata caches by the String splittingPoint key.
            if(name.equals("param_cache") || name.equals("data_source_cache")){
                for(var row:cache.getAll(points).entrySet()){
                    found++;
                    if(remove && !cache.remove(row.getKey(),row.getValue()))throw new IllegalStateException("Owned row changed concurrently in "+name);
                }
            }else{
                try(var cursor=cache.query(new ScanQuery<>().setPageSize(256))){
                    for(var row:cursor){
                        if(!points.contains(owner(row.getKey())))continue;
                        found++;
                        if(verification!=null && name.equals("splitting_object_cache"))verification.accept(row.getKey(),row.getValue());
                        if(remove && !cache.remove(row.getKey(),row.getValue()))throw new IllegalStateException("Owned row changed concurrently in "+name);
                    }
                }
            }
            result.put(name,found);
        }
        return result;
    }
    static final class ObjectVerification {
        private final Map<Key,JsonNode> expected=new HashMap<>();
        private final Set<Key> actual=new HashSet<>();
        private RuntimeException failure;
        ObjectVerification(JsonNode fixture){
            try{
                for(var row:fixture.path("expectedObjects")){
                    var key=new Key(row.path("splittingPoint").asText(),row.has("parentId")?row.path("parentId").asText():null,row.path("id").asText());
                    if(expected.put(key,row.path("fields"))!=null)throw new IllegalArgumentException("Duplicate fixture key");
                }
            }catch(RuntimeException error){failure=error;}
        }
        void accept(Object rawKey,Object rawValue){
            if(failure!=null)return;
            try{
                BinaryObject binary=(BinaryObject)rawKey;
                var key=new Key(owner(binary),binary.field("parentId"),binary.field("id"));
                JsonNode wanted=expected.get(key);
                if(wanted==null || !actual.add(key))throw new IllegalStateException("Unexpected/duplicate ingested object key");
                if(!(rawValue instanceof Map<?,?> values))throw new IllegalStateException("Expected object map from real ingestion");
                if(values.size()!=wanted.size())throw new IllegalStateException("Ingested field set differs from fixture");
                var fields=wanted.fields();
                while(fields.hasNext()){
                    var field=fields.next();Object value=values.get(field.getKey());JsonNode want=field.getValue();
                    boolean equal=value!=null && (want.isNumber()
                            ?new java.math.BigDecimal(value.toString()).compareTo(want.decimalValue())==0
                            :want.asText().equals(value.toString()));
                    if(!equal)throw new IllegalStateException("Ingested value differs for field "+field.getKey());
                }
            }catch(RuntimeException error){failure=error;}
        }
        void verifyObjects(){
            // Keep count mismatches ahead of object-content errors, as with the former second scan.
            if(failure!=null)throw failure;
            if(!expected.keySet().equals(actual))throw new IllegalStateException("Missing ingested fixture objects");
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2 || !Set.of("probe","prepare","verify","state","cleanup","session").contains(args[0]))
            throw new IllegalArgumentException("Expected mode and manifest");
        Path file=Path.of(args[1]).toAbsolutePath().normalize();JsonNode manifest=JSON.readTree(file.toFile());
        if(args[0].equals("session")){
            runSession(file,manifest);
            return;
        }
        if(args[0].equals("probe")){
            try(var client=Ignition.startClient(configuration(manifest.path("igniteAddresses").asText()))){
                if(!client.cacheNames().containsAll(CACHES))throw new IllegalStateException("Service caches are missing");
                new LinksCacheSchema(client);
                for(String cache:CACHES)try(var cursor=client.cache(cache).withKeepBinary().query(new ScanQuery<>().setPageSize(1))){cursor.iterator().hasNext();}
                System.out.println("FIXTURE_RESULT="+JSON.writeValueAsString(Map.of("storageContract",LinksCacheSchema.CONTRACT,
                        "schemaCompatible",true,"cacheReadVerified",CACHES,"mutations",false,"sessionProtocol",SESSION_PROTOCOL_VERSION)));
            }
            return;
        }
        long start=System.nanoTime();
        try(var client=Ignition.startClient(configuration(manifest.path("igniteAddresses").asText()))){
            Map<String,Object> result=execute(args[0],file,manifest,client);
            result.put("executionMode","one-shot");result.put("pid",ProcessHandle.current().pid());
            result.put("operationMillis",(System.nanoTime()-start)/1_000_000L);
            System.out.println("FIXTURE_RESULT="+JSON.writeValueAsString(result));
        }
    }
    static void reply(String id,boolean ok,Map<String,Object> result,String errorType)throws Exception{
        Map<String,Object> envelope=new LinkedHashMap<>();
        envelope.put("protocol",SESSION_PROTOCOL_VERSION);envelope.put("id",id);envelope.put("ok",ok);
        if(ok)envelope.put("result",result);else envelope.put("errorType",errorType);
        System.out.println(SESSION_PREFIX+JSON.writeValueAsString(envelope));System.out.flush();
    }
    static void validateSessionManifest(JsonNode original,JsonNode current){
        for(String key:List.of("environment","serviceUri","igniteAddresses","fixtureRuntimeSha256","fixture")){
            if(!original.path(key).equals(current.path(key)))throw new IllegalArgumentException("Fixture session identity changed: "+key);
        }
    }
    static void runSession(Path file,JsonNode original)throws Exception{
        String sessionId=UUID.randomUUID().toString();
        long start=System.nanoTime();
        // Closing stdin (including a terminated parent JVM) ends the session and closes the client.
        try(var input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8));
                var client=Ignition.startClient(configuration(original.path("igniteAddresses").asText()))){
            reply("ready",true,Map.of("state","ready","pid",ProcessHandle.current().pid(),"sessionId",sessionId,
                    "connectionMillis",(System.nanoTime()-start)/1_000_000L),null);
            String line;
            while((line=input.readLine())!=null){
                JsonNode command=JSON.readTree(line);String id=command.path("id").asText();String mode=command.path("mode").asText();
                if(command.path("protocol").asInt()!=SESSION_PROTOCOL_VERSION || !id.matches("[0-9a-f-]{36}")
                        || !Set.of("prepare","verify","state","cleanup","close").contains(mode))
                    throw new IllegalArgumentException("Invalid fixture session command");
                if(mode.equals("close")){
                    reply(id,true,Map.of("state","closed","pid",ProcessHandle.current().pid(),"sessionId",sessionId),null);
                    return;
                }
                long operationStart=System.nanoTime();
                try{
                    // Parent persists loading/ready status between commands; identity remains pinned.
                    JsonNode current=JSON.readTree(file.toFile());validateSessionManifest(original,current);
                    Map<String,Object> result=execute(mode,file,current,client);
                    result.put("executionMode","session");result.put("pid",ProcessHandle.current().pid());
                    result.put("sessionId",sessionId);result.put("operationMillis",(System.nanoTime()-operationStart)/1_000_000L);
                    reply(id,true,result,null);
                }catch(Exception failure){
                    // Keep one client alive after a command failure so partial preparation can be cleaned.
                    // Do not print third-party exception messages which may include connection settings.
                    System.err.println("FIXTURE_COMMAND_FAILED mode="+mode+" type="+failure.getClass().getName());
                    for(StackTraceElement frame:failure.getStackTrace())System.err.println("    at "+frame);
                    reply(id,false,null,failure.getClass().getName());
                }
            }
        }
    }
    static Map<String,Object> execute(String mode,Path file,JsonNode manifest,IgniteClient client)throws Exception{
        JsonNode fixture=manifest.path("fixture");String lease=fixture.path("leaseId").asText();
        if(!lease.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("Invalid ownership token");
        Set<String> points=Set.of("EXPLAB2974_"+lease+"_SP1","EXPLAB2974_"+lease+"_SP2");
        if(!points.equals(Set.of(fixture.path("points").path("SP1").asText(),fixture.path("points").path("SP2").asText())))
            throw new IllegalArgumentException("Points must belong to this exact manifest");
        Path claim=file.resolveSibling("ownership.json");
            if(!client.cacheNames().containsAll(CACHES))throw new IllegalStateException("Service caches are missing; initialize the selected service first");
            Map<String,Object> result=new LinkedHashMap<>();
            if(mode.equals("prepare")){
                LinksCacheSchema schema=new LinksCacheSchema(client);
                if(!"planned".equals(manifest.path("status").asText()) || Files.exists(claim))throw new IllegalStateException("Fixture already claimed");
                if(counts(client,points,false).values().stream().anyMatch(n->n!=0))throw new IllegalStateException("Refusing to overwrite existing point data");
                // Validate and build both metadata sets before acquiring ownership or writing rows.
                Map<String,BinaryObject> parameters=new LinkedHashMap<>(),sources=new LinkedHashMap<>();
                for(var row:fixture.path("params")){
                    String point=row.path("splittingPoint").asText();
                    if(!points.contains(point)||parameters.putIfAbsent(point,schema.parameters(row))!=null)throw new IllegalArgumentException("Invalid parameter ownership");
                }
                for(var row:fixture.path("archiveSources")){
                    String point=row.path("splittingPoint").asText();
                    if(!points.contains(point)||sources.putIfAbsent(point,schema.source(row))!=null)throw new IllegalArgumentException("Invalid source ownership");
                }
                if(!parameters.keySet().equals(points)||!sources.keySet().equals(points))throw new IllegalArgumentException("Both metadata definitions required");
                // Claim is durable before the first write; cleanup is forbidden without this claim.
                Files.writeString(claim,JSON.writeValueAsString(Map.of("leaseId",lease,"environment",manifest.path("environment").asText(),
                        "igniteAddresses",manifest.path("igniteAddresses").asText())),StandardOpenOption.CREATE_NEW);
                for(var row:parameters.entrySet()){
                    if(!client.cache("param_cache").withKeepBinary().putIfAbsent(row.getKey(),row.getValue()))
                        throw new IllegalStateException("Parameter metadata collision");
                }
                for(var row:sources.entrySet()){
                    if(!client.cache("data_source_cache").withKeepBinary().putIfAbsent(row.getKey(),row.getValue()))
                        throw new IllegalStateException("Source metadata collision");
                }
            }
            if(mode.equals("cleanup")){
                if(!Files.exists(claim))throw new IllegalStateException("Refusing cleanup without ownership claim");
                var owner=JSON.readTree(claim.toFile());
                if(!lease.equals(owner.path("leaseId").asText()) || !manifest.path("environment").equals(owner.path("environment"))
                        || !manifest.path("igniteAddresses").equals(owner.path("igniteAddresses")))throw new IllegalStateException("Ownership/environment mismatch");
                result.put("removed",counts(client,points,true));
            }
            ObjectVerification verification=mode.equals("verify")?new ObjectVerification(fixture):null;
            Map<String,Long> state=counts(client,points,false,verification);result.put("remaining",state);
            if(mode.equals("verify")){
                for(String cache:CACHES)if(state.get(cache)!=fixture.path("expectedCounts").path(cache).asLong(-1))
                    throw new IllegalStateException("Fixture count mismatch: "+cache+" actual="+state.get(cache));
                verification.verifyObjects();result.put("objectsVerified",true);
            }
            if(mode.equals("cleanup") && state.values().stream().anyMatch(n->n!=0))throw new IllegalStateException("Owned rows remain after cleanup");
            return result;
    }
}
