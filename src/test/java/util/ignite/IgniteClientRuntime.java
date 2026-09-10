package util.ignite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

/** Runs a helper against verified Ignite libraries without adding them to the test classpath. */
public final class IgniteClientRuntime {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Path, Object> COMPILE_LOCKS = new ConcurrentHashMap<>();
    private static final List<String> OPEN_PACKAGES = List.of(
            "java.nio", "sun.nio.ch", "sun.nio.cs", "java.lang", "java.lang.invoke",
            "java.lang.reflect", "java.util", "java.io");

    private final IgniteConfiguration configuration;
    private final String classpath;
    private final String mainClass;
    private final String resultPrefix;
    public final String runtimeSha256;

    public IgniteClientRuntime(IgniteConfiguration configuration, List<Path> sources,
            String mainClass, Path outputDirectory, String resultPrefix) throws Exception {
        this.configuration = configuration;
        if (mainClass == null || !mainClass.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) {
            throw new IllegalArgumentException("Invalid Ignite helper main class");
        }
        if (resultPrefix == null || !resultPrefix.matches("[A-Z][A-Z0-9_]*=")) {
            throw new IllegalArgumentException("Invalid Ignite helper result prefix");
        }
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Ignite helper sources are required");
        }
        this.mainClass = mainClass;
        this.resultPrefix = resultPrefix;

        Path bundle = configuration.runtimeDirectory().toAbsolutePath().normalize();
        Path lockFile = bundle.resolve("client-libraries.lock.json");
        if (!Files.isRegularFile(lockFile)) {
            throw new IllegalStateException("Install the configured Ignite client bundle: " + lockFile);
        }
        JsonNode libraryLock = JSON.readTree(lockFile.toFile());
        JsonNode lockedLibraries = libraryLock.path("libraries");
        if (libraryLock.path("schemaVersion").asInt() != 1
                || !lockedLibraries.isArray() || lockedLibraries.isEmpty()) {
            throw new IllegalStateException("Invalid Ignite client library lock");
        }
        List<String> libraries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (JsonNode library : lockedLibraries) {
            String name = library.path("file").asText();
            String expected = library.path("sha256").asText();
            if (!name.matches("[A-Za-z0-9._-]+\\.jar") || !names.add(name)
                    || !expected.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalStateException("Invalid Ignite client library entry");
            }
            Path file = bundle.resolve("lib").resolve(name);
            if (!Files.isRegularFile(file) || !expected.equalsIgnoreCase(hash(file))) {
                throw new IllegalStateException("Missing or changed Ignite client library: " + file);
            }
            libraries.add(file.toString());
        }

