package util;

import java.util.Objects;

public final class TestAssertions {

    private TestAssertions() {
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            fail(message);
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    public static void assertNotNull(Object value, String message) {
        if (value == null) {
            fail(message);
        }
    }

    public static void assertNull(Object value, String message) {
        if (value != null) {
            fail(message + " expected=<null> actual=<" + value + ">");
        }
    }

    public static void assertDoesNotThrow(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            AssertionError assertionError = new AssertionError(message + ": " + throwable.getMessage());
            assertionError.initCause(throwable);
            throw assertionError;
        }
    }

    public static <T> T fail(String message) {
        throw new AssertionError(message);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
