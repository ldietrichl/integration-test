package steps.rest.splitter;

import constants.Endpoints;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;

public final class SplitterKafkaConfigLoadFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification requestSpecification,
                           FilterableResponseSpecification responseSpecification,
                           FilterContext context) {
        if (!shouldRouteToKafka(requestSpecification)) {
            return context.next(requestSpecification, responseSpecification);
        }
        return SplitterKafkaConfigLoadClient.loadResponse(
                requestSpecification.getBody(),
                endpointSplittingPointCode(requestSpecification));
    }

    private static boolean shouldRouteToKafka(FilterableRequestSpecification requestSpecification) {
        return SplitterConfigLoadMode.isKafka()
                && "POST".equalsIgnoreCase(requestSpecification.getMethod())
                && isConfigPath(path(requestSpecification.getURI()));
    }

    private static boolean isConfigPath(String path) {
        return path.endsWith(Endpoints.Splitter.SPLITTER_CONFIG)
                || path.endsWith(Endpoints.Splitter.SPLITTER_REACTIONS_CONFIG);
    }

    private static String endpointSplittingPointCode(FilterableRequestSpecification requestSpecification) {
        String path = path(requestSpecification.getURI());
        if (path.endsWith(Endpoints.Splitter.SPLITTER_REACTIONS_CONFIG)) {
            return "REACTIONS";
        }
        return "MAPPER";
    }

    private static String path(String uri) {
        try {
            return URI.create(uri).getPath();
        } catch (IllegalArgumentException exception) {
            return uri;
        }
    }
}
