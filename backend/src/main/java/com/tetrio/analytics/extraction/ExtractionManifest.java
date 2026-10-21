package com.tetrio.analytics.extraction;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExtractionManifest(
        String datasetVersion,
        String runId,
        Instant createdAt,
        ExtractionWindow extractionWindow,
        List<SourceMetadata> sources,
        String featureSchemaVersion,
        List<String> transformationsApplied,
        ManifestCounts counts,
        Map<String, FieldQualityStats> fields
) {

    public record ExtractionWindow(
            Instant startedAt,
            Instant finishedAt,
            long durationSeconds
    ) {}

    public record SourceMetadata(
            String endpoint,
            String baseUrl,
            int limit,
            boolean sessionIdUsed
    ) {}

    public record ManifestCounts(
            Long upstreamRankedCountAtStart,
            int pagesFetched,
            int rawEntriesRead,
            int uniquePlayersExported,
            int duplicatesSkipped,
            int cachedSkipped,
            int rejectedRecords,
            Map<String, Integer> httpErrorsByStatus
    ) {}

    public record FieldQualityStats(
            String type,
            boolean nullable,
            int nullCount,
            double nullRate
    ) {}
}
