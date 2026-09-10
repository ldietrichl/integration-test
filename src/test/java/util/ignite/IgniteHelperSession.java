package util.ignite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Sequential JSON-lines commands; timeouts apply to operations, not idle fixture lifetime. */
public final class IgniteHelperSession implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PREFIX = "IGNITE_SESSION=";
    private record Reply(JsonNode payload, Exception failure) { }

    private final Process process;
    private final Path logFile;
    private final long timeoutSeconds;
    private final BufferedWriter input;
    private final ArrayBlockingQueue<Reply> replies = new ArrayBlockingQueue<>(16);
    private final Thread shutdownHook;
    private volatile Exception outputFailure;
    private boolean broken;
    private boolean closed;

    IgniteHelperSession(IgniteClientRuntime.HelperLaunch launch) throws Exception {
        logFile = launch.logFile();
        timeoutSeconds = launch.timeoutSeconds();
        process = launch.builder().start();
        input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        shutdownHook = new Thread(process::destroyForcibly, "ignite-helper-stop-" + process.pid());
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            Thread output = new Thread(this::readOutput, "ignite-helper-output-" + process.pid());
            output.setDaemon(true);
            output.start();
            JsonNode ready = await("ready");
            if (!ready.path("ok").asBoolean() || !"ready".equals(ready.path("result").path("state").asText())) {
                throw new IllegalStateException("Ignite helper does not support session protocol v1; rebuild the selected corporate runtime; inspect " + logFile);
            }
        } catch (Exception failure) {
            broken = true;
            try { terminate(process); } catch (Exception termination) { failure.addSuppressed(termination); }
            if (!process.isAlive()) {
                releaseHook();
                try { input.close(); } catch (IOException closing) { failure.addSuppressed(closing); }
            }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
    }

    public synchronized boolean isUsable() {
        return !closed && !broken && outputFailure == null && process.isAlive();
    }

    public Path logFile() { return logFile; }

    /** Stop and confirm exit before an adapter starts a separate recovery operation. */
    public synchronized void abort() throws Exception {
        broken = true;
        terminate(process);
        closed = true;
        releaseHook();
        try { input.close(); } catch (IOException ignored) { /* Process exit is already confirmed. */ }
    }

    public synchronized JsonNode call(String mode) throws Exception {
        if (mode == null || !mode.matches("[A-Za-z][A-Za-z0-9-]*") || mode.equals("close")) {
            throw new IllegalArgumentException("Invalid Ignite session operation");
        }
        return request(mode);
    }

    private JsonNode request(String mode) throws Exception {
        if (!isUsable()) throw new IllegalStateException("Ignite session unavailable; inspect " + logFile);
        String id = UUID.randomUUID().toString();
        JsonNode reply;
        try {
            input.write(JSON.writeValueAsString(Map.of("protocol", 1, "id", id, "mode", mode)));
            input.newLine();
            input.flush();
            reply = await(id);
        } catch (Exception failure) {
            broken = true;
            try { terminate(process); } catch (Exception termination) { failure.addSuppressed(termination); }
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
        // A command error is a valid reply. Keep the client alive for cleanup after partial setup.
        if (!reply.path("ok").asBoolean()) {
            throw new IllegalStateException("Ignite " + mode + " failed (" + reply.path("errorType").asText()
                    + "); session retained for cleanup; inspect " + logFile);
        }
        if (!reply.path("result").isObject()) {
            broken = true;
            var failure = new IllegalStateException("Invalid Ignite result; inspect " + logFile);
            try { terminate(process); } catch (Exception termination) { failure.addSuppressed(termination); }
            throw failure;
        }
        return reply.get("result");
    }

    private JsonNode await(String id) throws Exception {
        Reply reply = replies.poll(timeoutSeconds, TimeUnit.SECONDS);
        if (reply == null) {
            throw new IllegalStateException("Ignite session operation timed out; inspect " + logFile);
        }
        if (reply.failure() != null) {
            throw new IllegalStateException("Ignite helper exited or protocol unavailable; rebuild an old selected runtime if needed; inspect " + logFile, reply.failure());
        }
        JsonNode payload = reply.payload();
        if (payload.path("protocol").asInt() != 1 || !id.equals(payload.path("id").asText())
                || !payload.path("ok").isBoolean()) {
            throw new IllegalStateException("Unexpected Ignite session reply; inspect " + logFile);
        }
        return payload;
    }

    private void readOutput() {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                var log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.write(line);
                log.newLine();
                log.flush();
                if (line.startsWith(PREFIX)) {
                    JsonNode payload = JSON.readTree(line.substring(PREFIX.length()));
                    if (payload == null || !replies.offer(new Reply(payload, null))) {
                        throw new IOException("Invalid or overflowing Ignite session channel");
                    }
                }
            }
        } catch (Exception failure) {
            outputFailure = failure;
        } finally {
            replies.offer(new Reply(null, outputFailure == null ? new IOException("Ignite helper output ended") : outputFailure));
        }
    }

    /** Never permit a recovery writer while the previous helper may still be executing. */
    static void terminate(Process process) throws Exception {
        boolean interrupted = Thread.interrupted();
        try {
            if (process.isAlive()) process.destroyForcibly();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (process.isAlive() && System.nanoTime() < deadline) {
                try { process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
                catch (InterruptedException interruption) { interrupted = true; }
            }
            if (process.isAlive()) throw new IllegalStateException("Ignite helper still running; recovery must wait for PID " + process.pid());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private void releaseHook() {
        try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
        catch (IllegalStateException ignored) { /* JVM shutdown is already running the hook. */ }
    }

    @Override public synchronized void close() throws Exception {
        if (closed) return;
        Exception failure = null;
        try {
            if (isUsable()) {
                request("close");
                if (!process.waitFor(5, TimeUnit.SECONDS)) terminate(process);
                if (process.exitValue() != 0) throw new IllegalStateException("Ignite session shutdown failed; inspect " + logFile);
            }
        } catch (Exception closing) {
            failure = closing;
        } finally {
            try { terminate(process); }
            catch (Exception termination) {
                if (failure == null) failure = termination; else failure.addSuppressed(termination);
            }
            try { input.close(); }
            catch (IOException closing) {
                if (failure == null) failure = closing; else failure.addSuppressed(closing);
            }
            closed = !process.isAlive();
            if (closed) releaseHook();
        }
        if (failure != null) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure;
        }
    }
}
