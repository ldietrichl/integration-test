package config.services.container;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.containers.client.ContainerServiceClient;
import ru.sber.qa.containers.services.ContainerServiceConfiguration;

import java.util.HashMap;
import java.util.Map;

/** Uses the real framework client, with verified kubeconfig TLS and explicit ownership. */
public final class DiagnosticContainerServiceConfiguration implements ContainerServiceConfiguration {
    private static final ThreadLocal<Map<String, ManagedContainerServiceClient>> CLIENTS =
            ThreadLocal.withInitial(HashMap::new);

    @Override public ContainerServiceClient getContainerServiceClient(Environment environment, String name) {
        ContainerDiagnosticsSettings settings = ContainerDiagnosticsSettings.from(environment);
        if (!settings.environment.equals(name))
            throw new IllegalStateException("DIAGNOSTIC_CONFIG: client name must equal the selected stand");
        return CLIENTS.get().computeIfAbsent(name, ignored ->
                new ManagedContainerServiceClient(settings.connection().nativeClientConfiguration()));
    }

    public static void closeOwnedClients() {
        RuntimeException failure = null;
        try {
            for (ManagedContainerServiceClient client : CLIENTS.get().values()) {
                try { client.close(); }
                catch (RuntimeException error) {
                    failure = new IllegalStateException("DIAGNOSTIC_CLIENT_CLOSE: " + error.getClass().getSimpleName());
                }
            }
        } finally { CLIENTS.remove(); }
        if (failure != null) throw failure;
    }
}
