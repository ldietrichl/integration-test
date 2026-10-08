package flow;

import io.perfeccionista.framework.Environment;
import ru.sber.qa.services.rest.RestClient;
import ru.sber.qa.services.rest.RestService;
import steps.rest.RestCustomSteps;

public interface RestCustomFlow {
    default RestCustomSteps restCustomSteps() {
        return new RestCustomSteps(
                Environment.getForCurrentThread().getService(RestService.class).restClient()
        );
    }

    default RestClient restClient() {
        return Environment.getForCurrentThread().getService(RestService.class).restClient();
    }
}
