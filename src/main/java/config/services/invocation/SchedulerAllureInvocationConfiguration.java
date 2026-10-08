package config.services.invocation;

import infrastructure.scheduler.SchedulerAllurePresentation;
import io.perfeccionista.framework.invocation.AllureInvocationServiceConfiguration;
import io.perfeccionista.framework.invocation.runner.*;
import io.perfeccionista.framework.invocation.runner.visitors.AllureAndLogAttemptInvocationRunnerVisitor;
import io.perfeccionista.framework.invocation.wrapper.*;
import io.qameta.allure.Allure;
import io.qameta.allure.model.StepResult;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Scheduler-only adapter for platform-v 1.10.3-beta; execution/retry/exception handling stays inherited. */
public final class SchedulerAllureInvocationConfiguration extends AllureInvocationServiceConfiguration {
    @Override public Class<? extends InvocationRunner> getInvocationRunnerImplementation(Class<?> wrapper) {
        if (SingleAttemptInvocationWrapper.class.equals(wrapper)) return Single.class;
        if (MultipleAttemptInvocationWrapper.class.equals(wrapper)) return Multiple.class;
        return super.getInvocationRunnerImplementation(wrapper);
    }
    public static final class Single extends AllureSingleAttemptInvocationRunner {
        public Single() { visitor = new Visitor(); }
    }
    public static final class Multiple extends AllureMultipleAttemptInvocationRunner {
        public Multiple() { visitor = new Visitor(); }
    }
    public static final class Visitor extends AllureAndLogAttemptInvocationRunnerVisitor {
        private static final Logger LOG = LoggerFactory.getLogger(Visitor.class);
        @Override public Consumer<InvocationInfo> startInvocation(int indent) {
            return info -> {
                LOG.info("{}{}", getIndent(indent), info.toString());
                if (info.getResults().isEmpty()) {
                    Allure.getLifecycle().startStep(info.getUuid(), new StepResult()
                            .setName(SchedulerAllurePresentation.translate(info.getInvocationName())));
                }
                // Keep framework retry semantics, but never update a TestResult through a fixture UUID.
                // The framework label is applied by SchedulerAllureContainerListener to the actual result.
                Allure.getLifecycle().updateStep(info.getUuid(), step -> step.setSteps(new ArrayList<>()));
            };
        }
    }
}
