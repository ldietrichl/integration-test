package request.explab2539.layers;

import dto.experiments.layers.LayerGetChangeRequestDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LayerV2ByIdTestDataFactory {

    private LayerV2ByIdTestDataFactory() {
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
    }

    public static String uniquePrefix() {
        return "EXPLAB-2539-AQA-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static LayerFixture mapperLayer(String prefix) {
        return layer(prefix + "-MAPPER", 10, "MAPPER", 0, 1000);
    }

    public static LayerFixture reactionsLayer(String prefix) {
        return layer(prefix + "-REACTIONS", 20, "REACTIONS", 1000, 2000);
    }

    public static LayerGetChangeRequestDto layerRequest(LayerFixture fixture) {
        return LayerGetChangeRequestDto.builder()
                .name(fixture.name())
                .description("Слой для функциональных тестов EXPLAB-2539")
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

    private static LayerFixture layer(String name, int priority, String splittingPointCode, long shareFrom, long shareTo) {
        long startDt = Instant.parse("2026-01-10T00:00:00Z").toEpochMilli();
        long endDt = Instant.parse("2026-12-10T00:00:00Z").toEpochMilli();
        return new LayerFixture(name, priority, startDt, endDt, shareFrom, shareTo, splittingPointCode);
    }
}
