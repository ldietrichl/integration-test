package infrastructure.kubernetes;

/** Captured service-specific readiness policy, usable after the test Environment closes. */
public interface WorkloadReadinessProbe {
    boolean ingress();
    void verify(String ownedLoopback);
}
