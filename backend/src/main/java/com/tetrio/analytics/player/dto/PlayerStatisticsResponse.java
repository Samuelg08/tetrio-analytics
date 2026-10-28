package com.tetrio.analytics.player.dto;

import java.time.Instant;
import java.util.List;

public record PlayerStatisticsResponse(
        String playerId,
        String username,
        Instant lastSyncedAt,
        RawStatisticsDto raw,
        DerivedStatisticsDto derived,
        List<MetricDefinitionDto> definitions
) {

    public record RawStatisticsDto(
            int gamesPlayed,
            int gamesWon,
            Double tr,
            Double glicko,
            Double rd,
            Double gxe,
            Double apm,
            Double pps,
            Double vs,
            Double xp,
            Double gametimeSeconds
    ) {}

    public record DerivedStatisticsDto(
            Integer gamesLost,
            Double winRate,
            Double attackPerPiece,
            Double vsPerApm,
            Double gametimeHours
    ) {}

    public record MetricDefinitionDto(
            String key,
            String formula,
            String unit,
            List<String> sourceFields
    ) {}
}
