package steps.flow.splitter.workedgroup;

import org.junit.jupiter.api.*;

import org.junit.jupiter.api.parallel.*;

import static org.junit.jupiter.api.Assertions.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class ReactionsGroupsSteps extends WorkedGroupSteps {
    @Override protected EndpointMode endpointMode() { return EndpointMode.REACTIONS; }
}
