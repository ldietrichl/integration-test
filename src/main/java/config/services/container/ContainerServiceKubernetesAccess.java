package config.services.container;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.perfeccionista.framework.Environment;
import infrastructure.kubernetes.KubernetesDiagnosticException;
import ru.sber.qa.containers.client.ContainerServiceClient;
import ru.sber.qa.containers.services.ContainerService;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves framework clients and explicitly owned workload sessions using the same ContainerService configuration. */
public final class ContainerServiceKubernetesAccess {
    private ContainerServiceKubernetesAccess() { }

    /** Native sessions do not invoke ContainerService actions or require a thread-local Environment. */
    public static KubernetesClient ownedNativeWorkloadClient(KubernetesTunnelSettings settings) {
        settings.requireNativeManagement();
        KubernetesClient client;
        try {
            client = new io.fabric8.kubernetes.client.KubernetesClientBuilder()
                    .withConfig(KubernetesCaTrust.requireNativeTrust(settings.nativeClientConfiguration())).build();
        } catch (RuntimeException failure) {
            throw safeFailure(settings, "CREATE_WORKLOAD_CLIENT", failure);
        }
        try {
            if (!settings.namespace.equals(client.getNamespace()))
                throw new IllegalStateException("WORKLOAD_NAMESPACE_MISMATCH");
            return client;
        } catch (RuntimeException | Error failure) {
            try { client.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static ContainerServiceClient frameworkClient(KubernetesTunnelSettings settings) {
        settings.requireContainerServiceManagement();
        return Environment.getForCurrentThread().getService(ContainerService.class)
                .containerServiceClient(settings.environment);
    }

    public static KubernetesClient client(KubernetesTunnelSettings settings) {
        return client(settings, frameworkClient(settings));
    }

    /** Owns both native requests and ContainerService log reads until workload rollback finishes. */
    public static ManagedContainerServiceClient ownedWorkloadClient(KubernetesTunnelSettings settings) {
        settings.requireContainerServiceManagement();
        ManagedContainerServiceClient owned = new ManagedContainerServiceClient(settings.nativeClientConfiguration());
        try {
            client(settings, owned); // Apply the same exact cluster/namespace checks as framework clients.
            return owned;
        } catch (RuntimeException | Error failure) {
            try { owned.close(); }
            catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    private static KubernetesClient client(KubernetesTunnelSettings settings, ContainerServiceClient framework) {
        List<KubernetesClient> clients = framework.getClients();
        if (clients.size() != 1)
            throw new IllegalStateException("CONTAINER_SERVICE_CLUSTER_COUNT: exactly one approved cluster is required");
        KubernetesClient client = clients.get(0);
        if (!settings.namespace.equals(client.getNamespace()))
            throw new IllegalStateException("CONTAINER_SERVICE_NAMESPACE_MISMATCH");
        if (!origin(settings.clientConfiguration().getMasterUrl()).equals(origin(client.getMasterUrl().toString())))
            throw new IllegalStateException("CONTAINER_SERVICE_API_SERVER_MISMATCH");
        return client;
    }

    public static Map<String, Object> safeDiagnostics(KubernetesTunnelSettings settings, KubernetesClient client) {
        Map<String, Object> result = new LinkedHashMap<>(settings.description());
        result.put("framework", "ContainerService/ContainerServiceFlow");
        result.put("nativeClient", client.getClass().getSimpleName());
        result.put("fabric8ClientVersion", implementationVersion(KubernetesClient.class));
        result.put("fabric8ModelVersion", implementationVersion(io.fabric8.kubernetes.api.model.apps.Deployment.class));
        result.put("jacksonDatabindVersion", implementationVersion(ObjectMapper.class));
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("apiOrigin", origin(client.getMasterUrl().toString()));
        result.put("tlsVerificationEnabled", !client.getConfiguration().isTrustCerts()
                && !client.getConfiguration().isDisableHostnameVerification());
        result.put("clientCaMaterialPresent", hasConfiguredValue(client.getConfiguration(),
                "getCaCertFile", "getCaCertData"));
        result.put("explicitJavaTrustStorePresent", hasText(System.getProperty("javax.net.ssl.trustStore")));
        result.put("proxyConfigured", proxyConfigured(client.getConfiguration()));
        result.put("credentialMaterial", "present only in local kubeconfig; never attached");
        return result;
    }

    /** Safe transport evidence only: exception messages and credential-bearing objects are never returned. */
    public static Map<String, Object> failureDiagnostics(
            KubernetesTunnelSettings settings, KubernetesClient client, Throwable failure) {
        Map<String, Object> result = new LinkedHashMap<>(safeDiagnostics(settings, client));
        result.putAll(safeFailure(settings, "KUBERNETES_REQUEST", failure).safeDetails());
        result.putAll(mappingDiagnostics(failure));
        result.put("diagnosticFrame", firstDiagnosticFrame(failure));
        result.put("exceptionMessages", "suppressed");
        return result;
    }

    public static KubernetesDiagnosticException safeFailure(
            KubernetesTunnelSettings settings, String operation, Throwable failure) {
        return new KubernetesDiagnosticException(failureCode(settings, failure), operation, failure);
    }

    public static String failureCode(Throwable failure) {
        KubernetesDiagnosticException safe = KubernetesDiagnosticException.find(failure);
        if (safe != null) return safe.failureCode();
        Integer httpStatus = null;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof KubernetesClientException clientFailure) {
                if (clientFailure.getCode() == 401) return "AUTH_REQUIRED";
                if (clientFailure.getCode() == 403) return "RBAC_FORBIDDEN";
                if (clientFailure.getCode() == 409) return "RESOURCE_VERSION_CONFLICT";
                if (clientFailure.getCode() == 404) return "RESOURCE_NOT_FOUND";
                if (httpStatus == null) httpStatus = clientFailure.getCode();
            }
        }
        String transport = transportCategory(failure);
        if (transport != null) return transport;
        if (httpStatus != null && httpStatus >= 0) return "KUBERNETES_HTTP_" + httpStatus;
        return httpStatus == null ? "CONTAINER_SERVICE_FAILURE" : "TRANSPORT_UNCLASSIFIED";
    }

    public static String failureCode(KubernetesTunnelSettings settings, Throwable failure) {
        String code = failureCode(failure);
        if ("AUTH_REQUIRED".equals(code)
                && "kubeconfig".equals(settings.authMode)
                && !settings.expectedUser.isBlank())
            return "KUBECONFIG_USER_AUTH_REJECTED";
        return code;
    }

    private static String transportCategory(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String type = current.getClass().getSimpleName();
            if (current instanceof java.security.cert.CertPathBuilderException
                    || current instanceof java.security.cert.CertPathValidatorException)
                return "TLS_TRUST";
            if (current instanceof javax.net.ssl.SSLHandshakeException) return "TLS_HANDSHAKE";
            if (current instanceof javax.net.ssl.SSLException) return "TLS_FAILURE";
            if (current instanceof java.security.cert.CertificateException) return "TLS_CERTIFICATE";
            if (current instanceof java.net.UnknownHostException) return "DNS_FAILURE";
            if (type.toLowerCase(java.util.Locale.ROOT).contains("proxy")) return "PROXY_FAILURE";
            if (current instanceof java.net.NoRouteToHostException) return "NETWORK_ROUTE_FAILURE";
            if (current instanceof java.net.ConnectException) return "CONNECT_FAILURE";
            if (current instanceof java.net.SocketTimeoutException
                    || type.toLowerCase(java.util.Locale.ROOT).contains("timeout")) return "TIMEOUT";
            if (current instanceof java.net.ProtocolException) return "PROTOCOL_FAILURE";
            if (current instanceof java.net.SocketException) return "SOCKET_FAILURE";
            if (current instanceof java.io.EOFException) return "EOF_FAILURE";
            if (current instanceof java.io.InterruptedIOException) return "IO_TIMEOUT";
            if (current instanceof java.io.IOException) return "IO_FAILURE";
        }
        return null;
    }

    private static boolean proxyConfigured(Object configuration) {
        return hasConfiguredValue(configuration, "getHttpProxy", "getHttpsProxy")
                || hasText(System.getProperty("http.proxyHost"))
                || hasText(System.getProperty("https.proxyHost"))
                || hasText(System.getenv("HTTP_PROXY"))
                || hasText(System.getenv("HTTPS_PROXY"));
    }

    private static Map<String, Object> mappingDiagnostics(Throwable failure) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (!(current instanceof JsonMappingException mappingFailure)) continue;
            List<Map<String, Object>> path = new ArrayList<>();
            for (JsonMappingException.Reference reference : mappingFailure.getPath()) {
                Map<String, Object> item = new LinkedHashMap<>();
                Object from = reference.getFrom();
                item.put("fromType", from instanceof Class<?> type
                        ? type.getName()
                        : from == null ? "unknown" : from.getClass().getName());
                if (reference.getFieldName() != null) item.put("field", reference.getFieldName());
                else if (reference.getIndex() >= 0) item.put("index", reference.getIndex());
                path.add(item);
            }
            result.put("mappingPath", path);
            if (mappingFailure.getLocation() != null) {
                result.put("mappingLine", mappingFailure.getLocation().getLineNr());
                result.put("mappingColumn", mappingFailure.getLocation().getColumnNr());
            }
            return result;
        }
        result.put("mappingPath", List.of());
        return result;
    }