        List<Path> sourceFiles = new ArrayList<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        // Keep the historical lock + ordered source bytes algorithm for fixture recovery.
        digest.update(Files.readAllBytes(lockFile));
        for (Path source : sources) {
            Path file = source.toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("Ignite helper source missing: " + file);
            }
            sourceFiles.add(file);
            digest.update(Files.readAllBytes(file));
        }
        runtimeSha256 = HexFormat.of().formatHex(digest.digest());
        Path compileDirectory = outputDirectory.toAbsolutePath().normalize()
                .resolve("runtime-" + runtimeSha256.substring(0, 24));
        List<String> classpathEntries = new ArrayList<>();
        classpathEntries.add(compileDirectory.toString());
        classpathEntries.addAll(libraries);
        classpath = String.join(File.pathSeparator, classpathEntries);

        // File locks coordinate JVMs; the monitor also coordinates callers in this JVM.
        synchronized (COMPILE_LOCKS.computeIfAbsent(compileDirectory, ignored -> new Object())) {
            Files.createDirectories(compileDirectory);
            try (var channel = FileChannel.open(compileDirectory.resolve("compile.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                Path complete = compileDirectory.resolve("complete");
                if (Files.exists(complete)) {
                    if (!runtimeSha256.equals(Files.readString(complete, StandardCharsets.UTF_8))) {
                        throw new IllegalStateException("Ignite helper compilation marker differs: " + complete);
                    }
                } else {
                    var compiler = ToolProvider.getSystemJavaCompiler();
                    if (compiler == null) {
                        throw new IllegalStateException("JDK 17 is required to compile the Ignite helper");
                    }
                    Path compileLog = compileDirectory.resolve("compile.log");
                    try (var log = Files.newOutputStream(compileLog)) {
                        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8",
                                "-proc:none", "-cp", classpath, "-d", compileDirectory.toString()));
                        sourceFiles.forEach(file -> arguments.add(file.toString()));
                        int exit = compiler.run(null, log, log, arguments.toArray(String[]::new));
                        if (exit != 0) {
                            throw new IllegalStateException("Ignite helper compilation failed; see " + compileLog);
                        }
                    }
                    Files.writeString(complete, runtimeSha256, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                }
            }
        }
    }

    public JsonNode call(String mode, Path manifest) throws Exception {
        return call(mode, manifest, Map.of());
    }

    /** Additional environment entries are reserved for adapters using the historical LINKS_IGNITE protocol. */
    public JsonNode call(String mode, Path manifest, Map<String, String> additionalEnvironment) throws Exception {
        HelperLaunch launch = launch(mode, manifest, additionalEnvironment);
        Path manifestFile = launch.manifest();
        Path logFile = launch.logFile();
        Process process = launch.builder().redirectOutput(logFile.toFile()).start();
        try {
            if (!process.waitFor(launch.timeoutSeconds(), TimeUnit.SECONDS)) {
                var failure = new IllegalStateException("Ignite " + mode + " timed out; manifest retained: " + manifestFile);
                try { IgniteHelperSession.terminate(process); } catch (Exception termination) { failure.addSuppressed(termination); }
                throw failure;
            }
        } catch (InterruptedException interrupted) {
            try { IgniteHelperSession.terminate(process); }
            catch (Exception termination) { interrupted.addSuppressed(termination); }
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Ignite " + mode + " failed; inspect " + logFile);
        }
        try (var lines = Files.lines(logFile, StandardCharsets.UTF_8)) {
            String result = lines.filter(line -> line.startsWith(resultPrefix)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Ignite helper result missing; inspect " + logFile));
            return JSON.readTree(result.substring(resultPrefix.length()));
        }
    }

    /** Opens protocol v1; one helper process is owned by one fixture manifest. */
    public IgniteHelperSession openSession(Path manifest, Map<String, String> additionalEnvironment) throws Exception {
        return new IgniteHelperSession(launch("session", manifest, additionalEnvironment));
    }

    record HelperLaunch(ProcessBuilder builder, Path manifest, Path logFile, long timeoutSeconds) { }

    private HelperLaunch launch(String mode, Path manifest, Map<String, String> additionalEnvironment) throws Exception {
        if (mode == null || !mode.matches("[A-Za-z][A-Za-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid Ignite helper operation");
        }
        Path manifestFile = manifest.toAbsolutePath().normalize();
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("Ignite helper manifest missing: " + manifestFile);
        }
        String suffix = mode + "-" + UUID.randomUUID();
        Path argumentFile = manifestFile.resolveSibling(suffix + ".args");
        Path logFile = manifestFile.resolveSibling(suffix + ".log");
        List<String> arguments = new ArrayList<>();
        arguments.add("-Dfile.encoding=UTF-8");
        for (String name : OPEN_PACKAGES) {
            arguments.add("--add-opens=java.base/" + name + "=ALL-UNNAMED");
        }
        Collections.addAll(arguments, "-cp", classpath, mainClass, mode, manifestFile.toString());
        Files.write(argumentFile, arguments.stream().map(IgniteClientRuntime::quoteArgument).toList(),
                StandardCharsets.UTF_8);
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        var builder = new ProcessBuilder(java.toString(), "@" + argumentFile).redirectErrorStream(true);
        Map<String, String> childEnvironment = builder.environment();
        childEnvironment.keySet().removeIf(key -> {
            String normalized = key.toUpperCase(Locale.ROOT);
            return normalized.startsWith("IGNITE_CLIENT_") || normalized.startsWith("LINKS_IGNITE_");
        });
        Map<String, String> selectedEnvironment = new LinkedHashMap<>(configuration.helperEnvironment());
        for (var entry : additionalEnvironment.entrySet()) {
            if (!entry.getKey().matches("LINKS_IGNITE_[A-Z0-9_]+") || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid legacy Ignite helper environment entry");
            }
            selectedEnvironment.put(entry.getKey(), entry.getValue());
        }
        childEnvironment.putAll(selectedEnvironment);
        long timeoutSeconds = configuration.operationTimeoutSeconds();
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Ignite operation timeout must be positive");
        }
        return new HelperLaunch(builder, manifestFile, logFile, timeoutSeconds);
    }

    private static String quoteArgument(String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Unsupported control character in Ignite helper argument");
        }
        return "\"" + value.replace('\\', '/').replace("\"", "\\\"") + "\"";
    }

    private static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) >= 0;) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
