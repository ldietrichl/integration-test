package util.support;

import ru.sber.qa.services.rest.matchers.core.ValidatableResponseWrapperMatcher;

/** Checks semantic rejection for a direct SDK host and an HTTP-validating gateway. */
public final class ConfigLoadRejectionMatcher {
    private ConfigLoadRejectionMatcher() { }

    public static ValidatableResponseWrapperMatcher rejected() {
        return wrapper -> {
            var response = wrapper.toResponse();
            int status = response.statusCode();
            if (status == 400) {
                return; // The gateway rejected the request before SDK activation.
            }
            if (status == 200 && "CONFIG_ERROR".equals(response.jsonPath().getString("result"))) {
                return; // The SDK processed the request and explicitly rejected its config.
            }
            throw new AssertionError("Expected HTTP 400 rejection or HTTP 200 CONFIG_ERROR, got "
                    + status + ": " + response.asString());
        };
    }
}
