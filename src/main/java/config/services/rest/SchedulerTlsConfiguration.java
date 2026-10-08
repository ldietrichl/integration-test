package config.services.rest;

import config.services.core.SchedulerSettings;
import config.services.core.SecurePropertyResolver;
import config.services.core.TestConfigurationFiles;
import io.restassured.config.SSLConfig;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import static config.services.core.CustomTestConfigScope.TEST_CONFIG;

/** Strict TLS for the framework-owned RestClient; never changes JVM-global SSL settings. */
public final class SchedulerTlsConfiguration {
    private SchedulerTlsConfiguration() { }

    public static SSLConfig configure(SchedulerSettings settings, String prefix) {
        Properties project = TestConfigurationFiles.load("test.properties");
        String trustPath = settings.optional(prefix + "truststore", project.getProperty("truststore.path", ""));
        KeyStore trust;
        if (trustPath.isBlank()) {
            trust = jvmTrust(settings, prefix);
        } else {
            Path file = storePath(trustPath, "truststore");
            String type = settings.optional(prefix + "truststore.type",
                    project.getProperty("truststore.type", KeyStore.getDefaultType()));
            String password = settings.optional(prefix + "truststore.password", null);
            password = password == null ? TEST_CONFIG.truststorePass()
                    : SecurePropertyResolver.resolve(password);
            trust = loadStore(file, type, password, "truststore");
            requireTrustedCertificates(trust, settings, prefix);
        }

        // SSLConfig starts with strict hostname verification. No trust-all fallback.
        SSLConfig ssl = new SSLConfig().trustStore(trust);
        if ("true".equals(settings.optional(prefix + "mtls.enabled", "false"))) {
            Path file = storePath(settings.optional(prefix + "keystore", "src/test/resources/keystore.p12"), "keystore");
            String type = settings.optional(prefix + "keystore.type", "PKCS12");
            String password = settings.optional(prefix + "keystore.password", null);
            password = password == null ? TEST_CONFIG.keystorePass()
                    : SecurePropertyResolver.resolve(password);
            KeyStore keys = loadStore(file, type, password, "keystore");
            requireClientKey(keys, password);
            ssl = ssl.keyStore(file.toFile(), password).keystoreType(type);
        }
        return ssl;
    }

    private static Path storePath(String configured, String kind) {
        String value = SecurePropertyResolver.resolve(configured);
        if (value == null || value.isBlank() || value.contains("${") || value.startsWith("<SET_ME_"))
            throw new IllegalStateException("Scheduler TLS: configure a resolved " + kind + " file path");
        Path file = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !Files.isReadable(file))
            throw new IllegalStateException("Scheduler TLS: " + kind + " file is missing or unreadable: " + file
                    + "; relative paths use the integration-test project working directory");
        return file;
    }

    private static KeyStore loadStore(Path file, String type, String password, String kind) {
        if (password == null || password.isBlank() || password.contains("${") || password.startsWith("<SET_ME_"))
            throw new IllegalStateException("Scheduler TLS: unresolved " + kind
                    + " password; configure scheduler.<env>." + kind + ".password or shared " + kind + ".pass using project secure properties");
        char[] secret = password.toCharArray();
        try (InputStream in = Files.newInputStream(file)) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(in, secret);
            return store;
        } catch (Exception error) {
            // Do not expose provider error text, passwords, aliases or certificate contents.
            throw new IllegalStateException("Scheduler TLS: cannot load " + kind + " (" + error.getClass().getSimpleName()
                    + "); check file format/type and its secure password");
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static KeyStore jvmTrust(SchedulerSettings settings, String prefix) {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager) {
                    for (X509Certificate certificate : ((X509TrustManager) manager).getAcceptedIssuers())
                        store.setCertificateEntry("jvm-ca-" + index++, certificate);
                }
            }
            requireTrustedCertificates(store, settings, prefix);
            return store;
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Scheduler TLS: cannot initialize JVM trust (" + error.getClass().getSimpleName()
                    + "); configure scheduler." + settings.environment + "." + prefix
                    + "truststore and its type/password, or correct the test JVM truststore");
        }
    }

    private static void requireTrustedCertificates(KeyStore store, SchedulerSettings settings, String prefix) {
        try {
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isCertificateEntry(alias) && store.getCertificate(alias) instanceof X509Certificate) return;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Scheduler TLS: cannot inspect trusted certificate entries");
        }
        throw new IllegalStateException("Scheduler TLS: no trusted X.509 certificate entries; configure scheduler."
                + settings.environment + "." + prefix + "truststore with the approved corporate CA store. "
                + "A client keystore containing only private keys is not a truststore; TLS validation remains enabled");
    }

    private static void requireClientKey(KeyStore store, String password) {
        char[] secret = password.toCharArray();
        try {
            boolean found = false;
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isKeyEntry(alias) && store.getCertificateChain(alias) != null) found = true;
            }
            if (!found) throw new IllegalStateException("Scheduler TLS: mTLS keystore has no private-key entry with a certificate chain");
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, secret);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Scheduler TLS: cannot initialize client key (" + error.getClass().getSimpleName()
                    + "); this profile expects the private-key password to equal the keystore password");
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
