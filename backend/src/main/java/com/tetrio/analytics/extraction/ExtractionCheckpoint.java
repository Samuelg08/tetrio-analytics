package com.tetrio.analytics.extraction;

import java.time.Instant;
import java.util.Map;

public record ExtractionCheckpoint(
        String runId,
        String status,
        String phase,
        String sessionId,
        String lastCursor,
        int pagesCompleted,
        String seenPlayerIdsFile,
        Counters counters,
        Instant startedAt,
        Instant updatedAt,
        Long upstreamRankedCountAtStart,
        String datasetVersion
) {

    public record Counters(
            int requestsSent,
            int playersSeen,
            int uniquePlayers,
            int duplicatesSkipped,
            int cachedSkipped,
            int rejectedRecords,
            Map<String, Integer> httpErrorsByStatus
    ) {}
}