    private static Map<String, Object> firstDiagnosticFrame(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (StackTraceElement frame : current.getStackTrace()) {
                String className = frame.getClassName();
                if (!className.startsWith("io.fabric8.")
                        && !className.startsWith("com.fasterxml.jackson.")
                        && !className.startsWith("infrastructure.")
                        && !className.startsWith("config.services.container.")) continue;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("class", className);
                result.put("method", frame.getMethodName());
                result.put("file", Objects.toString(frame.getFileName(), "unknown"));
                result.put("line", frame.getLineNumber());
                return result;
            }
        }
        return Map.of();
    }

    private static String implementationVersion(Class<?> type) {
        Package sourcePackage = type.getPackage();
        return sourcePackage == null || sourcePackage.getImplementationVersion() == null
                ? "unknown" : sourcePackage.getImplementationVersion();
    }

    private static boolean hasConfiguredValue(Object target, String... accessors) {
        if (target == null) return false;
        for (String accessor : accessors) {
            try {
                Object value = target.getClass().getMethod(accessor).invoke(target);
                if (value instanceof CharSequence text && !text.toString().isBlank()) return true;
                if (value instanceof java.util.Collection<?> values && !values.isEmpty()) return true;
                if (value != null && value.getClass().isArray()
                        && java.lang.reflect.Array.getLength(value) > 0) return true;
            } catch (ReflectiveOperationException ignored) {
                // Fabric8 versions expose a slightly different set of safe configuration accessors.
            }
        }
        return false;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String origin(String value) {
        URI uri = URI.create(value);
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
    }
}
