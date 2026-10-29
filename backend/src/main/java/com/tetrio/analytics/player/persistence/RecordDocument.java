package com.tetrio.analytics.player.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "records")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_records_player_mode_type_unique",
                def = "{'playerId': 1, 'mode': 1, 'recordType': 1}",
                unique = true
        )
})
public record RecordDocument(
        @Id String id,
        String playerId,
        String mode,
        String recordType,
        String recordId,
        String replayId,
        String primaryMetric,
        Double value,
        Double finalTimeMs,
        Long score,
        Integer lines,
        Integer level,
        Double pps,
        Integer piecesPlaced,
        Integer inputs,
        Integer tSpins,
        Integer finesseFaults,
        Integer globalRank,
        Integer countryRank,
        Instant achievedAt,
        Instant lastSyncedAt,
        Instant cacheValidUntil,
        RecordSource source
) {
    public static final String RECORD_TYPE_PERSONAL_BEST = "personal_best";

    public record RecordSource(
            Long cachedAt,
            Long cachedUntil,
            String sessionId
    ) {}
}
