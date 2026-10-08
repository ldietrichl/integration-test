package infrastructure.kubernetes;

import io.fabric8.kubernetes.client.Config;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Observes public CA metadata and JVM defaults. Never installs certificates or reads private keys. */
public final class KubernetesTlsDiagnostics {
    private KubernetesTlsDiagnostics() { }

    public static Map<String, Object> snapshot(Config config, String configSource) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configSource", configSource);
        out.put("javaVersion", System.getProperty("java.version", ""));
        out.put("javaVendor", System.getProperty("java.vendor", ""));
        out.put("javaHome", System.getProperty("java.home", ""));
        out.put("defaultKeyStoreType", KeyStore.getDefaultType());
        out.put("jvmTrustStoreType", System.getProperty("javax.net.ssl.trustStoreType", "(default)"));
        out.put("trustManagerAlgorithm", TrustManagerFactory.getDefaultAlgorithm());
        out.put("securityProviders", Arrays.stream(Security.getProviders()).map(p -> p.getName()).toList());
        String configuredJvmStore = System.getProperty("javax.net.ssl.trustStore", "");
        out.put("jvmTrustStoreProperty", configuredJvmStore.isBlank() ? "(not set)" : configuredJvmStore);
        try {
            Path security = Path.of(System.getProperty("java.home"), "lib", "security");
            Path candidate = Files.isRegularFile(security.resolve("jssecacerts"))
                    ? security.resolve("jssecacerts") : security.resolve("cacerts");
            out.put("jsseDefaultFileCandidate", fileMetadata(candidate.toString()));
            if (!configuredJvmStore.isBlank() && !"NONE".equals(configuredJvmStore))
                out.put("jvmConfiguredFile", fileMetadata(configuredJvmStore));
        } catch (RuntimeException invalid) { out.put("jvmFileMetadata", invalid.getClass().getSimpleName()); }
        if (config != null) {
            out.put("trustCerts", config.isTrustCerts());
            out.put("disableHostnameVerification", config.isDisableHostnameVerification());
            // Optional getter reflection tolerates corporate Fabric8 variants without exposing Config.toString().
            String caData = optionalString(config, "getCaCertData");
            String caFile = optionalString(config, "getCaCertFile");
            String storeFile = optionalString(config, "getTrustStoreFile");
            out.put("fabric8CaDataPresent", caData != null && !caData.isBlank());
            out.put("fabric8CaFile", fileMetadata(caFile));
            out.put("fabric8TrustStoreFile", fileMetadata(storeFile));
            out.put("fabric8TrustStoreEntryCount", "not inspected; no passphrase/private-keystore access");
            try {
                byte[] ca = null;
                if (caData != null && !caData.isBlank()) {
                    if (caData.length() > 2800000) throw new IllegalStateException("CA_SIZE_LIMIT");
                    ca = Base64.getMimeDecoder().decode(caData);
                    out.put("caInspectionSource", "Fabric8 caCertData");
                } else if (caFile != null && !caFile.isBlank()) {
                    Path path = Path.of(caFile);
                    if (Files.size(path) > 2097152) throw new IllegalStateException("CA_SIZE_LIMIT");
                    ca = Files.readAllBytes(path);
                    out.put("caInspectionSource", "Fabric8 caCertFile");
                }
                if (ca != null) {
                    int count = CertificateFactory.getInstance("X.509")
                            .generateCertificates(new ByteArrayInputStream(ca)).size();
                    out.put("configuredCaCertificateCount", count);
                } else {
                    out.put("configuredCaCertificateCount", "no readable configured CA source");
                }
            } catch (Exception invalid) {
                out.put("caInspectionErrorType", invalid.getClass().getSimpleName());
            }
        }
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            int managers = 0, issuers = 0;
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager x509) {
                    managers++;
                    issuers += x509.getAcceptedIssuers().length;
                }
            }
            out.put("defaultX509TrustManagerCount", managers);
            out.put("defaultAcceptedIssuerCount", managers == 0 ? "unavailable" : issuers);
            out.put("defaultTrustManagerProvider", factory.getProvider().getName());
        } catch (Exception failure) {
            out.put("defaultTrustManagerErrorType", failure.getClass().getSimpleName());
        }
        out.put("interpretation", "Default JVM issuer count is not necessarily the effective Fabric8 trust set. "
                + "Candidate paths are metadata, not proof that a provider selected that file.");
        out.put("secrets", "No password, token, raw kubeconfig, CA bytes, private key or TLS bypass attached");
        return out;
    }

    private static String optionalString(Config config, String getter) {
        try {
            Object value = config.getClass().getMethod(getter).invoke(config);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException unavailable) { return null; }
    }

    private static Map<String, Object> fileMetadata(String name) {
        if (name == null || name.isBlank()) return Map.of("configured", false);
        try {
            Path path = Path.of(name).toAbsolutePath().normalize();
            return Map.of("configured", true, "path", path.toString(),
                    "regularFile", Files.isRegularFile(path), "readable", Files.isReadable(path));
        } catch (RuntimeException invalid) {
            return Map.of("configured", true, "pathErrorType", invalid.getClass().getSimpleName());
        }
    }
}
