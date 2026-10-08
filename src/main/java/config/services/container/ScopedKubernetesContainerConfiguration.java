package config.services.container;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.containers.client.ContainerServiceClient;
import ru.sber.qa.containers.services.ContainerServiceConfiguration;

import java.util.Objects;
import java.util.function.Function;

/** One framework client per configuration instance; the Environment owns its lifetime. */
public final class ScopedKubernetesContainerConfiguration implements ContainerServiceConfiguration, AutoCloseable {
    private final Function<Environment, KubernetesTunnelSettings> resolveSettings;
    private ManagedContainerServiceClient client;
    private String clientName;
    private boolean closed;

    public ScopedKubernetesContainerConfiguration(Function<Environment, KubernetesTunnelSettings> resolveSettings) {
        this.resolveSettings = Objects.requireNonNull(resolveSettings);
    }

    @Override public synchronized ContainerServiceClient getContainerServiceClient(Environment environment, String name) {
        if (closed) throw new IllegalStateException("CONTAINER_SERVICE_CONFIGURATION_CLOSED");
        if (client != null) {
            if (!clientName.equals(name)) throw new IllegalStateException("CONTAINER_SERVICE_ENVIRONMENT_MISMATCH");
            return client;
        }
        KubernetesTunnelSettings settings = resolveSettings.apply(environment);
        settings.requireContainerServiceManagement();
        if (!settings.environment.equals(name)) throw new IllegalStateException("CONTAINER_SERVICE_ENVIRONMENT_MISMATCH");
        ManagedContainerServiceClient created = new ManagedContainerServiceClient(settings.nativeClientConfiguration());
        try { Environment.addAfterAllHook(this::close); }
        catch (RuntimeException | Error failure) {
            try { created.close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
        clientName = name;
        client = created;
        return client;
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        if (client != null) client.close();
    }
}
