package com.tetrio.analytics.player.dto;

public record LeagueSummaryDto(
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
        boolean decaying
) {}
