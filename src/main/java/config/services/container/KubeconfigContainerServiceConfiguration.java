package config.services.container;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.containers.client.ContainerServiceClient;
import ru.sber.qa.containers.services.ContainerServiceConfiguration;

import java.util.HashMap;
import java.util.Map;

/** One owned Fabric8 client per environment thread; never one client per service lookup. */
public final class KubeconfigContainerServiceConfiguration implements ContainerServiceConfiguration {
    private static final ThreadLocal<Map<String, ManagedContainerServiceClient>> CLIENTS =
            ThreadLocal.withInitial(HashMap::new);

    @Override
    public ContainerServiceClient getContainerServiceClient(Environment environment, String name) {
        KubernetesTunnelSettings settings = KubernetesTunnelSettings.from(environment);
        if (!settings.environment.equals(name))
            throw new IllegalStateException("Container client must use the same dev/ift/lt selector as REST and DB");
        return CLIENTS.get().computeIfAbsent(name, ignored ->
                new ManagedContainerServiceClient(settings.nativeClientConfiguration()));
    }

    public static void closeOwnedClients() {
        RuntimeException failure = null;
        try {
            for (ManagedContainerServiceClient client : CLIENTS.get().values()) {
                try { client.close(); }
                catch (RuntimeException error) {
                    failure = new IllegalStateException("CONTAINER_CLIENT_CLOSE: "
                            + error.getClass().getSimpleName());
                }
            }
        } finally {
            CLIENTS.remove();
        }
        if (failure != null) throw failure;
    }
}