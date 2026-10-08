import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.ignite.client.SslMode;
import org.apache.ignite.configuration.ClientConfiguration;

/** Connection and TLS support shared by isolated Ignite helpers for all services. */
public final class IgniteClientSupport {
    private IgniteClientSupport() {
    }

    public static ClientConfiguration configuration(String addresses) {
        if (addresses == null || addresses.isBlank()) {
            throw new IllegalArgumentException("Ignite addresses are required");
        }
        String[] endpoints = Arrays.stream(addresses.split(",", -1)).map(String::trim).toArray(String[]::new);
        if (Arrays.stream(endpoints).anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("Ignite address list contains an empty endpoint");
        }
        var configuration = new ClientConfiguration().setAddresses(endpoints)
                .setPartitionAwarenessEnabled(false).setClusterDiscoveryEnabled(false).setTimeout(10000);
        String username = setting("USERNAME");
        String password = setting("PASSWORD");
        if (username != null) configuration.setUserName(username);
        if (password != null) configuration.setUserPassword(password);
        String sslEnabled = setting("SSL_ENABLED");
        if (!Set.of("true", "false").contains(sslEnabled == null ? "" : sslEnabled)) {
            throw new IllegalArgumentException("Explicit Ignite SSL mode true or false is required");
        }
        boolean ssl = Boolean.parseBoolean(sslEnabled);
        configuration.setSslMode(ssl ? SslMode.REQUIRED : SslMode.DISABLED).setSslTrustAll(false);
        if (ssl) configuration.setSslContextFactory(IgniteClientSupport::sslContext);
        return configuration;
    }

    /** The parent selects the environment; no unscoped legacy settings are read here. */
    private static String setting(String suffix) {
        String value = System.getenv("IGNITE_CLIENT_" + suffix);
        return value == null || value.isBlank() ? null : value;
    }

    private static KeyStore loadStore(String path, String type, char[] password) throws Exception {
        KeyStore store = KeyStore.getInstance(type == null ? "JKS" : type);
        try (var input = Files.newInputStream(Path.of(path))) {
            store.load(input, password);
        }
        return store;
    }

    public static SSLContext sslContext() {
        String keyPath = setting("SSL_KEY_STORE_PATH");
        String trustPath = setting("SSL_TRUST_STORE_PATH");
        String keyPassword = setting("SSL_KEY_STORE_PASSWORD");
        String trustPassword = setting("SSL_TRUST_STORE_PASSWORD");
        char[] keyChars = keyPassword == null ? new char[0] : keyPassword.toCharArray();
        char[] trustChars = trustPassword == null ? new char[0] : trustPassword.toCharArray();
        try {
            KeyManager[] keys = new KeyManager[0];
            if (keyPath != null) {
                var keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyFactory.init(loadStore(keyPath, setting("SSL_KEY_STORE_TYPE"), keyChars), keyChars);
                keys = keyFactory.getKeyManagers();
            }
            var trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustFactory.init(trustPath == null ? (KeyStore) null
                    : loadStore(trustPath, setting("SSL_TRUST_STORE_TYPE"), trustChars));
            var context = SSLContext.getInstance("TLS");
            context.init(keys, trustFactory.getTrustManagers(), null);
            System.out.println("IGNITE_TLS_CONTEXT=initialized; clientKeyStoreConfigured=" + (keyPath != null)
                    + "; trustStore=" + (trustPath == null ? "jdk-default" : "configured")
                    + "; certificateValidation=enabled");
            return context;
        } catch (Exception error) {
            throw new IllegalStateException("Ignite TLS context initialization failed; check the selected environment's stores", error);
        } finally {
            Arrays.fill(keyChars, '\0');
            Arrays.fill(trustChars, '\0');
        }
    }
}
