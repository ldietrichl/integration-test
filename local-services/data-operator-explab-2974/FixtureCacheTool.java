import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import explab.dataoperator.client.dictionary.dto.DataSourceDictDto;
import explab.dataoperator.client.dictionary.dto.ParamDictDto;
import explab.dataoperator.client.dictionary.dto.SplitDto;
import explab.dataoperator.model.mapper.DataSourceDictCacheEntryMapper;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.configuration.ClientConfiguration;
import java.nio.file.Path;
import java.util.*;

/** Local-only fallback: source metadata missing from Feign's MAPPER-only refresh, and owned cleanup. */
public class FixtureCacheTool {
    static final List<String> CACHES = List.of("splitting_object_cache", "splitting_field_cache",
            "actualization_cache", "param_cache", "data_source_cache");

    static String point(Object key) {
        if (key instanceof String text) return text;
        if (key instanceof BinaryObject binary && binary.hasField("splittingPoint"))
            return binary.field("splittingPoint");
        return null;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("install-sources", "state", "cleanup").contains(args[0]))
            throw new IllegalArgumentException("Expected install-sources/state/cleanup and fixture manifest");
        var mapper = new ObjectMapper();
        JsonNode manifest = mapper.readTree(Path.of(args[1]).toFile());
        String address = manifest.path("igniteAddress").asText();
        if (!address.matches("127\\.0\\.0\\.1:[0-9]{4,5}"))
            throw new IllegalArgumentException("Only a loopback Ignite endpoint is allowed");
        JsonNode fixture = manifest.path("fixture");
        String lease = fixture.path("leaseId").asText();
        if (!lease.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid lease ID");
        Set<String> points = Set.of("EXPLAB2974_" + lease + "_SP1", "EXPLAB2974_" + lease + "_SP2");
        if (!points.equals(Set.of(fixture.path("points").path("SP1").asText(), fixture.path("points").path("SP2").asText())))
            throw new IllegalArgumentException("Point names must belong to this exact lease");
        Map<String, Object> result = new LinkedHashMap<>();
        try (var client = Ignition.startClient(new ClientConfiguration().setAddresses(address)
                .setPartitionAwarenessEnabled(false).setClusterDiscoveryEnabled(false).setTimeout(10000))) {
            if (!client.cacheNames().containsAll(CACHES)) throw new IllegalStateException("Required caches missing");
            if (args[0].equals("install-sources")) {
                Set<String> paramPoints = new HashSet<>();
                for (var row : fixture.path("params")) {
                    var dto = mapper.treeToValue(row, ParamDictDto.class);
                    if (!points.contains(dto.getSplittingPoint()) || !paramPoints.add(dto.getSplittingPoint())
                            || dto.getParams() == null || dto.getParams().isEmpty())
                        throw new IllegalArgumentException("Invalid parameter dictionary");
                    for (var param : dto.getParams())
                        if (param.getType() == null || param.getOrder() == null || param.getCode() == null
                                || param.getParamPath() == null || param.getName() == null)
                            throw new IllegalArgumentException("Incomplete parameter metadata");
                }
                if (!paramPoints.equals(points)) throw new IllegalArgumentException("Both parameter definitions required");
                for (var row : fixture.path("splits")) {
                    var dto = mapper.treeToValue(row, SplitDto.class);
                    if (dto.getId() == null || dto.getName() == null || dto.getName().isBlank())
                        throw new IllegalArgumentException("Invalid experiment split DTO");
                }
                var rows = fixture.path("archiveSources");
                Set<String> sourcePoints = new HashSet<>();
                List<DataSourceDictDto> sources = new ArrayList<>();
                for (var row : rows) {
                    var dto = mapper.treeToValue(row, DataSourceDictDto.class);
                    if (!points.contains(dto.getSplittingPoint()) || !sourcePoints.add(dto.getSplittingPoint()))
                        throw new IllegalArgumentException("Source row outside this lease or duplicate");
                    if (!mapper.readTree(dto.getDataSchema()).path("properties").path("objects").has("items"))
                        throw new IllegalArgumentException("Full object schema required");
                    sources.add(dto);
                }
                if (!sourcePoints.equals(points)) throw new IllegalArgumentException("Both source definitions required");
                var cache = client.<String, Object>cache("data_source_cache");
                for (var dto : sources)
                    if (!cache.putIfAbsent(dto.getSplittingPoint(), DataSourceDictCacheEntryMapper.map(dto)))
                        throw new IllegalStateException("Refusing to overwrite existing source metadata");
            }
            for (String name : CACHES) {
                var cache = client.cache(name).withKeepBinary();
                long found = 0, removed = 0;
                try (var cursor = cache.query(new ScanQuery<>())) {
                    for (var entry : cursor) {
                        String owner = point(entry.getKey());
                        if (owner == null || !points.contains(owner)) continue;
                        found++;
                        if (args[0].equals("cleanup")) {
                            if (!cache.remove(entry.getKey(), entry.getValue()))
                                throw new IllegalStateException("Owned row changed during cleanup: " + name);
                            removed++;
                        }
                    }
                }
                result.put(name, Map.of("found", found, "removed", removed));
            }
        }
        System.out.println("FIXTURE_RESULT=" + mapper.writeValueAsString(result));
    }
}
