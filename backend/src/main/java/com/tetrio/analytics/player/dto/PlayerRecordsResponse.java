package com.tetrio.analytics.player.dto;

import java.time.Instant;
import java.util.Map;

public record PlayerRecordsResponse(
        String playerId,
        Instant lastSyncedAt,
        DataSource dataSource,
        Map<String, ModeRecordDto> modes
) {

    public record ModeRecordDto(
            String mode,
            String recordId,
            String replayId,
            String primaryMetric,
            Double primaryValue,
            Double finalTimeMs,
            Long score,
            Integer lines,
            Integer level,
            Double pps,
            Integer piecesPlaced,
            Integer inputs,
            Double kpp,
            Integer tSpins,
            Integer finesseFaults,
            Integer globalRank,
            Integer countryRank,
            Instant achievedAt
    ) {}
}
