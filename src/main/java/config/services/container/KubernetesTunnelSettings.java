package config.services.container;

import config.services.core.TestEnvironment;
import infrastructure.kubernetes.KubernetesDiagnosticException;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.configuration.ConfigurationService;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** DEV/IFT/LT share one mechanism, but never share an implicit cluster/context selection. */
public final class KubernetesTunnelSettings {
    private final Properties properties;
    public final String environment;
    public final String namespace;
    public final String service;
    public final int servicePort;
    public final int localPort;
    public final int startupSeconds;
    public final int requestTimeoutMillis;
    public final int diagnosticReadyTimeoutSeconds;
    public final int diagnosticReadyPollMillis;
    public final String ocExecutable;
    public final String transport;
    public final boolean containerServiceReady;
    public final String authMode;
    public final String expectedUser;
    public final boolean authRequireToken;
    public final String serviceAccount;
    public final String serviceAccountSecret;
    public final List<String> requiredPermissions;
    public final List<String> optionalMissingPermissions;
    public final boolean logsEnabled;
    public final String logsContainer;
    public final int logsTailLines;
    public final int logsMaxBytes;
    public final int logsTimeoutSeconds;
    public final int logsClockSkewSeconds;
    public final boolean tlsDiagnosticsEnabled;

    private KubernetesTunnelSettings(Properties properties) {
        this.properties = properties;
        this.environment = TestEnvironment.current();
        if (!Set.of("dev", "ift", "lt").contains(environment))
            throw new IllegalStateException("Kubernetes diagnostics support only dev/ift/lt from test.properties");
        namespace = dnsName(required("namespace"), "namespace");
        service = dnsName(optional("service", "scheduler-service"), "service");
        servicePort = integer("service-port", 8080, 1, 65535);
        localPort = integer("local-port", 0, 0, 65535);
        startupSeconds = integer("startup-timeout.seconds", 25, 1, 120);
        requestTimeoutMillis = integer("request-timeout.ms", 10000, 1000, 60000);
        diagnosticReadyTimeoutSeconds = integer("diagnostics.ready.timeout.seconds", 60, 10, 120);
        diagnosticReadyPollMillis = integer("diagnostics.ready.poll.ms", 2000, 250, 5000);
        ocExecutable = optional("oc.executable", "oc.exe");
        containerServiceReady = flag("container-service.ready", false);
        authMode = optional("auth.mode", "kubeconfig");
        if (!Set.of("kubeconfig", "service-account-token").contains(authMode))
            throw new IllegalStateException("kubernetes.<env>.auth.mode must be kubeconfig or service-account-token");
        expectedUser = optional("auth.expected-user", "");
        if (!expectedUser.isBlank() && !expectedUser.matches("[A-Za-z0-9._@-]{1,128}"))
            throw new IllegalStateException("kubernetes.<env>.auth.expected-user contains unsupported characters");
        authRequireToken = flag("auth.require-token", false);
        serviceAccount = optional("service-account", "");
        serviceAccountSecret = optional("service-account.secret", "");
        if ("service-account-token".equals(authMode) && serviceAccount.isBlank())
            throw new IllegalStateException("Configure kubernetes." + environment + ".service-account");
        requiredPermissions = csv(optional("permissions.required",
                "get/list/delete pods,get pods/log,get/list deployments.apps,get/list configmaps,patch configmaps"));
        optionalMissingPermissions = csv(optional("permissions.optional-missing",
                "create pods/exec,create pods/portforward,get/list endpoints"));
        logsEnabled = flag("logs.enabled", false);
        logsContainer = dnsName(optional("logs.container", "scheduler-service"), "log container");
        logsTailLines = integer("logs.tail.lines", 500, 1, 5000);
        logsMaxBytes = integer("logs.max.bytes", 131072, 4096, 1048576);
        logsTimeoutSeconds = integer("logs.timeout.seconds", 15, 1, 60);
        logsClockSkewSeconds = integer("logs.clock-skew.seconds", 3, 0, 60);
        tlsDiagnosticsEnabled = flag("tls.diagnostics.enabled", true);
        transport = optional("transport", "fabric8");
        if (!Set.of("oc", "fabric8").contains(transport))
            throw new IllegalStateException("kubernetes.<env>.transport or kubernetes.transport must be oc or fabric8");
        if (containerServiceReady && !"fabric8".equals(transport))
            throw new IllegalStateException("ContainerService management requires kubernetes.<env>.transport=fabric8");
    }

