package request.explab2434.layers;

import dto.experiments.layers.LayerGetChangeRequestDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class LayerRegistryV2TestDataFactory {
    private static final String DEFAULT_SPLITTING_POINT = "MAPPER";

    private LayerRegistryV2TestDataFactory() {
    }

    public record LayerFixture(
            String name,
            int priority,
            long startDt,
            long endDt,
            long shareFrom,
            long shareTo,
            String splittingPointCode
    ) {
        public long shareTotal() {
            return shareTo - shareFrom;
        }
    }

    public static String uniquePrefix() {
        return "EXPLAB-2434-AQA-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static List<LayerFixture> threeMapperLayers(String prefix) {
        long jan2026 = Instant.parse("2026-01-10T00:00:00Z").toEpochMilli();
        long feb2026 = Instant.parse("2026-02-10T00:00:00Z").toEpochMilli();
        long mar2026 = Instant.parse("2026-03-10T00:00:00Z").toEpochMilli();
        long oct2026 = Instant.parse("2026-10-10T00:00:00Z").toEpochMilli();
        long nov2026 = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli();
        long dec2026 = Instant.parse("2026-12-10T00:00:00Z").toEpochMilli();

        return List.of(
                new LayerFixture(prefix + "-ALPHA", 10, jan2026, oct2026, 0, 1000, DEFAULT_SPLITTING_POINT),
                new LayerFixture(prefix + "-BETA", 30, feb2026, nov2026, 1000, 3000, DEFAULT_SPLITTING_POINT),
                new LayerFixture(prefix + "-GAMMA", 20, mar2026, dec2026, 3000, 6000, DEFAULT_SPLITTING_POINT)
        );
    }

    public static LayerGetChangeRequestDto layerRequest(LayerFixture fixture) {
        return LayerGetChangeRequestDto.builder()
                .name(fixture.name())
                .description("Слой для функциональных тестов EXPLAB-2434")
                .priority(fixture.priority())
                .startDt(fixture.startDt())
                .endDt(fixture.endDt())
                .salt("AQA" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .shares(List.of(LayerGetChangeRequestDto.Share.builder()
                        .shareFrom(fixture.shareFrom())
                        .shareTo(fixture.shareTo())
                        .build()))
                .splittingPointCode(fixture.splittingPointCode())
                .build();
    }
}
