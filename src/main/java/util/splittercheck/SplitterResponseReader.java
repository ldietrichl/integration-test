package util.splittercheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class SplitterResponseReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> DIRECT_STRING_METHODS = Arrays.asList(
            "asString",
            "prettyPrint",
            "prettyPeek"
    );
    private static final List<String> NAVIGATION_METHODS = Arrays.asList(
            "extract",
            "response",
            "getResponse",
            "getValidatableResponse",
            "getResponseSpecification",
            "body",
            "getBody"
    );

    private SplitterResponseReader() {
    }

    public static SplitterResponseSnapshot snapshot(Object responseWrapper) {
        String sourceClassName = responseWrapper == null ? "<null>" : responseWrapper.getClass().getName();
        try {
            BodyCandidate candidate = extractBodyCandidate(responseWrapper);
            if (!candidate.isFound()) {
                return new SplitterResponseSnapshot(sourceClassName, null, null, null, null);
            }
            if (candidate.getBody() == null || candidate.getBody().isBlank()) {
                return new SplitterResponseSnapshot(sourceClassName, Objects.toString(candidate.getBody(), ""), null, null, null);
            }
            try {
                JsonNode root = OBJECT_MAPPER.readTree(candidate.getBody());
                return new SplitterResponseSnapshot(sourceClassName, candidate.getBody(), root, null, null);
            } catch (Exception parsingError) {
                return new SplitterResponseSnapshot(sourceClassName, candidate.getBody(), null, null, parsingError);
            }
        } catch (Exception extractionError) {
            return new SplitterResponseSnapshot(sourceClassName, null, null, extractionError, null);
        }
    }

    private static BodyCandidate extractBodyCandidate(Object responseWrapper) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Queue<Object> queue = new ArrayDeque<>();
        queue.add(responseWrapper);

        while (!queue.isEmpty()) {
            Object current = queue.poll();
            if (current == null || visited.contains(current)) {
                continue;
            }
            visited.add(current);

            BodyCandidate directBody = tryDirectBodyExtraction(current);
            if (directBody.isFound()) {
                return directBody;
            }

            enqueueKnownNavigationTargets(current, queue);
            enqueueInterestingFields(current, queue, visited);
        }

        return BodyCandidate.notFound();
    }

    private static BodyCandidate tryDirectBodyExtraction(Object current) {
        if (current instanceof String) {
            return BodyCandidate.found((String) current);
        }
        for (String methodName : DIRECT_STRING_METHODS) {
            try {
                Method method = current.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    Object value = method.invoke(current);
                    return BodyCandidate.found((String) value);
                }
            } catch (Exception ignored) {
                // try next strategy
            }
        }
        return BodyCandidate.notFound();
    }

    private static void enqueueKnownNavigationTargets(Object current, Queue<Object> queue) {
        for (String methodName : NAVIGATION_METHODS) {
            try {
                Method method = current.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    Object next = method.invoke(current);
                    if (next != null && next != current) {
                        queue.add(next);
                    }
                }
            } catch (Exception ignored) {
                // try next strategy
            }
        }
    }

    private static void enqueueInterestingFields(Object current, Queue<Object> queue, Set<Object> visited) {
        Class<?> type = current.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(current);
                    if (value == null || value == current || visited.contains(value)) {
                        continue;
                    }
                    String className = value.getClass().getName();
                    if (className.startsWith("io.restassured")
                            || className.startsWith("ru.sber.qa")
                            || className.contains("Response")
                            || className.contains("Validatable")) {
                        queue.add(value);
                    }
                } catch (Exception ignored) {
                    // keep searching
                }
            }
            type = type.getSuperclass();
        }
    }

    private static final class BodyCandidate {
        private final boolean found;
        private final String body;

        private BodyCandidate(boolean found, String body) {
            this.found = found;
            this.body = body;
        }

        public static BodyCandidate found(String body) {
            return new BodyCandidate(true, body);
        }

        public static BodyCandidate notFound() {
            return new BodyCandidate(false, null);
        }

        public boolean isFound() {
            return found;
        }

        public String getBody() {
            return body;
        }
    }
}
