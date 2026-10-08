package config.services.container;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Public CA material only. No login, network request, global JSSE change or trust-all fallback. */
public final class KubernetesCaTrust {
    private static final int MAX_BYTES = 1048576;
    private KubernetesCaTrust() { }

    public static Config withApprovedResource(Config config, String resource, String fingerprints) {
        if (!resource.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                || resource.contains("..") || !resource.endsWith(".pem"))
            throw new IllegalStateException("KUBECONFIG_CA_RESOURCE: expected a relative public PEM resource");
        Set<String> approved = new HashSet<>();
        for (String value : fingerprints.split(",")) {
            String fingerprint = value.trim().toUpperCase(java.util.Locale.ROOT);
            if (!fingerprint.matches("[0-9A-F]{64}") || !approved.add(fingerprint))
                throw new IllegalStateException("KUBECONFIG_CA_PINS: distinct approved certificate SHA-256 values required");
        }
        try {
            // Match project properties behaviour for both IDEA and Gradle launches.
            Path source = Path.of("src", "test", "resources").resolve(resource);
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = KubernetesCaTrust.class.getClassLoader();
            byte[] pem;
            try (InputStream input = Files.isRegularFile(source)
                    ? Files.newInputStream(source) : loader.getResourceAsStream(resource)) {
                if (input == null)
                    throw new IllegalStateException("KUBECONFIG_CA_MISSING: approved CA resource is unavailable");
                pem = input.readNBytes(MAX_BYTES + 1);
            }
            if (pem.length == 0 || pem.length > MAX_BYTES)
                throw new IllegalStateException("KUBECONFIG_CA_SIZE: expected 1..1048576 bytes");
            Set<String> actual = fingerprints(pem);
            if (!actual.equals(approved))
                throw new IllegalStateException("KUBECONFIG_CA_PIN_MISMATCH: public bundle differs from approved certificates");
            return new ConfigBuilder(config)
                    .withCaCertData(Base64.getEncoder().encodeToString(pem))
                    .withCaCertFile(null)
                    .withTrustCerts(false)
                    .withDisableHostnameVerification(false)
                    .build();
        } catch (Exception failure) {
            throw safe(failure);
        }
    }

    /** Local fail-fast only. A positive result does NOT prove live TLS, credentials or RBAC. */
    public static Config requireNativeTrust(Config config) {
        try {
            if (config.isTrustCerts() || config.isDisableHostnameVerification())
                throw new IllegalStateException("KUBECONFIG_TLS: certificate and hostname verification must remain enabled");
            String data = config.getCaCertData();
            String file = config.getCaCertFile();
            if (data != null && !data.isBlank()) {
                if (data.length() > MAX_BYTES * 2)
                    throw new IllegalStateException("KUBECONFIG_CA_SIZE: encoded CA exceeds the diagnostic bound");
                fingerprints(Base64.getMimeDecoder().decode(data));
                return config;
            }
            if (file != null && !file.isBlank()) {
                try (InputStream input = Files.newInputStream(Path.of(file))) {
                    byte[] pem = input.readNBytes(MAX_BYTES + 1);
                    if (pem.length > MAX_BYTES)
                        throw new IllegalStateException("KUBECONFIG_CA_SIZE: configured CA exceeds the diagnostic bound");
                    fingerprints(pem);
                }
                return config;
            }
            // An explicit Fabric8 truststore is not the default JSSE trust set.
            // Its actual validation is left to Fabric8; never read its password/private entries here.
            try {
                Object custom = config.getClass().getMethod("getTrustStoreFile").invoke(config);
                if (custom instanceof String path && !path.isBlank()) {
                    if (!Files.isRegularFile(Path.of(path)) || !Files.isReadable(Path.of(path)))
                        throw new IllegalStateException("KUBECONFIG_CA_STORE_MISSING: configured native truststore is unreadable");
                    return config;
                }
            } catch (ReflectiveOperationException unsupportedGetter) {
                // Compatible with framework distributions without the optional getter.
            }
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (var manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager x509 && x509.getAcceptedIssuers().length > 0)
                    return config;
            }
            throw new IllegalStateException("JAVA_TRUST_ANCHORS_EMPTY: no explicit native CA and zero default trusted issuers; "
                    + "configure the selected stand CA, not an application token");
        } catch (Exception failure) {
            throw safe(failure);
        }
    }

    private static Set<String> fingerprints(byte[] pem) throws Exception {
        var certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(pem));
        if (certificates.isEmpty() || certificates.size() > 16)
            throw new IllegalStateException("KUBECONFIG_CA_CERTIFICATES: expected 1..16 CA certificates");
        Set<String> actual = new HashSet<>();
        for (var certificate : certificates) {
            if (!(certificate instanceof X509Certificate x509) || x509.getBasicConstraints() < 0)
                throw new IllegalStateException("KUBECONFIG_CA_NOT_CA: leaf certificates must not become trust anchors");
            x509.checkValidity();
            String fingerprint = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(x509.getEncoded()));
            if (!actual.add(fingerprint))
                throw new IllegalStateException("KUBECONFIG_CA_DUPLICATE: duplicated public CA certificate");
        }
        return actual;
    }

    private static IllegalStateException safe(Exception failure) {
        String message = failure.getMessage();
        if (failure instanceof IllegalStateException && message != null
                && (message.startsWith("KUBECONFIG_") || message.startsWith("JAVA_TRUST_ANCHORS_EMPTY")))
            return (IllegalStateException) failure;
        return new IllegalStateException("KUBECONFIG_CA_INVALID: " + failure.getClass().getSimpleName()
                + "; inspect the approved public CA source; native details suppressed");
    }
}
