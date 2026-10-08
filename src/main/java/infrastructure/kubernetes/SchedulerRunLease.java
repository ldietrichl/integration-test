package infrastructure.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compatibility adapter. The shared protocol retains the existing annotation to interoperate with older runs. */
public final class SchedulerRunLease implements AutoCloseable {
    public static final String KEY = WorkloadRunLease.KEY;
    private final WorkloadRunLease delegate;
    public SchedulerRunLease(Supplier<JsonNode> read, Consumer<String> patch) {
        delegate = new WorkloadRunLease(read, patch);
    }
    public void acquire() { delegate.acquire(); }
    public void requireHeld() { delegate.requireHeld(); }
    public boolean acquired() { return delegate.acquired(); }
    @Override public void close() { delegate.close(); }
}
