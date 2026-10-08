package infrastructure.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Carries safe transport facts across wrappers without retaining native messages or credentials. */
public final class KubernetesDiagnosticException extends IllegalStateException {
    private final String failureCode;
    private final String operation;
    private final int httpStatus;
    private final List<String> exceptionTypes;
    private final String rootCauseType;

    public KubernetesDiagnosticException(String code, String operation, Throwable original) {
        this(safeCode(code), safeOperation(operation), snapshot(original));
    }

    private KubernetesDiagnosticException(String code, String operation, Facts facts) {
        super(operation + "_" + code + "; httpStatus="
                + (facts.httpStatus > 0 ? facts.httpStatus : "not-received")
                + "; exceptionTypes=" + facts.types + "; rootCauseType=" + facts.root
                + "; exception messages and credentials suppressed", null);
        this.failureCode = code;
        this.operation = operation;
        this.httpStatus = facts.httpStatus;
        this.exceptionTypes = facts.types;
        this.rootCauseType = facts.root;
    }

    public String failureCode() { return failureCode; }
    public int httpStatus() { return httpStatus; }

    public Map<String, Object> safeDetails() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("failureCode", failureCode);
        result.put("operation", operation);
        result.put("httpStatus", httpStatus > 0 ? httpStatus : "not-received");
        result.put("exceptionTypes", exceptionTypes);
        result.put("rootCauseType", rootCauseType);
        result.put("exceptionMessages", "suppressed");
        String action = recommendedAction();
        if (action != null) result.put("recommendedActionRu", action);
        if (httpStatus == 401) result.put("authenticationRejectionReason", "NOT_PROVEN");
        return result;
    }

    private String recommendedAction() {
        return switch (failureCode) {
            case "KUBERNETES_MANAGEMENT_NOT_CONFIRMED" ->
                    "Доступ Kubernetes не подтверждён настройкой container-service.ready. Сначала проверьте kubeconfig, контекст, API выбранного стенда и RBAC; не включайте флаг вслепую.";
            case "KUBECONFIG_FILE_MISSING" ->
                    "Укажите существующий kubeconfig выбранного стенда в kubernetes.<env>.kubeconfig. Клиентский P12 для ingress не заменяет kubeconfig.";
            case "KUBECONFIG_MULTIPLE_FILES" ->
                    "Укажите один kubeconfig выбранного стенда вместо списка файлов в KUBECONFIG.";
            case "KUBECONFIG_CONTEXT_MISSING" ->
                    "Задайте kubernetes.<env>.context из утверждённого kubeconfig выбранного стенда. Контекст DEV нельзя автоматически использовать для IFT.";
            case "KUBERNETES_REQUIRED_SETTING_MISSING" ->
                    "Заполните обязательные настройки Kubernetes выбранного стенда: namespace, context и api-server. Значения настроек не включены в отчёт.";
            case "KUBERNETES_FABRIC8_REQUIRED" ->
                    "Для прямой диагностики требуется транспорт fabric8. Изменяйте профиль только после проверки доступа к выбранному стенду.";
            case "KUBERNETES_PORTFORWARD_PERMISSION_NOT_DECLARED" ->
                    "В permissions.required не заявлено create pods/portforward. Сначала подтвердите это право через RBAC: изменение списка само по себе не выдаёт разрешение.";
            default -> null;
        };
    }

    public static KubernetesDiagnosticException find(Throwable failure) {
        for (Throwable current : chain(failure))
            if (current instanceof KubernetesDiagnosticException safe) return safe;
        return null;
    }

    private record Facts(int httpStatus, List<String> types, String root) { }

    private static Facts snapshot(Throwable original) {
        int status = -1;
        List<String> types = new ArrayList<>();
        String root = "unknown";
        for (Throwable current : chain(original)) {
            root = current.getClass().getSimpleName();
            if (!types.contains(root)) types.add(root);
            if (current instanceof KubernetesDiagnosticException safe) {
                if (status < 0) status = safe.httpStatus;
                for (String type : safe.exceptionTypes)
                    if (types.size() < 24 && !types.contains(type)) types.add(type);
                root = safe.rootCauseType;
            } else if (current instanceof KubernetesClientException nativeFailure) {
                int code = nativeFailure.getCode();
                if (status < 0 && code >= 100 && code <= 599) status = code;
            }
        }
        return new Facts(status, List.copyOf(types), root);
    }

    private static List<Throwable> chain(Throwable failure) {
        List<Throwable> pending = new ArrayList<>();
        List<Throwable> result = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(failure);
        for (int i = 0; i < pending.size() && i < 24; i++) {
            Throwable current = pending.get(i);
            if (current == null || !seen.add(current)) continue;
            result.add(current);
            if (pending.size() < 24) pending.add(current.getCause());
            for (Throwable suppressed : current.getSuppressed())
                if (pending.size() < 24) pending.add(suppressed);
        }
        return result;
    }

    private static String safeCode(String value) {
        return value != null && value.matches("[A-Z][A-Z0-9_]{0,95}") ? value : "UNCLASSIFIED";
    }

    private static String safeOperation(String value) {
        // Call sites supply operation labels, never URLs, resource bodies or credentials.
        if (value == null) return "UNKNOWN_OPERATION";
        String result = value.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        return result.isBlank() ? "UNKNOWN_OPERATION" : result.substring(0, Math.min(96, result.length()));
    }
}
