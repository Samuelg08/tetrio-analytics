package com.tetrio.analytics.player.dto;

import java.time.Instant;

public record PlayerProfileResponse(
        String playerId,
        String username,
        String country,
        Instant lastSyncedAt,
        DataSource dataSource,
        LeagueSummaryDto league
) {}
