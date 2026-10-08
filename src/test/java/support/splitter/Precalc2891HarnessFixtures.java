package support.splitter;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;

import util.splittercheck.PrecalcResponseAssertions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import infrastructure.kubernetes.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

/** Reusable offline fixtures; test cases remain in their ticket package. */
public abstract class Precalc2891HarnessFixtures {
    public static ObjectNode body(String code, String details) {
        return dto.splitter.precalc.MapperPrecalcRequests.JSON.createObjectNode().put("requestId", "id").put("errorCode", code).put("errorDetails", details);
    }
    public static void check(int status, ObjectNode response) {
        PrecalcResponseAssertions.rejected(status, response, dto.splitter.precalc.MapperPrecalcRequests.JSON.createObjectNode().put("requestId", "id"),
                "/api/v1/splitter/pre-calculate");
    }
}
