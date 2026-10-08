package config.services.container;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import ru.sber.qa.containers.client.ContainerServiceClient;

import java.util.List;
import java.util.Map;

/** Owns the clients allocated by Platform V AT, instead of allocating another list on every getter. */
public final class ManagedContainerServiceClient extends ContainerServiceClient implements AutoCloseable {
    // No initializer: the parent constructor invokes getClients() and populates this field.
    private List<KubernetesClient> managedClients;
    private boolean closed;

    public ManagedContainerServiceClient(Config configuration) {
        super(Map.of("firstConfig", KubernetesCaTrust.requireNativeTrust(configuration)));
    }

    @Override
    public synchronized List<KubernetesClient> getClients() {
        if (closed) throw new IllegalStateException("Kubernetes client has been closed");
        if (managedClients == null) managedClients = List.copyOf(super.getClients());
        return managedClients;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException problem = null;
        if (managedClients != null) {
            for (KubernetesClient client : managedClients) {
                try { client.close(); }
                catch (RuntimeException failure) {
                    problem = new IllegalStateException("Failed to close an owned Kubernetes client ("
                            + failure.getClass().getSimpleName() + ")");
                }
            }
        }
        if (problem != null) throw problem;
    }
}
