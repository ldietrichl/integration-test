package infrastructure.kubernetes;

import config.services.container.KubernetesTunnelSettings;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Short, bounded read-only oc calls. Never logs command output or kubeconfig contents. */
public final class OcReadOnlyCommand {
    private OcReadOnlyCommand() { }

    public record Result(int exitCode, boolean timedOut, boolean truncated, int bytes, String output) {
        public boolean successful() { return exitCode == 0 && !timedOut && !truncated; }
        public String failureCode() {
            if (timedOut) return "OC_TIMEOUT";
            if (truncated) return "OC_OUTPUT_LIMIT";
            if (output.toLowerCase(java.util.Locale.ROOT).contains("exceeded quota")) return "RESOURCE_QUOTA_EXCEEDED";
            if (output.contains("Forbidden") || output.contains("forbidden")) return "RBAC_FORBIDDEN";
            String lower = output.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("unauthorized") || lower.contains("must be logged in")
                    || lower.contains("provide credentials")) return "AUTH_REQUIRED";
            if (output.contains("NotFound") || output.contains("not found")) return "RESOURCE_NOT_FOUND";
            return exitCode == 0 ? "OK" : "OC_COMMAND_FAILED";
        }
    }

    public static Result run(KubernetesTunnelSettings settings, List<String> command,
                             long timeoutMillis, int maxBytes) {
        if (command.isEmpty() || !(command.get(0).equals("get") || command.get(0).equals("logs")))
            throw new IllegalArgumentException("Only read-only get/logs commands are supported");
        return OcBoundedCommand.run(settings, command, timeoutMillis, maxBytes);
    }
}
