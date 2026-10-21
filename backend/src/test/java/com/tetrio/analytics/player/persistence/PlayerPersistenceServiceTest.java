package com.tetrio.analytics.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class PlayerPersistenceServiceTest {

    private static final String PLAYER_ID = "5e9d4eec530bc13f9fdcd2c0";
    private static final String WINDOW_KEY = PLAYER_ID + ":1700000000000";

    private MongoTemplate mongoTemplate;
    private PlayerPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        persistenceService = new PlayerPersistenceService(mongoTemplate);
    }

    @Test
    void upsertsPlayerWithSetAndSnapshotWithSetOnInsertForIdempotency() {
        Instant windowStart = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant windowUntil = Instant.ofEpochMilli(1_700_000_300_000L);
        Instant syncedAt = Instant.ofEpochMilli(1_700_000_010_000L);

        LeagueSummaryDto league = new LeagueSummaryDto(
                100, 60, true, 24000.0, 2200.0, 65.0, 99.1,
                "x", "x", 150.0, 2.5, 310.0, 50, 0.01, false
        );

        PlayerDocument player = new PlayerDocument(
                null,
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(10000.0, 5000.0),
                league,
                syncedAt,
                windowStart,
                windowUntil,
                WINDOW_KEY,
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );

        SnapshotDocument snapshot = new SnapshotDocument(
                null,
                PLAYER_ID,
                WINDOW_KEY,
                windowStart,
                windowUntil,
                syncedAt,
                24000.0,
                2200.0,
                65.0,
                "x",
                new SnapshotDocument.SnapshotMetrics(150.0, 2.5, 310.0, 99.1, 100, 60, 50, 0.01, "x", true, false),
                new SnapshotDocument.SnapshotSource(1_700_000_000_000L, 1_700_000_300_000L, 1_700_000_000_000L, 1_700_000_300_000L, "live-1"),
                SnapshotDocument.CURRENT_SCHEMA_VERSION
        );

        persistenceService.saveSyncedPlayerAndSnapshot(player, snapshot);

        ArgumentCaptor<Query> playerQueryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> playerUpdateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(playerQueryCaptor.capture(), playerUpdateCaptor.capture(), eq(PlayerDocument.class));

        Document playerQueryDoc = playerQueryCaptor.getValue().getQueryObject();
        assertThat(playerQueryDoc.getString("externalPlayerId")).isEqualTo(PLAYER_ID);

        Document playerUpdateDoc = playerUpdateCaptor.getValue().getUpdateObject();
        assertThat(playerUpdateDoc).containsKey("$set");
        Document setFields = playerUpdateDoc.get("$set", Document.class);
        assertThat(setFields.getString("usernameLower")).isEqualTo("5han");
        assertThat(setFields.getString("activeCacheWindowKey")).isEqualTo(WINDOW_KEY);

        ArgumentCaptor<Query> snapshotQueryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> snapshotUpdateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(snapshotQueryCaptor.capture(), snapshotUpdateCaptor.capture(), eq(SnapshotDocument.class));

        Document snapshotQueryDoc = snapshotQueryCaptor.getValue().getQueryObject();
        assertThat(snapshotQueryDoc.getString("playerId")).isEqualTo(PLAYER_ID);
        assertThat(snapshotQueryDoc.getString("cacheWindowKey")).isEqualTo(WINDOW_KEY);

        Document snapshotUpdateDoc = snapshotUpdateCaptor.getValue().getUpdateObject();
        assertThat(snapshotUpdateDoc).containsOnlyKeys("$setOnInsert");
        Document setOnInsertFields = snapshotUpdateDoc.get("$setOnInsert", Document.class);
        assertThat(setOnInsertFields.getString("playerId")).isEqualTo(PLAYER_ID);
        assertThat(setOnInsertFields.getString("cacheWindowKey")).isEqualTo(WINDOW_KEY);
    }

    @Test
    void rejectsSnapshotWithoutMandatoryCacheWindowKey() {
        Instant now = Instant.now();
        PlayerDocument player = new PlayerDocument(
                null, PLAYER_ID, "5han", "5han", "JP", null, null, now, now, now, WINDOW_KEY, 1
        );
        SnapshotDocument invalidSnapshot = new SnapshotDocument(
                null, PLAYER_ID, "  ", now, now, now, null, null, null, null, null, null, 1
        );

        assertThatThrownBy(() -> persistenceService.saveSyncedPlayerAndSnapshot(player, invalidSnapshot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cacheWindowKey");
    }

    @Test
    void definesRequiredMongoIndexesOnPlayerAndSnapshotDocuments() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.setAutoIndexCreation(true);
        mappingContext.getPersistentEntity(PlayerDocument.class);
        mappingContext.getPersistentEntity(SnapshotDocument.class);
        mappingContext.afterPropertiesSet();

        MongoPersistentEntityIndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        List<? extends IndexDefinition> playerIndexes =
                StreamSupport.stream(resolver.resolveIndexFor(PlayerDocument.class).spliterator(), false).toList();
        assertThat(playerIndexes).hasSize(2);
        assertThat(playerIndexes).anySatisfy(idx -> {
            assertThat(idx.getIndexKeys()).containsEntry("externalPlayerId", 1);
            assertThat(idx.getIndexOptions().getBoolean("unique")).isTrue();
        });
        assertThat(playerIndexes).anySatisfy(idx -> {
            assertThat(idx.getIndexKeys()).containsEntry("usernameLower", 1);
        });

        List<? extends IndexDefinition> snapshotIndexes =
                StreamSupport.stream(resolver.resolveIndexFor(SnapshotDocument.class).spliterator(), false).toList();
        assertThat(snapshotIndexes).hasSize(2);
        assertThat(snapshotIndexes).anySatisfy(idx -> {
            assertThat(idx.getIndexKeys())
                    .containsEntry("playerId", 1)
                    .containsEntry("cacheWindowKey", 1);
            assertThat(idx.getIndexOptions().getBoolean("unique")).isTrue();
            assertThat(idx.getIndexOptions().getBoolean("sparse", false)).isFalse();
        });
        assertThat(snapshotIndexes).anySatisfy(idx -> {
            assertThat(idx.getIndexKeys())
                    .containsEntry("playerId", 1)
                    .containsEntry("capturedAt", -1);
        });
    }

    @Test
    void queriesSnapshotsFilteredByDateRangeAndSortedAscending() {
        Instant from = Instant.parse("2026-10-20T00:00:00Z");
        Instant to = Instant.parse("2026-10-20T23:59:59Z");

        when(mongoTemplate.count(any(Query.class), eq(SnapshotDocument.class))).thenReturn(25L);
        when(mongoTemplate.find(any(Query.class), eq(SnapshotDocument.class))).thenReturn(List.of());

        PageResponse<SnapshotDocument> page =
                persistenceService.findSnapshotsByPlayerId(PLAYER_ID, from, to, 1, 10);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalItems()).isEqualTo(25L);
        assertThat(page.totalPages()).isEqualTo(3);

        ArgumentCaptor<Query> findQueryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findQueryCaptor.capture(), eq(SnapshotDocument.class));

        Query executed = findQueryCaptor.getValue();
        assertThat(executed.getSkip()).isEqualTo(10L);
        assertThat(executed.getLimit()).isEqualTo(10);
        assertThat(executed.getSortObject().getInteger("capturedAt")).isEqualTo(1);
        assertThat(executed.getQueryObject().getString("playerId")).isEqualTo(PLAYER_ID);
        assertThat(executed.getQueryObject()).containsKey("capturedAt");
    }
}
