package util.dataoperator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import request.dataoperator.v2.DataOperatorLinksFunctionalCases.DataSet;
import steps.rest.dataoperator.v2.DataOperatorV2Steps;
import java.nio.file.*;
import java.util.UUID;

/** Each invocation owns its points; try-with-resources cleans partial setup and failed assertions. */
public final class LinksFixtureSession implements AutoCloseable {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final LinksFixtureRuntime runtime;
    private final ObjectNode manifest;
    private final Path manifestFile;
    private boolean closed;
    public LinksFixtureSession(DataSet dataSet)throws Exception{
        var config=new LinksFixtureConfiguration();runtime=new LinksFixtureRuntime(config);
        String lease=UUID.randomUUID().toString().replace("-","");
        Path directory=config.output().resolve("case-"+lease);Files.createDirectory(directory);
        manifestFile=directory.resolve("fixture-manifest.json");
        manifest=JSON.createObjectNode().put("schemaVersion",3).put("environment",config.environment)
                .put("serviceUri",config.serviceUri).put("igniteAddresses",config.ignite().required("addresses"))
                .put("fixtureRuntimeSha256",runtime.runtimeSha256).put("status","planned");
        manifest.set("fixture",LinksFixtureData.create(dataSet,lease));save();
    }
    private LinksFixtureSession(Path file)throws Exception{
        var config=new LinksFixtureConfiguration();
        manifestFile=file.toAbsolutePath().normalize();
        if(!manifestFile.startsWith(config.output()))throw new IllegalArgumentException("Recovery manifest must be inside this environment's output directory");
        manifest=(ObjectNode)JSON.readTree(manifestFile.toFile());
        if(!config.environment.equals(manifest.path("environment").asText()) || !config.serviceUri.equals(manifest.path("serviceUri").asText())
                || !config.ignite().required("addresses").equals(manifest.path("igniteAddresses").asText()))
            throw new IllegalArgumentException("Recovery environment, service or Ignite differs from manifest");
        runtime=new LinksFixtureRuntime(config);
        if(manifest.path("schemaVersion").asInt()!=3 || !runtime.runtimeSha256.equals(manifest.path("fixtureRuntimeSha256").asText()))
            throw new IllegalArgumentException("Use the original fixture runtime for recovery; recover older fixtures before installing a new client bundle");
    }
    public static void recover(Path file)throws Exception{try(var session=new LinksFixtureSession(file)){ /* close performs recovery */ }}
    public String point(){return manifest.path("fixture").path("points").path("SP1").asText();}
    public Path manifestFile(){return manifestFile;}
    public void prepare(DataOperatorV2Steps steps)throws Exception{
        if(!"planned".equals(manifest.path("status").asText()))throw new IllegalStateException("Fixture setup already performed");
        Allure.step("Готовим метаданные только двух собственных точек (Ignite fallback)",()->{
            runtime.startSession(manifestFile);
            JsonNode prepared=runtime.call("prepare",manifestFile);
            Allure.addAttachment("Fixture prepared: helper session","application/json",prepared.toString(),".json");
            return prepared;
        });
        manifest.put("status","loading");save();
        var entries=manifest.path("fixture").path("payloads").fields();
        while(entries.hasNext()){
            var entry=entries.next();
            var response=steps.loadSplittingObjectsFixture(entry.getKey(),entry.getValue().toString()).toResponse();
            Allure.addAttachment("Fixture REST load: "+entry.getKey(),"text/plain","HTTP "+response.statusCode()+"\n"+response.asString(),".txt");
            if(response.statusCode()!=200)throw new IllegalStateException("Real data-operator fixture load returned HTTP "+response.statusCode());
        }
        JsonNode loaded=Allure.step("Проверяем загруженные объекты и состав фикстуры в Ignite",()->runtime.call("verify",manifestFile));
        JSON.writerWithDefaultPrettyPrinter().writeValue(manifestFile.resolveSibling("fixture-loaded.json").toFile(),loaded);
        Allure.addAttachment("Fixture loaded and verified","application/json",loaded.toString(),".json");
        manifest.put("status","ready");save();
    }
    private void save()throws Exception{
        Path pending=manifestFile.resolveSibling("fixture-manifest.pending");
        JSON.writerWithDefaultPrettyPrinter().writeValue(pending.toFile(),manifest);
        try{Files.move(pending,manifestFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(pending,manifestFile,StandardCopyOption.REPLACE_EXISTING);}
    }
    @Override public void close()throws Exception{
        if(closed)return;
        boolean interrupted=Thread.interrupted();
        Exception failure=null;
        try{
            if(Files.exists(manifestFile.resolveSibling("ownership.json"))){
                boolean report=Allure.getLifecycle().getCurrentTestCase().isPresent();
                JsonNode cleaned=report
                        ?Allure.step("Удаляем собственные объекты и метаданные, проверяем нулевой остаток",()->runtime.cleanup(manifestFile))
                        :runtime.cleanup(manifestFile);
                JSON.writerWithDefaultPrettyPrinter().writeValue(manifestFile.resolveSibling("fixture-cleanup.json").toFile(),cleaned);
                if(report)Allure.addAttachment("Fixture cleanup: zero owned rows","application/json",cleaned.toString(),".json");
                manifest.put("status","cleaned");
            }else manifest.put("status","not-acquired");
            save();closed=true;
        }catch(Exception cleanupFailure){failure=cleanupFailure;}
        finally{
            try{runtime.close();}
            catch(Exception shutdownFailure){
                if(failure==null)failure=shutdownFailure;else failure.addSuppressed(shutdownFailure);
            }
            if(interrupted)Thread.currentThread().interrupt();
        }
        if(failure==null && interrupted)failure=new InterruptedException("Fixture cleanup completed after interruption");
        if(failure!=null)throw failure;
    }
}
