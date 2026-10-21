package com.tetrio.analytics.player.persistence;

import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "players")
public record PlayerDocument(
        @Id String id,
        @Indexed(name = "idx_players_externalPlayerId_unique", unique = true) String externalPlayerId,
        String username,
        @Indexed(name = "idx_players_usernameLower") String usernameLower,
        String country,
        ProfileMetadata profile,
        LeagueSummaryDto league,
        Instant lastSyncedAt,
        Instant cacheWindowStartAt,
        Instant cacheValidUntil,
        String activeCacheWindowKey,
        int sourceSchemaVersion
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public record ProfileMetadata(
            Double xp,
            Double gametime
    ) {}
}
