package steps.rest;

import ru.sber.qa.services.rest.RestClient;
import steps.rest.dictionaries.v1.DictionariesV1Steps;
import steps.rest.dataoperator.DataOperatorSteps;
import steps.rest.configurations.v2.ConfigurationsSteps;
import steps.rest.experiments.v1.ExperimentsV1Steps;
import steps.rest.experiments.v1.layer.LayerV1Steps;
import steps.rest.experiments.v1.split.SplitV1Steps;
import steps.rest.experiments.v2.ExperimentsV2Steps;
import steps.rest.experiments.v2.layers.LayerV2Steps;
import steps.rest.pilot.PilotSteps;
import steps.rest.splitter.SplitterRestSteps;
import steps.rest.dataoperator.v2.DataOperatorV2Steps;
import steps.rest.user.UserServiceRegressionSteps;

public class RestCustomSteps {
    public steps.flow.scheduler.SchedulerRealStandRegressionSteps schedulerRealStandRegressionSteps() {
        return new steps.flow.scheduler.SchedulerRealStandRegressionSteps();
    }

    public steps.flow.scheduler.SchedulerApiRegressionSteps schedulerApiRegressionSteps() {
        return new steps.flow.scheduler.SchedulerApiRegressionSteps();
    }

    public steps.flow.scheduler.SchedulerAutonomousRegressionSteps schedulerAutonomousRegressionSteps() {
        return new steps.flow.scheduler.SchedulerAutonomousRegressionSteps();
    }

    public steps.flow.scheduler.SchedulerWorkloadRegressionSteps schedulerWorkloadRegressionSteps() {
        return new steps.flow.scheduler.SchedulerWorkloadRegressionSteps();
    }

    public steps.flow.scheduler.SchedulerReadOnlyRegressionSteps schedulerReadOnlyRegressionSteps() {
        return new steps.flow.scheduler.SchedulerReadOnlyRegressionSteps();
    }

    public steps.flow.scheduler.SchedulerPreflightSteps schedulerPreflightSteps() {
        return new steps.flow.scheduler.SchedulerPreflightSteps();
    }

    public steps.rest.scheduler.SchedulerSteps schedulerSteps() {
        return new steps.rest.scheduler.SchedulerSteps(this.client);
    }

    public steps.rest.scheduler.SchedulerRegistrySteps schedulerRegistrySteps() {
        return infrastructure.kubernetes.SchedulerRegressionSession.registrySteps(this.client);
    }

    public UserServiceRegressionSteps schedulerIdentityUserSteps() {
        return infrastructure.kubernetes.SchedulerRegressionSession.userSteps(this.client);
    }

    RestClient client;

    public RestCustomSteps(RestClient client) {
        this.client = client;
    }

    public ExperimentsV1Steps experimentsV1Steps() {
        return new ExperimentsV1Steps(this.client);
    }

    public SplitV1Steps splitSteps() {
        return new SplitV1Steps(this.client);
    }

    public LayerV1Steps layerSteps() {
        return new LayerV1Steps(this.client);
    }

    public ExperimentsV2Steps experimentsV2Steps() {
        return new ExperimentsV2Steps(this.client);
    }

    public LayerV2Steps layerV2Steps() {
        return new LayerV2Steps(this.client);
    }

    public DictionariesV1Steps dictionariesV1Steps() {
        return new DictionariesV1Steps(this.client);
    }

    public ConfigurationsSteps configurationsSteps() {
        return new ConfigurationsSteps(this.client);
    }

    public SplitterRestSteps splitterSteps() {
        return new SplitterRestSteps(this.client);
    }

    public DataOperatorSteps dataOperatorSteps() {
        return new DataOperatorSteps(this.client);
    }

    public DataOperatorV2Steps dataOperatorV2Steps() {
        return new DataOperatorV2Steps(this.client);
    }

    public PilotSteps pilotSteps() {
        return new PilotSteps(this.client);
    }

    public UserServiceRegressionSteps userServiceSteps() {
        return new UserServiceRegressionSteps(this.client);
    }
}
