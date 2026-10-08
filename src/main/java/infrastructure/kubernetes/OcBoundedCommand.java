package infrastructure.kubernetes;

import config.services.container.KubernetesTunnelSettings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Internal bounded oc executor used by guarded read-only and workload APIs. Never logs command output or kubeconfig contents. */
final class OcBoundedCommand {
    private OcBoundedCommand() { }

    static OcReadOnlyCommand.Result run(KubernetesTunnelSettings settings, List<String> command,
                             long timeoutMillis, int maxBytes) {
        if (timeoutMillis <= 0) return new OcReadOnlyCommand.Result(-1, true, false, 0, "");
        if (maxBytes < 1) throw new IllegalArgumentException("Positive output limit required");
        List<String> args = new ArrayList<>(List.of(settings.ocExecutable,
                "--kubeconfig", settings.kubeconfig().toString(), "--context", settings.context(),
                "-n", settings.namespace, "--request-timeout=" + Math.max(1, timeoutMillis) + "ms"));
        args.addAll(command);
        Process process;
        try { process = new ProcessBuilder(args).redirectErrorStream(true).start(); }
        catch (Exception failure) {
            throw new IllegalStateException("OC_EXECUTABLE_START_FAILED: " + failure.getClass().getSimpleName());
        }
        Buffer buffer = new Buffer(maxBytes);
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = in.read(chunk)) >= 0) {
                    if (count > 0) buffer.append(chunk, count);
                }
            } catch (Exception failure) { buffer.readFailed = true; }
        }, "scheduler-oc-bounded-reader");
        reader.setDaemon(true);
        reader.start();
        boolean timedOut = false;
        int exitCode = -1;
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                timedOut = true;
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
            if (!process.isAlive()) exitCode = process.exitValue();
            reader.join(1000);
            if (reader.isAlive()) timedOut = true;
            if (buffer.readFailed && exitCode == 0) exitCode = -1;
            return buffer.result(exitCode, timedOut);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return buffer.result(-1, true);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
        }
    }

    private static final class Buffer {
        private final int limit;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private boolean truncated;
        private volatile boolean readFailed;
        private Buffer(int limit) { this.limit = limit; }
        private synchronized void append(byte[] bytes, int count) {
            int remaining = Math.max(0, limit - out.size());
            out.write(bytes, 0, Math.min(count, remaining));
            if (count > remaining) truncated = true;
        }
        private synchronized OcReadOnlyCommand.Result result(int exitCode, boolean timedOut) {
            return new OcReadOnlyCommand.Result(exitCode, timedOut, truncated, out.size(), out.toString(StandardCharsets.UTF_8));
        }
    }
}