    public static KubernetesTunnelSettings from(Environment environment) {
        Properties properties = environment.getService(ConfigurationService.class)
                .getProperties(new KubernetesTunnelConfigScope());
        return new KubernetesTunnelSettings(properties);
    }

    /** Isolated diagnostics reuse the same strict context and TLS validation. */
    public static KubernetesTunnelSettings fromProperties(Properties properties) {
        Properties snapshot = new Properties();
        snapshot.putAll(properties);
        return new KubernetesTunnelSettings(snapshot);
    }

    /** User-service dependency stays in the same explicit cluster and scheduler namespace. */
    public KubernetesTunnelSettings userSettings() {
        config.services.core.StandSettings stand = new config.services.core.StandSettings();
        Properties copy = new Properties();
        copy.putAll(properties);
        String prefix = "kubernetes." + environment + ".";
        copy.setProperty(prefix + "namespace", namespace);
        copy.setProperty(prefix + "service", stand.optional("kubernetes.user.service", "user-service"));
        copy.setProperty(prefix + "service-port",
                Integer.toString(stand.integer("kubernetes.user.service-port", 8080, 1, 65535)));
        copy.setProperty(prefix + "local-port", "0");
        copy.setProperty(prefix + "logs.enabled", "false");
        return new KubernetesTunnelSettings(copy);
    }

    /** A second owned loopback tunnel; no gateway fallback or second cluster selection. */
    public KubernetesTunnelSettings dictionarySettings() {
        Properties copy = new Properties();
        copy.putAll(properties);
        String prefix = "kubernetes." + environment + ".";
        copy.setProperty(prefix + "namespace", optional("dictionary.namespace", namespace));
        copy.setProperty(prefix + "service", optional("dictionary.service", "dictionary-service"));
        copy.setProperty(prefix + "service-port",
                Integer.toString(integer("dictionary.service-port", 8080, 1, 65535)));
        copy.setProperty(prefix + "local-port", "0");
        // Scheduler log attribution must never switch to the dictionary pod.
        copy.setProperty(prefix + "logs.enabled", "false");
        return new KubernetesTunnelSettings(copy);
    }

    /** Reuse the same approved cluster/namespace identity for another service in this workload. */
    public KubernetesTunnelSettings withService(String serviceName, int port) {
        return withService(serviceName, port, serviceName);
    }

    public KubernetesTunnelSettings withService(String serviceName, int port, String container) {
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("Service port must be in 1..65535");
        Properties copy = new Properties();
        copy.putAll(properties);
        String prefix = "kubernetes." + environment + ".";
        copy.setProperty(prefix + "service", dnsName(serviceName, "service"));
        copy.setProperty(prefix + "service-port", Integer.toString(port));
        copy.setProperty(prefix + "local-port", "0");
        copy.setProperty(prefix + "logs.container", dnsName(container, "container"));
        return new KubernetesTunnelSettings(copy);
    }

    public String context() { return required("context"); }

