package ru.sber.qa.experiments.EXPLAB_2972.launch_plan;

import com.fasterxml.jackson.databind.JsonNode;
import config.services.core.StatusChange2972Settings;
import config.services.rest.LaunchPlan2972UsersService;
import io.perfeccionista.framework.Environment;
import io.qameta.allure.Allure;
import io.restassured.http.Method;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static steps.rest.experiments.v2.StatusChange2972Steps.JSON;

/** PDF pp. 3292–3301, 3321–3327, 3333–3346. IAM accounts/tokens are provisioned outside user-service. */
final class LaunchPlan2972Users {
    static final Map<String,String> ROLES = Map.of("primary","expCreator", "approver","expValidator",
            "admin","adminSp", "operator","smartExpCreator");

    static void prepare(StatusChange2972Settings s) throws Exception {
        String mode=s.required("launch-plan.users.mode");
        if(!Set.of("api","prepared","local-db-adapter").contains(mode))
            throw new IllegalStateException("User provisioning mode must be api, prepared or local-db-adapter");
        if(mode.equals("local-db-adapter")&&!s.env.equals("local"))
            throw new IllegalStateException("Local user adapter is forbidden on corporate stands");
        if(s.env.equals("local")&&!s.localAuth())throw new IllegalStateException("Distinct local identities must be enabled");
        String lookupMode=s.optional("launch-plan.users.lookup-mode","subs");
        if(!Set.of("subs","audit").contains(lookupMode))
            throw new IllegalStateException("User lookup mode must be subs or audit; endpoint fallback is not allowed");
        Set<Long> ids=new HashSet<>();Set<String> subs=new HashSet<>();
        List<Object> proof=new ArrayList<>();
        for(String role:List.of("primary","approver","admin","operator")) {
            String configured=s.optional("auth."+role+".user-id",null);
            Long id=configured==null?null:Long.valueOf(configured);
            String sub=s.required("auth."+role+".sub");UUID.fromString(sub);
            if(!subs.add(sub))throw new IllegalStateException("Four distinct subjects are required");
            String[] jwt=s.token(role,false).split("\\.");
            if(jwt.length!=3)throw new IllegalStateException("Expected configured IAM bearer token for "+role);
            JsonNode payload=JSON.readTree(Base64.getUrlDecoder().decode(jwt[1]));
            if(!sub.equals(payload.path("sub").asText()))throw new IllegalStateException("Token subject mismatch for "+role);
            if(payload.path("exp").asLong()<=System.currentTimeMillis()/1000)
                throw new IllegalStateException("Expired account token for "+role);
            if(payload.path("nbf").asLong()>System.currentTimeMillis()/1000)
                throw new IllegalStateException("Account token is not yet valid for "+role);
            Set<String> tokenRoles=new HashSet<>();
            payload.at("/resource_access/explab/roles").forEach(value->tokenRoles.add(value.asText()));
            assertEquals(Set.of(ROLES.get(role),"spMAPPER"),tokenRoles,
                    "Token roles must match the dedicated user; a valid subject alone does not establish the tested privileges");
            String login=StatusChange2972Settings.resolveSecret(s.required("auth."+role+".login"),"auth."+role+".login");
            if(mode.equals("api")) {
                if(!login.startsWith("EXPLAB-2972-"))throw new IllegalStateException("Only dedicated EXPLAB-2972- accounts may be upserted");
                // Refuse to replace an existing subject's identity or roles unless they match the owned fixture.
                JsonNode before=lookup(lookupMode,sub,true);
                if(before!=null) {
                    assertIdentity(s,role,login,before);
                    assertStoredRoles(lookupMode,sub,before,role);
                    if(before.hasNonNull("id"))assertTrue(before.path("id").asLong()>1,
                            "A system account must never be upserted by role fixtures");
                    if(id!=null&&before.hasNonNull("id"))
                        assertEquals(id.longValue(),before.path("id").asLong(),"Existing fixture ID differs from configuration");
                }
                call("POST","/api/v2/users",Map.of("sub",sub,"employeeId",s.required("auth."+role+".employee-id"),
                        "userName",login,"lastName","EXPLAB2972", "firstName",role,
                        "email",s.required("auth."+role+".email"),"roles",List.of(ROLES.get(role),"spMAPPER"),
                        "splittingPoints",List.of("MAPPER")));
            }
            JsonNode user=lookup(lookupMode,sub,false);
            if(user==null)throw new IllegalStateException("User is not provisioned: "+role);
            assertIdentity(s,role,login,user);
            assertStoredRoles(lookupMode,sub,user,role);
            JsonNode permissions=call("GET","/api/v2/users/permissions?sub="+sub
                    +"&control_point_code=status_update_exp&roles="+ROLES.get(role)+"&roles=spMAPPER",null);
            long actualId=permissions.path("userId").asLong();
            if(id!=null)assertEquals(id.longValue(),actualId,"Configured user ID differs from user-service");
            if(user.hasNonNull("id"))assertEquals(user.path("id").asLong(),actualId,"Lookup and permissions must refer to the same user");
            if(actualId<=1||!ids.add(actualId))throw new IllegalStateException("Four distinct non-system users are required");
            s.properties.setProperty("explab2972."+s.env+".auth."+role+".user-id",Long.toString(actualId));
            proof.add(Map.of("role",role,"id",actualId,"sub",sub,"roleCodes",List.of(ROLES.get(role),"spMAPPER")));
        }
        Allure.addAttachment("Distinct user identities (without tokens)","application/json",
                JSON.writeValueAsString(Map.of("mode",mode,"lookupMode",lookupMode,"accounts",proof,"tokenSubjectChecked",true,
                        "iamSignatureVerifiedByTest",false)),".json");
    }
    private static JsonNode lookup(String mode,String sub,boolean allowMissing) throws Exception {
        if(mode.equals("audit")) {
            JsonNode user=call("GET","/api/v2/users/audit/"+sub,null,allowMissing);
            if(user==null)return null;
            assertTrue(user.isObject(),"Audit lookup must return one account object");
            assertTrue(user.hasNonNull("id")&&user.path("id").asLong()>1,
                    "Audit lookup must identify a non-system account");
            return user;
        }
        JsonNode users=users(call("GET","/api/v2/users/subs?subs="+sub,null));
        assertTrue(users.isArray(),"User lookup must return an array");
        JsonNode user=null;int matches=0;
        for(JsonNode candidate:users)if(sub.equals(candidate.path("sub").asText())){user=candidate;matches++;}
        assertTrue(matches<=1,"Subject lookup must identify at most one account");
        if(!allowMissing)assertEquals(1,matches,"Subject lookup must identify exactly one account");
        return user;
    }
    private static void assertIdentity(StatusChange2972Settings s,String role,String expectedLogin,JsonNode user) {
        assertEquals(expectedLogin,login(user),"Existing subject is not the owned fixture");
        assertEquals(s.required("auth."+role+".employee-id"),user.path("employeeId").asText(),
                "Employee ID differs from the dedicated fixture");
        assertEquals(s.required("auth."+role+".email"),user.path("email").asText(),
                "Email differs from the dedicated fixture");
    }
    private static void assertStoredRoles(String lookupMode,String sub,JsonNode user,String role) throws Exception {
        if(lookupMode.equals("subs")){assertRoles(user,role);return;}
        // Audit omits roles. Omitting the request's roles parameter makes user-service read its stored roles.
        // Supplying expected roles here would merely echo our own privileges and could hide a wrong fixture.
        JsonNode permissions=call("GET","/api/v2/users/permissions?sub="+sub
                +"&control_point_code=status_update_exp",null);
        JsonNode storedUser=permissions.path("userData");
        assertEquals(user.path("id").asLong(),permissions.path("userId").asLong(),
                "Audit and stored permissions must identify the same account");
        assertEquals(user.path("id").asLong(),storedUser.path("id").asLong(),
                "Stored role information must identify the audited account");
        for(String field:List.of("username","employeeId","email"))
            assertEquals(user.path(field),storedUser.path(field),"Audit and permissions identity mismatch: "+field);
        assertRoles(storedUser,role);
        assertTrue(permissions.path("principalIds").isArray()&&permissions.path("principalIds").isEmpty(),
                "Dedicated role fixtures must not inherit another user's delegated rights");
        assertTrue(permissions.path("effectivePermissions").isArray()&&permissions.path("effectivePermissions").isEmpty(),
                "Dedicated role fixtures must not have delegated permissions");
    }
    private static JsonNode users(JsonNode response) {
        // Documented corporate envelope is {users:[...]}; an older local adapter returned a bare array.
        return response.isArray()?response:response.path("users");
    }
    private static String login(JsonNode user) {
        // The v17 field table says username; its example also contains userName.
        return user.hasNonNull("username")?user.path("username").asText():user.path("userName").asText();
    }
    private static void assertRoles(JsonNode user,String role) {
        Set<String> actual=new HashSet<>();user.path("roles").forEach(v->actual.add(v.isTextual()?v.asText():v.path("code").asText()));
        assertEquals(Set.of(ROLES.get(role),"spMAPPER"),actual,"Unexpected privilege set for "+role);
    }
    private static JsonNode call(String method,String path,Object body) throws Exception {
        return call(method,path,body,false);
    }
    private static JsonNode call(String method,String path,Object body,boolean allowAuditMissing) throws Exception {
        var client=Environment.getForCurrentThread().getService(LaunchPlan2972UsersService.class).restClient();
        var response=client.request(Method.valueOf(method),spec->{
            if(body!=null)try{spec.body(JSON.writeValueAsString(body));}catch(Exception e){throw new IllegalArgumentException(e);}
            return spec;
        },path).toResponse();
        JsonNode data=response.asString().isBlank()?JSON.nullNode():JSON.readTree(response.asString());
        // This branch maps EntityNotFoundException to 400. Only its precise documented-in-code missing-user
        // response is absence; route errors, generic 400, 404 and dependency failures must prevent an upsert.
        if(allowAuditMissing&&method.equals("GET")&&path.startsWith("/api/v2/users/audit/")
                &&response.statusCode()==400&&"Пользователь не найден".equals(data.path("message").asText()))return null;
        assertTrue(response.statusCode()>=200&&response.statusCode()<300,"User API "+method+" "+path+" HTTP "+response.statusCode());
        return data;
    }
}
