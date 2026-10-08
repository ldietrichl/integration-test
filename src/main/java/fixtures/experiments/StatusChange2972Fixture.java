package fixtures.experiments;

import config.services.core.StatusChange2972Settings;
import io.perfeccionista.framework.fixture.Fixture;
import io.perfeccionista.framework.fixture.FixtureSetUpResult;
import io.perfeccionista.framework.fixture.FixtureTearDownResult;
import steps.rest.experiments.v2.StatusChange2972Steps;

/** Environment's FixtureService guarantees teardown for every parameterized invocation. */
public final class StatusChange2972Fixture implements Fixture<StatusChange2972Steps, Void> {
    private final StatusChange2972Settings settings;
    private final int scenario;
    private StatusChange2972Steps steps;

    public StatusChange2972Fixture(StatusChange2972Settings settings, int scenario) {
        this.settings = settings;
        this.scenario = scenario;
    }

    @Override public FixtureSetUpResult<StatusChange2972Steps> setUp() {
        steps = new StatusChange2972Steps(settings, scenario);
        return FixtureSetUpResult.of(steps);
    }

    @Override public FixtureTearDownResult<Void> tearDown() {
        if (steps != null) {
            try { steps.close(); }
            catch (Exception error) { return FixtureTearDownResult.exception(new IllegalStateException("Fixture cleanup failed", error)); }
        }
        return FixtureTearDownResult.empty();
    }
}
