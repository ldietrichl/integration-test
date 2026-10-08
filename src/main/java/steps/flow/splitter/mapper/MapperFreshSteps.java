package steps.flow.splitter.mapper;
import static dto.splitter.precalc.MapperPrecalcRequests.*;
import static util.splittercheck.MapperPrecalcAssertions.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Reusable scenario operations; test cases remain in their ticket package. */
public abstract class MapperFreshSteps extends MapperPrecalcSteps {
    @Override public String managedProfile() { return "FRESH"; }
}
