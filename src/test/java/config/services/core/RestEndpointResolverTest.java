package config.services.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

class RestEndpointResolverTest {

    @Test
    void currentEnvironmentAndAllRestUrisMustBeValid() {
        RestEndpointResolver.validateCurrentEnvironment();

        assertTrue(Set.of("dev", "ift", "ift-dm", "lt")
                .contains(RestEndpointResolver.currentEnvironment()));
        for (RestServiceEndpoint endpoint : RestServiceEndpoint.values()) {
            String uri = RestEndpointResolver.baseUri(endpoint);
            assertFalse(uri.isBlank());
            assertTrue(uri.startsWith("http://") || uri.startsWith("https://"));
            assertFalse(uri.endsWith("/"));
        }
    }

    @Test
    void logicalServicesMayUseGatewayWithoutDuplicatingUriValues() {
        String gateway = RestEndpointResolver.baseUri(RestServiceEndpoint.EXPLAB_GATEWAY);

        assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.EXPERIMENTS));
        assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.DICTIONARIES));
        assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.DATA_OPERATOR));
        assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.MESSAGES));
        assertEquals(gateway, RestEndpointResolver.baseUri(RestServiceEndpoint.SPLITTER));
    }
}
