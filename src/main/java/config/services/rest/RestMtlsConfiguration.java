package config.services.rest;

import config.services.core.SecurePropertyResolver;
import config.services.core.TestConfigurationFiles;
import config.services.core.TestEnvironment;
import static config.services.core.CustomTestConfigScope.TEST_CONFIG;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;

import java.io.InputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import io.restassured.config.RedirectConfig;
import io.restassured.filter.OrderedFilter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;

/**
 * Applies stand-level mTLS settings to RestAssured clients without changing JVM-global SSL state.
 *
 * <p>The client certificate is intentionally configured through stand properties and secure placeholders,
 * so IDE runs and Gradle runs use the same values.</p>
 */
public final class RestMtlsConfiguration {
    private static final String KEY_PREFIX = "stand.";
    private static final String MTLS_SEGMENT = ".mtls.";

    private RestMtlsConfiguration() {
    }

    public static RestAssuredConfig apply(RestAssuredConfig config) {
        String environment = TestEnvironment.current();
        if ("local".equals(environment) || !enabled(environment)) {
            return config;
        }

        Properties properties = properties();
        if (flag(properties, environment, "server-tls.relaxed", false)) {
            throw new IllegalStateException("mTLS requires server-tls.relaxed=false; server and hostname validation cannot be bypassed");
        }
        allowedHosts(properties, environment); // Fail before loading private material.
        Path pkcs12 = storePath(required(properties, environment, "client.pkcs12"), "mTLS client PKCS12");
        String type = optional(properties, environment, "client.pkcs12.type", "PKCS12");
        String password = resolveSecret(required(properties, environment, "client.pkcs12.password"),
                key(environment, "client.pkcs12.password"));

        KeyStore clientStore = loadStore(pkcs12, type, password, "mTLS client PKCS12");
        requireClientKey(clientStore, password);

        SSLConfig sslConfig = new SSLConfig()
                .keyStore(pkcs12.toFile(), password)
                .keystoreType(type);

        String truststorePath = required(properties, environment, "truststore");
        String truststoreType = optional(properties, environment, "truststore.type", "PKCS12");
        String truststorePassword = resolveSecret(required(properties, environment, "truststore.password"),
                key(environment, "truststore.password"));
        KeyStore trustStore = loadStore(storePath(truststorePath, "mTLS truststore"),
                truststoreType, truststorePassword, "mTLS truststore");
        requireTrustedCertificates(trustStore);
        sslConfig = sslConfig.trustStore(trustStore);
        // SSLConfig is strict by default. Do not inherit a relaxed hostname verifier.
        return config.sslConfig(sslConfig)
                .redirect(RedirectConfig.redirectConfig().followRedirects(false));
    }

    /** Gateway clients keep their legacy non-DEV profile until that stand is migrated explicitly. */
    public static RestAssuredConfig apply(RestAssuredConfig config, String baseUri) {
        String environment = TestEnvironment.current();
        if (enabled(environment)) {
            requireApprovedTarget(baseUri);
            return apply(config);
        }
        if (!"local".equals(environment)) {
            return config.sslConfig(new SSLConfig()
                    .keyStore("src/test/resources/keystore.p12",
                            TEST_CONFIG.keystorePass())
                    .keystoreType("PKCS12").relaxedHTTPSValidation());
        }
        return config;
    }

    /** Validate the actual request too: an absolute URL can override the configured base URI. */
    public static OrderedFilter requestGuard() {
        return new OrderedFilter() {
            @Override public int getOrder() { return Integer.MIN_VALUE; }
            @Override public Response filter(FilterableRequestSpecification request,
                    FilterableResponseSpecification response, FilterContext context) {
                if (enabled(TestEnvironment.current())) {
                    requireApprovedTarget(request.getURI());
                    request.config(request.getConfig()
                            .redirect(RedirectConfig.redirectConfig().followRedirects(false)));
                }
                return context.next(request, response);
            }
        };
    }

