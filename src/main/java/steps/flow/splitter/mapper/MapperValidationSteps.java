package steps.flow.splitter.mapper;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;

import steps.reporting.ReportingSteps;

import util.splittercheck.PrecalcResponseAssertions;

import com.fasterxml.jackson.databind.node.ObjectNode;
import constants.SplitterEndpointPaths;

import static org.junit.jupiter.api.Assertions.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperValidationSteps extends MapperPrecalcSteps {

    public void preserved(FlowWithRest f) {
        ReportingSteps.step("Проверить сохранность связанного объекта и объекта без связей", () -> {
            // Contradictory current parameters distinguish saved links from dynamic calculation.
            assertMainA(splitOne(f, "U1", "silver"), 101);
            assertNoResult(splitOne(f, "U2", "gold"), "O1");
            // This also detects partially inserted objects without links (a negative split alone cannot).
            assertCounters(calculate(f, pre(103, preObject("U1", "gold"), preObject("U2", "silver"))),
                    2, 0, 0, 1, 2, 1, 1);
        });
    }

    public void reject(FlowWithRest f, ObjectNode request) {
        ReportingSteps.step("Проверить HTTP-отказ и формат ошибки валидации", () -> {
            var response = rawCalculate(f, request);
            PrecalcResponseAssertions.rejected(response.toResponse().statusCode(), body(response), request,
                    SplitterEndpointPaths.mapperPrecalculate());
        });
    }

    public static void mutate(ObjectNode owner, String field, String mutation) {
        switch (mutation) {
            case "null" -> owner.putNull(field);
            case "empty" -> owner.put(field, "");
            case "empty-array" -> owner.putArray(field);
            case "missing" -> owner.remove(field);
            default -> throw new IllegalArgumentException(mutation);
        }
    }
}
