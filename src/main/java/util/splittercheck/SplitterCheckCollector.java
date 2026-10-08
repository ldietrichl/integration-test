package util.splittercheck;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SplitterCheckCollector {

    private final String checkName;
    private final List<String> violations = new ArrayList<>();

    public SplitterCheckCollector(String checkName) {
        this.checkName = checkName;
    }

    public void addViolation(String message) {
        violations.add(message);
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public List<String> getViolations() {
        return List.copyOf(violations);
    }

    public void finish(SplitterCheckMode mode, SplitterResponseSnapshot snapshot) {
        if (!hasViolations() || mode == SplitterCheckMode.OFF) {
            return;
        }
        String details = format(snapshot);
        if (mode.isFailFast()) {
            throw new AssertionError(details);
        }
        System.err.println(details);
    }

    private String format(SplitterResponseSnapshot snapshot) {
        String joined = violations.stream()
                .map(message -> " - " + message)
                .collect(Collectors.joining("\n"));
        return "[SplitterStrictCheck][" + checkName + "] Найдены расхождения:\n"
                + joined
                + "\nResponse body:\n"
                + snapshot.prettyBody();
    }
}
