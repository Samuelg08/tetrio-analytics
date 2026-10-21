package com.tetrio.analytics.player.dto;

import java.time.Instant;

public record SnapshotItemDto(
        Instant capturedAt,
        Double tr,
        Double glicko,
        Double rd,
        Double gxe,
        String rank,
        Double apm,
        Double pps,
        Double vs,
        int gamesPlayed,
        int gamesWon,
        Integer standing
) {}
