package com.tetrio.analytics.player.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "snapshots")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_snapshots_player_window_unique",
                def = "{'playerId': 1, 'cacheWindowKey': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_snapshots_player_capturedAt",
                def = "{'playerId': 1, 'capturedAt': -1}"
        )
})
public record SnapshotDocument(
        @Id String id,
        String playerId,
        String cacheWindowKey,
        Instant windowStartAt,
        Instant windowUntilAt,
        Instant capturedAt,
        Double rating,
        Double glicko,
        Double rd,
        String rank,
        SnapshotMetrics metrics,
        SnapshotSource source,
        int schemaVersion
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record SnapshotMetrics(
            Double apm,
            Double pps,
            Double vs,
            Double gxe,
            int gamesPlayed,
            int gamesWon,
            Integer standing,
            Double percentile,
            String bestRank,
            boolean ranked,
            boolean decaying
    ) {}

    public record SnapshotSource(
            Long userCachedAt,
            Long userCachedUntil,
            Long leagueCachedAt,
            Long leagueCachedUntil,
            String sessionId
    ) {}
}
