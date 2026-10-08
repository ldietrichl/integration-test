package infrastructure.kubernetes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** A loopback listener owned by this test JVM, never an arbitrary process discovered by its port. */
public final class TunnelSession implements AutoCloseable {
    private final String id = UUID.randomUUID().toString();
    private final String transport;
    private final int port;
    private final AutoCloseable resource;
    private final BooleanSupplier alive;
    private final Map<String, Object> target;
    private final AtomicBoolean closed = new AtomicBoolean();

    TunnelSession(String transport, int port, AutoCloseable resource, BooleanSupplier alive) {
        this(transport, port, resource, alive, Map.of());
    }

    TunnelSession(String transport, int port, AutoCloseable resource, BooleanSupplier alive, Map<String, Object> target) {
        this.target = Map.copyOf(target);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid tunnel listener port");
        this.transport = transport;
        this.port = port;
        this.resource = resource;
        this.alive = alive;
    }

    public Map<String, Object> targetDescription() { return target; }

    public String id() { return id; }
    public int localPort() { return port; }
    public String baseUri() { return "http://127.0.0.1:" + port; }
    public boolean isClosed() { return closed.get(); }
    public boolean isAlive() { return !closed.get() && alive.getAsBoolean(); }

    public Map<String, Object> description() {
        return Map.of("session", id, "transport", transport, "baseUri", baseUri(),
                "alive", isAlive(), "closed", isClosed());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { resource.close(); }
        catch (Exception failure) {
            throw new IllegalStateException("Cannot close owned " + transport + " tunnel ("
                    + failure.getClass().getSimpleName() + ")");
        }
    }
}
