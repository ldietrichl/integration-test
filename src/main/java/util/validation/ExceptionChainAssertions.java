package util.validation;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class ExceptionChainAssertions {
    public record Cause(Class<?> type, String message, StackTraceElement origin) { }
    public static List<Throwable> chain(Throwable failure) {
        List<Throwable> result = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) result.add(t);
        return result;
    }
    // Snapshot before invoking the SDK: comparing a mutated exception to itself proves nothing.
    public static List<Cause> snapshot(Throwable original) {
        return chain(original).stream().map(t -> {
            assertTrue(t.getStackTrace().length > 0, "Тестовое исключение должно иметь место возникновения");
            return new Cause(t.getClass(), t.getMessage(), t.getStackTrace()[0]);
        }).toList();
    }
    public static void preserves(Throwable actual, List<Cause> expected) {
        Iterator<Throwable> remaining = chain(actual).iterator();
        for (Cause cause : expected) {
            Throwable found = null;
            while (remaining.hasNext()) {
                Throwable candidate = remaining.next();
                if (candidate.getClass().equals(cause.type()) && Objects.equals(candidate.getMessage(), cause.message())
                        && Arrays.asList(candidate.getStackTrace()).contains(cause.origin())) {
                    found = candidate; break;
                }
            }
            assertNotNull(found, "Потеряна причина или место возникновения: " + cause.type().getName()
                    + ": " + cause.message() + " at " + cause.origin());
        }
    }
    public static void startupFailure(Throwable failure, String... acceptedTypes) {
        List<Throwable> causes = chain(failure);
        for (Throwable cause : causes) {
            assertFalse(cause instanceof ReflectiveOperationException || cause instanceof LinkageError
                    || cause instanceof AssertionError, "Ошибка тестовой инфраструктуры вместо валидации: " + cause);
        }
        assertTrue(causes.stream().anyMatch(c -> Arrays.asList(acceptedTypes).contains(c.getClass().getName())),
                "Не подтверждён отказ загрузчика/валидатора: " + causes);
    }
}