    public Path kubeconfig() {
        String configured = optional("kubeconfig", "");
        if (configured.isBlank()) {
            configured = System.getenv("KUBECONFIG");
            if (configured != null && configured.contains(File.pathSeparator))
                throw configurationFailure("KUBECONFIG_MULTIPLE_FILES");
        }
        Path path = configured == null || configured.isBlank()
                ? Path.of(System.getProperty("user.home"), ".kube", "config") : Path.of(configured);
        path = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path))
            throw configurationFailure("KUBECONFIG_FILE_MISSING");
        return path;
    }

    /** Never return this object to Allure or a logger: Config contains credentials. */
    public Config clientConfiguration() {
        try {
            Path file = kubeconfig();
            String kubeconfigContent = Files.readString(file);
            Config parsed = Config.fromKubeconfig(context(), kubeconfigContent, file.toString());
            if (parsed.getCurrentContext() == null || !context().equals(parsed.getCurrentContext().getName()))
                throw new IllegalStateException("KUBECONFIG_CONTEXT: explicitly selected context was not resolved");
            if (!server(required("api-server")).equals(server(parsed.getMasterUrl())))
                throw new IllegalStateException("KUBECONFIG_CLUSTER: selected context does not match the expected DEV/IFT/LT API server");
            if (parsed.isTrustCerts() || parsed.isDisableHostnameVerification())
                throw new IllegalStateException("KUBECONFIG_TLS: install the cluster CA; insecure certificate/hostname verification is not accepted");

            io.fabric8.kubernetes.api.model.Config rawConfig =
                    KubeConfigUtils.parseConfigFromString(kubeconfigContent);
            if (rawConfig == null || rawConfig.getContexts() == null)
                throw new IllegalStateException("KUBECONFIG_CONTEXT: kubeconfig has no contexts");
            var selectedNamedContext = rawConfig.getContexts().stream()
                    .filter(item -> item != null && context().equals(item.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "KUBECONFIG_CONTEXT: explicitly selected context was not found"));
            if (selectedNamedContext.getContext() == null)
                throw new IllegalStateException(
                        "KUBECONFIG_CONTEXT: explicitly selected context has no configuration");
            String selectedUser = selectedNamedContext.getContext().getUser();
            if (!expectedUser.isBlank() && !matchesExpectedUser(selectedUser, expectedUser))
                throw new IllegalStateException(
                        "KUBECONFIG_USER_MISMATCH: active context does not belong to the approved user");
            String activeToken = KubeConfigUtils.getUserToken(rawConfig, selectedNamedContext.getContext());
            if (("service-account-token".equals(authMode) || authRequireToken)
                    && (activeToken == null || activeToken.isBlank()))
                throw new IllegalStateException("service-account-token".equals(authMode)
                        ? "KUBECONFIG_AUTH: service-account-token mode requires a token in the active kubeconfig user"
                        : "KUBECONFIG_USER_AUTH_MISSING: approved user kubeconfig requires a token in the active user");

            ConfigBuilder builder = new ConfigBuilder(parsed)
                    .withNamespace(namespace)
                    .withTrustCerts(false)
                    .withDisableHostnameVerification(false)
                    .withConnectionTimeout(requestTimeoutMillis)
                    .withRequestTimeout(requestTimeoutMillis);
            if (activeToken != null && !activeToken.isBlank()) builder.withOauthToken(activeToken);
            return builder.build();
        } catch (Exception failure) {
            // Parser/auth-provider exceptions can contain kubeconfig fragments. Do not attach causes.
            if (failure instanceof KubernetesDiagnosticException safe) throw safe;
            if (failure instanceof IllegalStateException && failure.getMessage() != null
                    && failure.getMessage().startsWith("KUBECONFIG_")) throw (IllegalStateException) failure;
            throw new IllegalStateException("KUBECONFIG_LOAD: check file, explicit context and authentication; details suppressed to protect credentials ("
                    + failure.getClass().getSimpleName() + ")");
        }
    }

    /** Native Java trust is explicit; oc keeps its own verified kubeconfig/OS trust path. */
    public Config nativeClientConfiguration() {
        Config parsed = clientConfiguration();
        String prefix = "kubernetes." + environment + ".tls.";
        String resource = properties.getProperty(prefix + "ca-resource", "").trim();
        if (resource.isBlank()) return parsed;
        String approvedServer = properties.getProperty(prefix + "ca-api-server", "").trim();
        String fingerprints = properties.getProperty(prefix + "ca-sha256", "").trim();
        if (approvedServer.isBlank() || !server(approvedServer).equals(server(parsed.getMasterUrl())))
            throw new IllegalStateException("KUBECONFIG_CA_TARGET: CA bundle is not approved for the selected API origin");
        return KubernetesCaTrust.withApprovedResource(parsed, resource, fingerprints);
    }

    public boolean tlsVerificationEnabled() {
        Config config = clientConfiguration();
        return !config.isTrustCerts() && !config.isDisableHostnameVerification();
    }

    public Map<String, Object> description() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("apiServer", required("api-server"));
        result.put("context", context());
        result.put("namespace", namespace);
        result.put("service", service);
        result.put("servicePort", servicePort);
        result.put("apiTransport", transport);
        result.put("containerServiceReady", containerServiceReady);
        result.put("authenticationMode", authMode);
        result.put("expectedUser", expectedUser.isBlank() ? "not-configured" : expectedUser);
        result.put("tokenRequired", authRequireToken || "service-account-token".equals(authMode));
        result.put("serviceAccount", "service-account-token".equals(authMode)
                ? (serviceAccount.isBlank() ? "not-configured" : serviceAccount) : "not-used");
        result.put("requiredPermissions", requiredPermissions);
        result.put("optionalMissingPermissions", optionalMissingPermissions);
        result.put("serviceLogsEnabled", logsEnabled);
        result.put("tlsDiagnosticsEnabled", tlsDiagnosticsEnabled);
        result.put("localPort", localPort == 0 ? "automatic" : localPort);
        result.put("bindAddress", "127.0.0.1");
        result.put("authentication", "credential-bearing kubeconfig; token value is never logged");
        result.put("tlsVerification", "required");
        return result;
    }

    private String optional(String key, String fallback) {
        return properties.getProperty("kubernetes." + environment + "." + key,
                properties.getProperty("kubernetes." + key, fallback)).trim();
    }

    private String required(String key) {
        String value = optional(key, "");
        if (value.isBlank() || value.contains("<SET_ME_") || value.contains("${"))
            throw configurationFailure("context".equals(key) ? "KUBECONFIG_CONTEXT_MISSING"
                    : "KUBERNETES_REQUIRED_SETTING_MISSING");
        return value;
    }

    private boolean flag(String key, boolean fallback) {
        String value = optional(key, Boolean.toString(fallback));
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalStateException("kubernetes." + key + " must be true or false");
        return Boolean.parseBoolean(value);
    }

    private int integer(String key, int fallback, int min, int max) {
        int value = Integer.parseInt(optional(key, Integer.toString(fallback)));
        if (value < min || value > max) throw new IllegalStateException("Invalid kubernetes setting: " + key);
        return value;
    }

    public void requireContainerServiceManagement() {
        requireNativeManagement();
        if (!requiredPermissions.contains("create pods/portforward"))
            throw configurationFailure("KUBERNETES_PORTFORWARD_PERMISSION_NOT_DECLARED");
    }

    public void requireNativeManagement() {
        if (!containerServiceReady)
            throw configurationFailure("KUBERNETES_MANAGEMENT_NOT_CONFIRMED");
        if (!"fabric8".equals(transport))
            throw configurationFailure("KUBERNETES_FABRIC8_REQUIRED");
    }

    private static KubernetesDiagnosticException configurationFailure(String code) {
        // Only a fixed diagnostic code crosses the reporting boundary, never property values.
        return new KubernetesDiagnosticException(code, "LOCAL_CONFIGURATION", null);
    }

    private static boolean matchesExpectedUser(String selectedUser, String expectedUser) {
        if (selectedUser == null || selectedUser.isBlank()) return false;
        return selectedUser.equals(expectedUser)
                || selectedUser.startsWith(expectedUser + "/")
                || selectedUser.endsWith("/" + expectedUser);
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static String dnsName(String value, String setting) {
        if (!value.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))
            throw new IllegalStateException("Expected an exact Kubernetes " + setting + ", without quotes, suffixes or selectors");
        return value;
    }

    private static String server(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath())))
            throw new IllegalStateException("KUBECONFIG_CLUSTER: expected an HTTPS API origin without credentials");
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        return "https://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
    }
}
