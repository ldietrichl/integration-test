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

public class RestCustomSteps {
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
}
