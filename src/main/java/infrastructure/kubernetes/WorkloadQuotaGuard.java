package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Conservative preflight using admitted pod resources, including injected sidecars and init containers. */
final class WorkloadQuotaGuard {
    private static final Pattern QUANTITY = Pattern.compile(
            "^([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+))([eE][+-]?[0-9]+|[numkKMGTPE]|[KMGTPE]i)?$");
    private WorkloadQuotaGuard() {}
    static void requireCapacity(JsonNode pod, JsonNode quotas, int additional) {
        if (additional <= 0) return;
        if (!quotas.path("items").isArray()) throw new IllegalStateException("ResourceQuota list required");
        for (JsonNode init : pod.at("/spec/initContainers"))
            if ("Always".equals(init.path("restartPolicy").asText()))
                throw new IllegalStateException("QUOTA_PREFLIGHT_UNSUPPORTED: restartable init sidecar requires explicit accounting");
        List<String> notes = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        for (JsonNode quota : quotas.path("items")) {
            JsonNode hard = quota.at("/status/hard"), used = quota.at("/status/used");
            if (!hard.isObject() || !used.isObject())
                throw new IllegalStateException("QUOTA_PREFLIGHT_UNAVAILABLE: ResourceQuota status has not been populated");
            Iterator<Map.Entry<String,JsonNode>> entries = hard.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                String resource = entry.getKey();
                BigDecimal perPod;
                if (resource.equals("pods") || resource.equals("count/pods")) perPod = BigDecimal.ONE;
                else {
                    String mode;
                    String name;
                    if (resource.startsWith("requests.")) { mode = "requests"; name = resource.substring(9); }
                    else if (resource.startsWith("limits.")) { mode = "limits"; name = resource.substring(7); }
                    else if (resource.equals("cpu") || resource.equals("memory")) { mode = "requests"; name = resource; }
                    else continue; // Deployment scaling does not create another Service, PVC or Secret.
                    if (name.equals("storage")) continue;
                    perPod = podResource(pod, mode, name);
                }
                if (!used.has(resource))
                    throw new IllegalStateException("QUOTA_PREFLIGHT_UNAVAILABLE: missing used amount for " + resource);
                BigDecimal available = quantity(entry.getValue().asText()).subtract(quantity(used.path(resource).asText()));
                BigDecimal needed = perPod.multiply(BigDecimal.valueOf(additional));
                String note = quota.at("/metadata/name").asText() + ": " + resource
                        + " available=" + available.toPlainString() + " additional=" + needed.toPlainString();
                notes.add(note);
                // Scoped quotas are applied conservatively; no attempt to bypass a scope restriction.
                if (needed.compareTo(available) > 0) blocked.add(note);
            }
        }
        Allure.addAttachment("Scale quota preflight", String.join("\n", notes)
                + "\nCPU is measured in cores; memory/storage in bytes. No replica change dispatched yet."
                + "\nCapacity can still change concurrently; admission and Ready checks remain mandatory.");
        if (!blocked.isEmpty())
            throw new IllegalStateException("SCALE_QUOTA_INSUFFICIENT: no replica change dispatched; " + String.join("; ", blocked));
    }
    private static BigDecimal podResource(JsonNode pod, String mode, String resource) {
        BigDecimal regular = BigDecimal.ZERO, init = BigDecimal.ZERO;
        for (JsonNode container : pod.at("/spec/containers"))
            regular = regular.add(containerResource(container, mode, resource));
        for (JsonNode container : pod.at("/spec/initContainers"))
            init = init.max(containerResource(container, mode, resource));
        JsonNode overhead = pod.at("/spec/overhead").path(resource);
        return regular.max(init).add(overhead.isMissingNode() ? BigDecimal.ZERO : quantity(overhead.asText()));
    }
    private static BigDecimal containerResource(JsonNode container, String mode, String resource) {
        JsonNode value = container.path("resources").path(mode).path(resource);
        if (value.isMissingNode() && mode.equals("requests"))
            value = container.path("resources").path("limits").path(resource);
        return value.isMissingNode() ? BigDecimal.ZERO : quantity(value.asText());
    }
    private static BigDecimal quantity(String text) {
        Matcher match = QUANTITY.matcher(text);
        if (!match.matches()) throw new IllegalArgumentException("Unsupported Kubernetes quantity; preflight stops");
        BigDecimal number = new BigDecimal(match.group(1));
        String unit = match.group(2);
        if (unit == null) return number;
        if (unit.matches("[eE][+-]?[0-9]+")) return number.scaleByPowerOfTen(Integer.parseInt(unit.substring(1)));
        if (unit.endsWith("i")) {
            int power = "KMGTPE".indexOf(unit.charAt(0)) + 1;
            return number.multiply(BigDecimal.valueOf(1024).pow(power));
        }
        int exponent = switch (unit) {
            case "n" -> -9; case "u" -> -6; case "m" -> -3; case "k", "K" -> 3;
            case "M" -> 6; case "G" -> 9; case "T" -> 12; case "P" -> 15; case "E" -> 18;
            default -> throw new IllegalArgumentException("Unsupported quantity unit");
        };
        return number.scaleByPowerOfTen(exponent);
    }
}
