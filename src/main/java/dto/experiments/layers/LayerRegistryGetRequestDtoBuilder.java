package dto.experiments.layers;

import java.util.List;

public class LayerRegistryGetRequestDtoBuilder {

    public static LayerRegistryGetRequestDto buildDefaultDto() {
        return LayerRegistryGetRequestDto.builder()
                .page(0)
                .size(10)
                .filters(List.of(LayerRegistryGetRequestDto.Filter.builder()
                        .code("name")
                        .operator("more")
                        .values(List.of("5", "10"))
                        .build()))
                .sorts(List.of(LayerRegistryGetRequestDto.Sort.builder()
                        .code("name")
                        .direction("ASC")
                        .build()))
                .build();
    }
}
