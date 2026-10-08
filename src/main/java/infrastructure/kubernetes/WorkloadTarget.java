package infrastructure.kubernetes;

import config.services.core.StandSettings;

/** Service identity is independent of cluster credentials and scenario configuration values. */
public record WorkloadTarget(String workload, String service, int port, String container) {
    public WorkloadTarget {
        if (workload == null || !workload.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Workload profile name required");
        for (String name : new String[]{service, container})
            if (name == null || !name.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
                throw new IllegalArgumentException("Exact service/container name required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Service port out of range");
    }

    public static WorkloadTarget from(StandSettings stand, String workload) {
        if (workload == null || !workload.matches("[a-z][a-z0-9-]*"))
            throw new IllegalArgumentException("Workload profile name required");
        String prefix = "workloads." + workload + ".";
        String service = stand.optional(prefix + "service", stand.optional("services." + workload + ".name", ""));
        return new WorkloadTarget(workload, service, stand.integer(prefix + "service-port", 8080, 1, 65535),
                stand.optional(prefix + "container", service));
    }
}
