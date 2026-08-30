package config.services.core;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.sber.qa.services.configuration.converters.SecretPropertyConverter;

import static config.services.core.SecureLocalConfigScope.SECURE_LOCAL_CONFIG;

/**
 * Bridge for project code paths that read raw Properties outside Owner.
 */
public final class SecurePropertyResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final SecretPropertyConverter SECRET_PROPERTY_CONVERTER = new SecretPropertyConverter();

    private SecurePropertyResolver() {
    }

    public static String resolve(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (isSetMePlaceholder(value.trim())) {
            throw new IllegalStateException("Секретное значение не заполнено: " + value.trim()
                    + ". Заполните secure.local.override.properties, secure.local.properties, env или JVM -D property.");
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
                throw new IllegalStateException("Не найдено значение для плейсхолдера ${" + name
                        + "}. Заполните secure.local.override.properties, secure.local.properties, env или JVM -D property.");
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
        return firstUsable(
                System.getProperty(name),
                System.getenv(name),
                System.getenv(toEnvName(name)),
                SECURE_LOCAL_CONFIG.getProperty(name)
        );
    }

    private static String toEnvName(String name) {
        return name.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    private static String convertIfNeeded(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("ENC(") || trimmed.startsWith("vault.")) {
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

}
