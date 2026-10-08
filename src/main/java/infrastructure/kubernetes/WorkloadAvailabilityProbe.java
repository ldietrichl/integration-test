package infrastructure.kubernetes;

/** One read-only sample on the same route used by the business test. Never replay a mutation. */
@FunctionalInterface
public interface WorkloadAvailabilityProbe {
    boolean ready();
}
