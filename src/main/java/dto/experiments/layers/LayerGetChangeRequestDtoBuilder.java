package dto.experiments.layers;

import java.util.List;

public class LayerGetChangeRequestDtoBuilder {

    public static LayerGetChangeRequestDto buildDefaultDto() {
        return LayerGetChangeRequestDto.builder()
                .name("Слой тестовый")
                .description("Слой для важных задач")
                .priority(1)
                .startDt(1717016400000L)
                .endDt(1719781140000L)
                .salt("240512Pqv6")
                .shares(List.of(LayerGetChangeRequestDto.Share.builder()
                        .shareFrom(100L)
                        .shareTo(500L)
                        .build()))
                .splittingPointCode("MAPPER")
                .build();
    }
}
