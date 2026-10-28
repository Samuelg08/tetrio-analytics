package com.tetrio.analytics.comparison.dto;

import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import java.time.Instant;

public record ComparisonResponse(
        PlayerProfileResponse playerA,
        PlayerProfileResponse playerB,
        MetricDifferencesDto differences,
        Object headToHead,
        Instant generatedAt
) {}
