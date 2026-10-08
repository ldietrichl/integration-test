package request.pilot;

import dto.pilot.PilotRegistryRequestDto;
import dto.pilot.PilotRequestDto;
import dto.pilot.PilotStatusUpdateRequestDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PilotTestDataFactory {
    private PilotTestDataFactory() {
    }

    public static String uniquePrefix() {
        return "PILOT-AQA-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static PilotRequestDto minimalDraftPilot(String name) {
        return PilotRequestDto.builder()
                .name(name)
                .launchStatus("DRAFT")
                .linkedConfigComIds(List.of())
                .linkedCampaignIds(List.of())
                .linkedExperimentPromIds(List.of())
                .linkedExperimentDkgIds(List.of())
                .linkedPilotIds(List.of())
                .build();
    }

    public static PilotRequestDto draftPilotWithMainFields(String name) {
        return minimalDraftPilot(name).toBuilder()
                .startDt(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli())
                .endDt(Instant.parse("2026-09-30T00:00:00Z").toEpochMilli())
                .communicationChannel(List.of(PilotRequestDto.CommunicationChannel.builder()
                        .id(1L)
                        .name("SMS")
                        .build()))
                .initiatorLabel(List.of("Первичный анализ проведен"))
                .goalDescription("AQA цель пилота")
                .targetAudience("AQA целевая аудитория")
                .customerPath("AQA клиентский путь")
                .successMetricDescription("AQA метрика успеха")
                .whyItWillWork("AQA гипотеза")
                .baseMetricValue(0.1)
                .targetMetricValue(0.2)
                .pilotVarCount(2L)
                .replicationBase(20L)
                .controlGroupPct(2L)
                .dynamicCGRequired(false)
                .calcPilotGroupSize(10L)
                .estimPilotClients(20L)
                .finalClientAllocReq(100L)
                .validatorLabel(List.of("Требуется уточнение условий"))
                .comments(PilotRequestDto.Comment.builder()
                        .name("AQA Автотест")
                        .commentText("Создание пилота автотестом")
                        .dateComment(Instant.now().toEpochMilli())
                        .build())
                .assignOrder(999)
                .fastTrackSwitcher(false)
                .sendToOptimizerSwitcher(true)
                .expPromSwitcher(true)
                .expDkgSwitcher(true)
                .build();
    }

    public static PilotRequestDto withoutName() {
        return minimalDraftPilot(null);
    }

    public static PilotRequestDto withInvalidStatus(String name) {
        return minimalDraftPilot(name).toBuilder()
                .launchStatus("UNKNOWN_STATUS")
                .build();
    }

    public static PilotRegistryRequestDto firstPageRegistry() {
        return PilotRegistryRequestDto.builder()
                .page(0)
                .size(10)
                .isOwn(false)
                .build();
    }

    public static PilotRegistryRequestDto registrySearch(String search) {
        return firstPageRegistry().toBuilder()
                .search(search)
                .size(20)
                .build();
    }

    public static PilotRegistryRequestDto registrySortedByNameAsc(String search) {
        return registrySearch(search).toBuilder()
                .sorts(List.of(PilotRegistryRequestDto.Sort.builder()
                        .paramCode("name")
                        .direction("ASC")
                        .build()))
                .build();
    }

    public static PilotRegistryRequestDto invalidRegistryRequest() {
        return PilotRegistryRequestDto.builder()
                .page(-1)
                .size(0)
                .isOwn(false)
                .build();
    }

    public static PilotStatusUpdateRequestDto keepDraftStatusRequest() {
        return PilotStatusUpdateRequestDto.builder()
                .launchStatus("DRAFT")
                .comments(PilotRequestDto.Comment.builder()
                        .name("AQA Автотест")
                        .commentText("Проверка endpoint смены статуса")
                        .dateComment(Instant.now().toEpochMilli())
                        .build())
                .initiatorLabel(List.of("Первичный анализ проведен"))
                .validatorLabel(List.of("Требуется уточнение условий"))
                .build();
    }

    public static PilotStatusUpdateRequestDto statusWithoutLaunchStatus() {
        return PilotStatusUpdateRequestDto.builder()
                .comments(PilotRequestDto.Comment.builder()
                        .name("AQA Автотест")
                        .commentText("Некорректный запрос без статуса")
                        .dateComment(Instant.now().toEpochMilli())
                        .build())
                .build();
    }
}
