package com.tetrio.analytics.extraction;

import java.time.Instant;

public record ProcessedPlayerRecord(
        String playerId,
        String username,
        String country,
        Double xp,
        Double gametime,
        int gamesPlayed,
        int gamesWon,
        boolean ranked,
        Double tr,
        Double glicko,
        Double rd,
        Double gxe,
        String rank,
        String bestRank,
        Double apm,
        Double pps,
        Double vs,
        Integer standing,
        Double percentile,
        boolean decaying,
        String runId,
        Instant capturedAt
) {}
