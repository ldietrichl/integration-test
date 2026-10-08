package config.services.core;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jasypt.util.text.BasicTextEncryptor;
import ru.sber.qa.services.configuration.converters.SecretPropertyConverter;

import static config.services.core.SecureLocalConfigScope.SECURE_LOCAL_CONFIG;

/**
 * Bridge for project code paths that read raw Properties outside Owner.
 */
public final class SecurePropertyResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final SecretPropertyConverter SECRET_PROPERTY_CONVERTER = new SecretPropertyConverter();
    private static final String FAIL_ON_UNRESOLVED_PROPERTY = "secure.placeholders.fail-on-unresolved";
    private static final String FAIL_ON_UNRESOLVED_ENV = "SECURE_PLACEHOLDERS_FAIL_ON_UNRESOLVED";
    private static final String ENCRYPTION_PASSWORD_PROPERTY = "encryption.password";

    private SecurePropertyResolver() {
    }

    public static String resolve(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (isSetMePlaceholder(value.trim())) {
            if (!failOnUnresolvedPlaceholders()) {
                return value;
            }
            throw new IllegalStateException("Секретное значение не заполнено: " + value.trim()
                    + ". Заполните secure.local.override.properties.");
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return convertIfNeeded(value);
        }
        matcher.reset();
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String resolved = lookup(name);
            if (resolved == null) {
                if (isSecurePlaceholder(name) && !failOnUnresolvedPlaceholders()) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                    continue;
                }
                throw new IllegalStateException("Не найдено значение для плейсхолдера ${" + name
                        + "}. Заполните secure.local.override.properties.");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(convertIfNeeded(resolved)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static Properties resolve(Properties source) {
        Properties target = new Properties();
        source.stringPropertyNames()
                .forEach(name -> target.put(name, resolve(source.getProperty(name))));
        return target;
    }

    private static String lookup(String name) {
        return firstUsable(SECURE_LOCAL_CONFIG.getProperty(name));
    }

    private static String convertIfNeeded(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("ENC(")) {
            String password = lookup(ENCRYPTION_PASSWORD_PROPERTY);
            if (password == null) {
                throw new IllegalStateException("Для ENC требуется encryption.password в secure.local.override.properties.");
            }
            if (!trimmed.endsWith(")") || trimmed.length() <= 5) {
                throw new IllegalStateException("Некорректное значение ENC в конфигурации.");
            }
            // Same Jasypt algorithm as the SDK, without its JVM/env lookup or regex replacement
            // of decrypted text (which treats '$' and backslashes as replacement syntax).
            BasicTextEncryptor crypt = new BasicTextEncryptor();
            crypt.setPassword(password);
            String decrypted;
            try {
                decrypted = crypt.decrypt(trimmed.substring(4, trimmed.length() - 1));
            } catch (RuntimeException failure) {
                // Do not expose ciphertext, a password, or SDK exception text in test reports.
                throw new IllegalStateException("Не удалось расшифровать ENC; проверьте encryption.password в secure.local.override.properties.");
            }
            return decrypted.startsWith("vault.") ? SECRET_PROPERTY_CONVERTER.convert(decrypted) : decrypted;
        }
        if (trimmed.startsWith("vault.")) {
            return SECRET_PROPERTY_CONVERTER.convert(trimmed);
        }
        return value;
    }

    private static String firstUsable(String... candidates) {
        for (String candidate : candidates) {
            if (isUsable(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static boolean isUsable(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !isSetMePlaceholder(value.trim());
    }

    private static boolean isSetMePlaceholder(String value) {
        return value.startsWith("<SET_ME_") && value.endsWith(">");
    }

    private static boolean isSecurePlaceholder(String name) {
        return name != null && name.startsWith("SECURE_");
    }

    private static boolean failOnUnresolvedPlaceholders() {
        String value = firstRaw(
                System.getProperty(FAIL_ON_UNRESOLVED_PROPERTY),
                System.getenv(FAIL_ON_UNRESOLVED_ENV)
        );
        if (value == null) {
            return true;
        }

        String normalized = value.trim();
        return !(normalized.equalsIgnoreCase("false")
                || normalized.equalsIgnoreCase("no")
                || normalized.equals("0"));
    }

    private static String firstRaw(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

}
