package com.tetrio.analytics.extraction;

import java.time.Instant;

public record RejectedRecord(
        String runId,
        String playerId,
        String field,
        String originalValue,
        String reason,
        Instant rejectedAt
) {}
