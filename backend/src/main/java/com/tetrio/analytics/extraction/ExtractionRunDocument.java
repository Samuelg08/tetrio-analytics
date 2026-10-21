package com.tetrio.analytics.extraction;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "extraction_runs")
public record ExtractionRunDocument(
        @Id String runId,
        Instant startedAt,
        Instant updatedAt,
        Instant finishedAt,
        String status,
        String phase,
        String cursor,
        String sessionId,
        RunParameters parameters,
        ExtractionCheckpoint.Counters counters,
        String rawPath,
        String datasetVersion
) {

    public record RunParameters(
            List<String> phases,
            long minIntervalMs,
            int batchSize,
            int maxPages
    ) {}
}
