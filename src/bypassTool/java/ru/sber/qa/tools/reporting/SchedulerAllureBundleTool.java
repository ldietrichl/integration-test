package ru.sber.qa.tools.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import infrastructure.scheduler.SchedulerOutcomeClassification;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Read-only evidence audit and local raw-Allure preparation. No HTTP, DB or test execution. */
public final class SchedulerAllureBundleTool {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> STAGES = Set.of("scheduler-regression", "scheduler-read-only",
            "scheduler-fixtures", "scheduler-jobs", "scheduler-preflight", "scheduler-infrastructure");
    private static final Set<String> STATUSES = Set.of("passed", "failed", "broken", "skipped", "unknown");

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Expected project, environment, stage, run-or-latest");
        Path project = Path.of(args[0]).toRealPath();
        String env = args[1], stage = args[2], requested = args[3];
        if (!Set.of("dev", "ift", "lt").contains(env) || !STAGES.contains(stage))
            throw new IllegalArgumentException("Unsupported environment or scheduler stage");
        Path root = contained(project, project.resolve("build/regression-results/" + env));
        Path stageRoot = contained(root, root.resolve(stage));
        Path pointer = contained(stageRoot, stageRoot.resolve("latest.txt"));
        String pointerBefore = requested.equals("latest") ? Files.readString(pointer).trim().replace('\\', '/') : null;
        String relative = pointerBefore == null ? "runs/" + requested : pointerBefore;
        if (!relative.matches("runs/[A-Za-z0-9][A-Za-z0-9_.-]*") || relative.contains(".."))
            throw new IllegalArgumentException("Invalid run ID or latest pointer");
        Path run = contained(stageRoot, stageRoot.resolve(relative));
        Path raw = contained(run, run.resolve("allure-results"));
        JsonNode summary = read(run.resolve("summary.json"));
        require(summary.path("completed").asBoolean(false), "Incomplete run; no fallback to an older run");
        require(env.equals(summary.path("environment").asText()) && stage.equals(summary.path("stage").asText()),
                "Run summary environment/stage mismatch");
        long expected = counter(summary, "total");
        require(expected > 0 && expected == counter(summary, "passed") + counter(summary, "failed")
                + counter(summary, "skipped"), "Invalid Gradle summary counters");
        List<Path> sources;
        try (var stream = Files.list(raw)) { sources = stream.sorted().toList(); }
        for (Path source : sources) require(Files.isRegularFile(contained(raw, source), LinkOption.NOFOLLOW_LINKS),
                "Raw Allure directory must contain regular flat files");
        List<Path> results = sources.stream().filter(p -> p.toString().endsWith("-result.json")).toList();
        List<Path> containers = sources.stream().filter(p -> p.toString().endsWith("-container.json")).toList();
        require(results.size() == expected, "JUnit/Allure result count mismatch; source results were not altered");
        Map<String, JsonNode> resultModels = new LinkedHashMap<>();
        List<JsonNode> containerModels = new ArrayList<>();
        Map<String, Path> copies = new TreeMap<>();
        Map<String, String> hashes = new TreeMap<>();
        Set<String> resultIds = new HashSet<>(), containerIds = new HashSet<>();
        Map<String, Long> statuses = new TreeMap<>(), outcomes = new TreeMap<>();
        List<Map<String, Object>> cases = new ArrayList<>();
        int missingHistory = 0, missingTestCase = 0;
        for (Path file : results) {
            JsonNode result = read(file);
            require(!result.toString().contains("Bypass registration: original functional test is not executed")
                    && !result.path("historyId").asText().startsWith("registration-only::"),
                    "Registration results cannot be included in a functional scheduler bundle");
            for (JsonNode label : result.path("labels"))
                require(!(("executionMode".equals(label.path("name").asText()) && "registration-only".equals(label.path("value").asText()))
                        || ("registrationOnly".equals(label.path("name").asText()) && "true".equals(label.path("value").asText()))),
                        "Registration results cannot be included in a functional scheduler bundle");
            String uuid = uuid(result, file, "-result.json");
            require(resultIds.add(uuid), "Duplicate result UUID");
            resultModels.put(uuid, result);
            String status = result.path("status").asText();
            require(STATUSES.contains(status), "Invalid Allure test status");
            String name = result.path("fullName").asText();
            require(!name.isBlank(), "Allure fullName missing");
            String outcome = SchedulerOutcomeClassification.classify(status,
                    result.path("statusDetails").path("message").asText());
            statuses.merge(status, 1L, Long::sum);
            outcomes.merge(outcome, 1L, Long::sum);
            if (result.path("historyId").asText().isBlank()) missingHistory++;
            if (result.path("testCaseId").asText().isBlank()) missingTestCase++;
            cases.add(Map.of("fullName", name, "uuid", uuid, "status", status, "outcomeKind", outcome));
            copies.put(file.getFileName().toString(), file);
            collectAttachments(result, raw, copies);
        }
        for (Path file : containers) {
            JsonNode container = read(file);
            String uuid = uuid(container, file, "-container.json");
            containerModels.add(container);
            require(containerIds.add(uuid) && !resultIds.contains(uuid), "Conflicting Allure UUID");
            JsonNode children = container.path("children");
            require(children.isArray(), "Allure container children missing");
            for (JsonNode child : children)
                require(child.isTextual() && resultIds.contains(child.textValue()), "Dangling Allure container child");
            copies.put(file.getFileName().toString(), file);
            collectAttachments(container, raw, copies);
        }
        require(statuses.getOrDefault("passed", 0L) == counter(summary, "passed")
                && statuses.getOrDefault("failed", 0L) + statuses.getOrDefault("broken", 0L) == counter(summary, "failed")
                && statuses.getOrDefault("skipped", 0L) == counter(summary, "skipped")
                && statuses.getOrDefault("unknown", 0L) == 0, "Allure status counters disagree with Gradle");
        Map<String, Long> junit = junitCounters(contained(run, run.resolve("test-results")));
        require(junit.get("total") == expected && junit.get("passed") == counter(summary, "passed")
                && junit.get("failed") == counter(summary, "failed") && junit.get("skipped") == counter(summary, "skipped"),
                "JUnit XML counters disagree with Gradle summary");
        for (var entry : copies.entrySet()) hashes.put(entry.getKey(), sha(entry.getValue()));
        String summaryHash = sha(run.resolve("summary.json"));
        Path destination = contained(root, root.resolve("scheduler-testops"));
        Files.createDirectories(root);
        try (var channel = FileChannel.open(contained(root, root.resolve(".scheduler-testops.lock")),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            require(lock != null, "Another scheduler TestOps preparation is active");
            String suffix = UUID.randomUUID().toString();
            Path staging = contained(root, root.resolve(".scheduler-testops-staging-" + suffix));
            Path backup = contained(root, root.resolve(".scheduler-testops-previous-" + suffix));
            Path bundleRaw = staging.resolve("allure-results");
            Files.createDirectories(bundleRaw);
            for (var entry : copies.entrySet()) {
                Path target = bundleRaw.resolve(entry.getKey());
                Files.copy(entry.getValue(), target);
                require(hashes.get(entry.getKey()).equals(sha(target))
                        && hashes.get(entry.getKey()).equals(sha(entry.getValue())),
                        "Source changed during preparation");
            }
            Map<String, Integer> untranslatedSteps = new TreeMap<>();
            int[] visibleStepCount = {0};
            for (Path file : results) {
                Path target = bundleRaw.resolve(file.getFileName());
                JsonNode translated = read(target);
                infrastructure.scheduler.SchedulerAllurePresentation.tree(translated);
                auditStepNames(translated, visibleStepCount, untranslatedSteps);
                JSON.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), translated);
            }
            for (Path file : containers) {
                Path target = bundleRaw.resolve(file.getFileName());
                JsonNode translated = read(target);
                infrastructure.scheduler.SchedulerAllurePresentation.tree(translated);
                auditStepNames(translated, visibleStepCount, untranslatedSteps);
                JSON.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), translated);
            }
            List<Map<String, String>> restorationBindings = new ArrayList<>();
            Map<Path, String> restorationHashes = new LinkedHashMap<>();
            int restoredConfigMapsAttached = 0;
            Path privateArtifacts = contained(run, run.resolve("stand-artifacts"));
            if (Files.isDirectory(privateArtifacts)) {
                List<Path> safeFinals;
                try (var stream = Files.walk(privateArtifacts)) {
                    safeFinals = stream.filter(p -> p.getFileName().toString().equals("configmap-restored.safe.json")).toList();
                }
                for (Path file : safeFinals) {
                    contained(privateArtifacts, file);
                    restorationHashes.put(file, sha(file));
                    JsonNode evidence = read(file);
                    require(evidence.path("originalFlagsVerified").asBoolean(false)
                            && "RUN_ROOT_RESTORATION".equals(evidence.path("scope").asText())
                            && evidence.path("capturedAfterAllScenarios").asBoolean(false),
                            "RESTORATION_EVIDENCE_UNVERIFIED: final restoration must be explicitly confirmed");
                    Map<String, String> binding = resolveRestorationOwner(evidence, resultModels, containerModels, raw);
                    String owner = binding.get("ownerTestUuid");
                    restorationBindings.add(binding);
                    var safe = JSON.createObjectNode();
                    safe.put("scope", "RUN_ROOT_RESTORATION");
                    safe.put("description", "Восстановление общего стенда после всех сценариев; не шаг выполнения последнего теста");
                    safe.put("originalFlagsVerified", true);
                    safe.put("capturedAfterAllScenarios", true);
                    safe.put("ownerTestUuid", owner);
                    safe.put("ownerBinding", binding.get("strategy"));
                    safe.set("configMap", infrastructure.scheduler.SchedulerConfigMapEvidence.sanitize(evidence.path("configMap")));
                    String name = UUID.randomUUID() + "-attachment.json";
                    JSON.writerWithDefaultPrettyPrinter().writeValue(bundleRaw.resolve(name).toFile(), safe);
                    Path target = bundleRaw.resolve(owner + "-result.json");
                    var result = (com.fasterxml.jackson.databind.node.ObjectNode) read(target);
                    var attachments = result.withArray("attachments");
                    attachments.addObject().put("name", "ConfigMap: финальное восстановление после всего прогона")
                            .put("source", name).put("type", "application/json");
                    JSON.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), result);
                    restoredConfigMapsAttached++;
                }
            }
            var categories = JSON.createArrayNode();
            for (String name : List.of("categories.json", "scheduler-categories.json")) {
                Path category = contained(project, project.resolve("allure/" + name));
                if (Files.isRegularFile(category)) {
                    JsonNode array = read(category);
                    require(array.isArray(), "Invalid Allure categories");
                    categories.addAll((com.fasterxml.jackson.databind.node.ArrayNode) array);
                }
            }
            JSON.writerWithDefaultPrettyPrinter().writeValue(bundleRaw.resolve("categories.json").toFile(), categories);
            String phase = summary.path("phase").asText("unspecified-legacy");
            require(phase.matches("[A-Za-z_-]+"), "Invalid phase metadata");
            Files.writeString(bundleRaw.resolve("environment.properties"),
                    "env=" + env + "\nschedulerStage=" + stage + "\nschedulerPhase=" + phase
                            + "\nschedulerRun=" + relative.substring(5) + "\n");
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("schemaVersion", 1);
            manifest.put("environment", env); manifest.put("stage", stage); manifest.put("run", relative);
            manifest.put("preparedAt", Instant.now().toString()); manifest.put("phase", phase);
            manifest.put("resultCount", results.size()); manifest.put("containerCount", containers.size());
            manifest.put("attachmentCount", copies.size() - results.size() - containers.size() + restoredConfigMapsAttached);
            manifest.put("allureStatuses", statuses); manifest.put("outcomes", outcomes);
            manifest.put("junit", junit); manifest.put("sourceSummary", summary);
            manifest.put("missingHistoryId", missingHistory); manifest.put("missingTestCaseId", missingTestCase);
            manifest.put("sourceFileSha256", hashes); manifest.put("cases", cases);
            manifest.put("russianStepPresentation", untranslatedSteps.isEmpty());
            manifest.put("visibleStepCount", visibleStepCount[0]);
            manifest.put("untranslatedStepNames", untranslatedSteps);
            manifest.put("restorationBindings", restorationBindings);
            manifest.put("restoredConfigMapsAttached", restoredConfigMapsAttached);
            manifest.put("legacyFinalConfigMapNotInvented", true);
            manifest.put("rawStatusesUnchanged", true); manifest.put("sourceFilesUnchanged", true);
            manifest.put("uploadPerformed", false); manifest.put("buildSuccessNotInferred", true);
            manifest.put("restorationEvidenceRemainsInPrivateRunDirectory", true);
            Path selection = contained(run, run.resolve("selection.json"));
            if (Files.isRegularFile(selection)) manifest.put("selection", read(selection));
            else manifest.put("selection", Map.of("available", false,
                    "reason", "Legacy run: no selection manifest; no omitted coverage is invented"));
            JSON.writerWithDefaultPrettyPrinter().writeValue(staging.resolve("manifest.json").toFile(), manifest);
            for (var source : restorationHashes.entrySet())
                require(source.getValue().equals(sha(source.getKey())), "Restoration evidence changed during preparation");
            for (var source : copies.entrySet())
                require(hashes.get(source.getKey()).equals(sha(source.getValue())), "Source changed during preparation");
            require(summaryHash.equals(sha(run.resolve("summary.json"))), "Summary changed during preparation");
            if (pointerBefore != null)
                require(pointerBefore.equals(Files.readString(pointer).trim().replace('\\', '/')),
                        "Latest run changed during preparation");
            boolean oldMoved = false;
            try {
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    contained(root, destination); Files.move(destination, backup); oldMoved = true;
                }
                Files.move(staging, destination);
            } catch (Exception failure) {
                if (oldMoved && !Files.exists(destination)) Files.move(backup, destination);
                throw failure;
            }
            // Previous bundles are retained, never recursively deleted by the preparation tool.
            System.out.println("SchedulerTestOpsPrepared=true");
            System.out.println("RestoredConfigMapsAttached=" + restoredConfigMapsAttached);
            System.out.println("RussianStepPresentation=" + untranslatedSteps.isEmpty());
            for (var binding : restorationBindings)
                System.out.println("RestorationBinding=" + binding.get("strategy") + "; owner=" + binding.get("ownerTestUuid"));
            System.out.println("SourceRun=" + run);
            System.out.println("AllureResults=" + destination.resolve("allure-results"));
            System.out.println("Manifest=" + destination.resolve("manifest.json"));
            System.out.println("AllureStatuses=" + JSON.writeValueAsString(statuses));
            System.out.println("Outcomes=" + JSON.writeValueAsString(outcomes));
            System.out.println("JUnitAllureCountsMatch=true; StatusesUnchanged=true; UploadPerformed=false");
            if (missingHistory + missingTestCase > 0)
                System.out.println("MetadataWarning: missingHistoryId=" + missingHistory + "; missingTestCaseId=" + missingTestCase);
        }
    }


    private static Map<String, String> resolveRestorationOwner(JsonNode evidence,
            Map<String, JsonNode> results, List<JsonNode> containers, Path raw) throws Exception {
        String fullName = evidence.path("ownerTestFullName").asText();
        String oldOwner = evidence.path("ownerTestUuid").asText();
        if (!fullName.isBlank()) {
            List<String> matches = results.entrySet().stream()
                    .filter(entry -> fullName.equals(entry.getValue().path("fullName").asText()))
                    .map(Map.Entry::getKey).toList();
            require(matches.size() == 1, "RESTORATION_EVIDENCE_UNBOUND: fullName is absent or ambiguous");
            require(oldOwner.isBlank() || !results.containsKey(oldOwner) || oldOwner.equals(matches.get(0)),
                    "RESTORATION_EVIDENCE_UNBOUND: conflicting owner identities");
            return Map.of("strategy", "JUNIT_FULL_NAME", "ownerTestUuid", matches.get(0));
        }
        if (results.containsKey(oldOwner))
            return Map.of("strategy", "EXISTING_RESULT_UUID", "ownerTestUuid", oldOwner);
        // Legacy v17 captured a fixture UUID. Recover only from the unique initial ConfigMap attachment.
        Set<String> candidates = new HashSet<>();
        for (var result : results.entrySet())
            if (hasInitialConfigMap(result.getValue(), evidence.path("configMap"), raw)) candidates.add(result.getKey());
        for (JsonNode container : containers) {
            if (!hasInitialConfigMap(container, evidence.path("configMap"), raw)) continue;
            for (JsonNode child : container.path("children")) candidates.add(child.asText());
        }
        require(candidates.size() == 1, "RESTORATION_EVIDENCE_UNBOUND: initial ConfigMap attachment is absent or ambiguous");
        String owner = candidates.iterator().next();
        require(results.containsKey(owner), "RESTORATION_EVIDENCE_UNBOUND: child result missing");
        return Map.of("strategy", "LEGACY_INITIAL_CONFIGMAP_ATTACHMENT", "ownerTestUuid", owner,
                "legacyOwnerTestUuid", oldOwner);
    }
    private static boolean hasInitialConfigMap(JsonNode node, JsonNode restored, Path raw) throws Exception {
        for (JsonNode attachment : node.path("attachments")) {
            if (!"ConfigMap: исходное состояние (чувствительные значения скрыты)".equals(attachment.path("name").asText()))
                continue;
            JsonNode original = read(contained(raw, raw.resolve(attachment.path("source").asText())));
            boolean identity = true;
            for (String field : List.of("namespace", "name", "uid")) {
                String expected = restored.path("metadata").path(field).asText();
                identity &= !expected.isBlank() && expected.equals(original.path("metadata").path(field).asText());
            }
            if (identity && infrastructure.scheduler.SchedulerConfigMapEvidence.sanitize(original).path("data")
                    .equals(infrastructure.scheduler.SchedulerConfigMapEvidence.sanitize(restored).path("data"))) return true;
        }
        for (String field : List.of("steps", "befores", "afters"))
            for (JsonNode child : node.path(field))
                if (hasInitialConfigMap(child, restored, raw)) return true;
        return false;
    }
    private static void auditStepNames(JsonNode node, int[] count, Map<String, Integer> untranslated) {
        for (String field : List.of("steps", "befores", "afters")) {
            for (JsonNode child : node.path(field)) {
                count[0]++;
                String name = child.path("name").asText();
                if (!name.matches("(?s).*[\\p{IsCyrillic}].*"))
                    untranslated.merge(name, 1, Integer::sum);
                auditStepNames(child, count, untranslated);
            }
        }
    }

    private static Map<String, Long> junitCounters(Path directory) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        long total = 0, failed = 0, skipped = 0;
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).toList();
        }
        require(!files.isEmpty(), "No JUnit XML in selected run");
        for (Path path : files) {
            contained(directory, path);
            var document = factory.newDocumentBuilder().parse(path.toFile());
            var tests = document.getElementsByTagName("testcase");
            for (int i = 0; i < tests.getLength(); i++) {
                var test = (org.w3c.dom.Element) tests.item(i); total++;
                if (test.getElementsByTagName("failure").getLength() > 0
                        || test.getElementsByTagName("error").getLength() > 0) failed++;
                else if (test.getElementsByTagName("skipped").getLength() > 0) skipped++;
            }
        }
        return Map.of("total", total, "passed", total - failed - skipped, "failed", failed, "skipped", skipped);
    }
    private static void collectAttachments(JsonNode node, Path raw, Map<String, Path> copies) throws Exception {
        if (node.isObject()) {
            if (node.has("attachments")) {
                require(node.get("attachments").isArray(), "Invalid attachments list");
                for (JsonNode attachment : node.get("attachments")) {
                    String name = attachment.path("source").asText();
                    require(!name.isBlank() && !name.contains("/") && !name.contains("\\")
                            && !name.contains(":") && !name.equals(".") && !name.equals(".."), "Invalid attachment path");
                    Path source = contained(raw, raw.resolve(name));
                    require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS), "Missing attachment: " + name);
                    copies.put(name, source);
                }
            }
            var it = node.fields();
            while (it.hasNext()) {
                var field = it.next();
                if (!field.getKey().equals("attachments")) collectAttachments(field.getValue(), raw, copies);
            }
        } else if (node.isArray()) for (JsonNode child : node) collectAttachments(child, raw, copies);
    }
    private static Path contained(Path root, Path candidate) throws Exception {
        root = root.toAbsolutePath().normalize(); candidate = candidate.toAbsolutePath().normalize();
        require(candidate.startsWith(root) && !candidate.equals(root), "Path leaves expected parent");
        for (Path node = candidate; node != null; node = node.getParent())
            require(!Files.isSymbolicLink(node), "Symbolic paths are not permitted");
        if (Files.exists(candidate))
            require(candidate.toRealPath().startsWith(root.toRealPath()), "Redirected path");
        return candidate;
    }
    private static JsonNode read(Path path) throws Exception {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 64L * 1024 * 1024,
                "Missing or oversized JSON artifact");
        return JSON.readTree(path.toFile());
    }
    private static long counter(JsonNode node, String key) {
        JsonNode value = node.path(key);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0, "Invalid counter: " + key);
        return value.longValue();
    }
    private static String uuid(JsonNode node, Path file, String suffix) {
        String uuid = node.path("uuid").asText();
        require(uuid.matches("[A-Za-z0-9_-]+") && file.getFileName().toString().equals(uuid + suffix), "Invalid UUID filename");
        return uuid;
    }
    private static String sha(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int size;
            while ((size = in.read(buffer)) >= 0) digest.update(buffer, 0, size);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