    public static void requireApprovedTarget(String target) {
        String environment = TestEnvironment.current();
        if (!enabled(environment)) return;
        URI uri;
        try { uri = URI.create(target); }
        catch (RuntimeException error) { throw new IllegalStateException("Invalid mTLS target URI"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || !allowedHosts(properties(), environment).contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("mTLS request rejected: HTTPS/443 and an explicitly approved host from "
                    + key(environment, "gateway.hosts") + " are required; no HTTP fallback");
        }
    }

    private static Set<String> allowedHosts(Properties properties, String environment) {
        Set<String> hosts = Arrays.stream(required(properties, environment, "gateway.hosts").split(",", -1))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (hosts.isEmpty() || hosts.stream().anyMatch(value ->
                !value.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?") || value.contains(".."))) {
            throw new IllegalStateException("Configure exact DNS names, without wildcards, URLs or ports: "
                    + key(environment, "gateway.hosts"));
        }
        return hosts;
    }

    public static boolean enabled(String environment) {
        return flag(properties(), environment, "enabled", false);
    }

    private static Properties properties() {
        Properties result = new Properties();
        result.putAll(TestConfigurationFiles.load("stand.properties"));
        result.putAll(TestConfigurationFiles.load("test.properties"));
        return result;
    }

    private static boolean flag(Properties properties, String environment, String name, boolean fallback) {
        String value = optional(properties, environment, name, Boolean.toString(fallback));
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalStateException("Property " + key(environment, name) + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static String required(Properties properties, String environment, String name) {
        String value = optional(properties, environment, name, "");
        if (value.isBlank() || value.startsWith("<SET_ME_")) {
            throw new IllegalStateException("Configure " + key(environment, name));
        }
        return value;
    }

    private static String optional(Properties properties, String environment, String name, String fallback) {
        String value = properties.getProperty(key(environment, name),
                properties.getProperty(KEY_PREFIX + "mtls." + name, fallback));
        return value == null ? "" : value.trim();
    }

    private static String key(String environment, String name) {
        return KEY_PREFIX + environment + MTLS_SEGMENT + name;
    }

    private static String resolveSecret(String raw, String key) {
        String value = SecurePropertyResolver.resolve(raw);
        if (value == null || value.isBlank() || value.contains("${") || value.startsWith("<SET_ME_")) {
            throw new IllegalStateException("Unresolved mTLS secret: " + key);
        }
        return value;
    }

    private static Path storePath(String configured, String kind) {
        String value = SecurePropertyResolver.resolve(configured);
        if (value == null || value.isBlank() || value.contains("${") || value.startsWith("<SET_ME_")) {
            throw new IllegalStateException("Configure a resolved " + kind + " file path");
        }
        Path file = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IllegalStateException(kind + " file is missing or unreadable: " + file
                    + "; relative paths use the integration-test project working directory");
        }
        return file;
    }

    private static KeyStore loadStore(Path file, String type, String password, String kind) {
        if (password == null || password.isBlank() || password.contains("${") || password.startsWith("<SET_ME_")) {
            throw new IllegalStateException("Unresolved " + kind + " password");
        }
        char[] secret = password.toCharArray();
        try (InputStream input = Files.newInputStream(file)) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(input, secret);
            return store;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load " + kind + " (" + error.getClass().getSimpleName()
                    + "); check file format/type and secure password");
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static void requireClientKey(KeyStore store, String password) {
        char[] secret = password.toCharArray();
        try {
            int found = 0;
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isKeyEntry(alias) && store.getKey(alias, secret) instanceof PrivateKey) {
                    java.security.cert.Certificate[] chain = store.getCertificateChain(alias);
                    if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate leaf))
                        throw new IllegalStateException("mTLS private key has no X.509 certificate chain");
                    for (java.security.cert.Certificate certificate : chain) {
                        if (!(certificate instanceof X509Certificate x509))
                            throw new IllegalStateException("mTLS client chain must contain X.509 certificates");
                        x509.checkValidity();
                    }
                    List<String> usage = leaf.getExtendedKeyUsage();
                    if (usage != null && !usage.contains("1.3.6.1.5.5.7.3.2") && !usage.contains("2.5.29.37.0"))
                        throw new IllegalStateException("mTLS certificate does not permit TLS client authentication");
                    boolean[] keyUsage = leaf.getKeyUsage();
                    if (keyUsage != null && (keyUsage.length == 0 || !keyUsage[0]))
                        throw new IllegalStateException("mTLS certificate does not permit digital signatures");
                    found++;
                }
            }
            if (found != 1) {
                throw new IllegalStateException("mTLS PKCS12 must contain exactly one private-key entry with a certificate chain");
            }
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(store, secret);
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize mTLS client key (" + error.getClass().getSimpleName()
                    + "); this profile expects the private-key password to equal the PKCS12 password");
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static void requireTrustedCertificates(KeyStore store) {
        try {
            Enumeration<String> aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (store.isCertificateEntry(alias) && store.getCertificate(alias) instanceof X509Certificate certificate
                        && certificate.getBasicConstraints() >= 0) {
                    try { certificate.checkValidity(); }
                    catch (java.security.cert.CertificateException expiredOrNotYetValid) { continue; }
                    return;
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot inspect mTLS truststore certificate entries");
        }
        throw new IllegalStateException("mTLS truststore has no currently valid trusted X.509 CA certificate entries");
    }
}
