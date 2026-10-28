package com.tetrio.analytics.ranking.dto;

import java.time.Instant;

public record RankingItemDto(
        int position,
        String playerId,
        String username,
        String country,
        Double value,
        Instant lastSyncedAt
) {}
