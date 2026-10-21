package com.tetrio.analytics.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.dto.SnapshotItemDto;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlayerServiceTest {

    private static final String PLAYER_ID = "5e9d4eec530bc13f9fdcd2c0";
    private static final Instant FIXED_NOW = Instant.ofEpochMilli(1_700_000_100_000L);

    private TetraChannelClient tetraChannelClient;
    private PlayerPersistenceService persistenceService;
    private PlayerService playerService;

    @BeforeEach
    void setUp() {
        tetraChannelClient = mock(TetraChannelClient.class);
        persistenceService = mock(PlayerPersistenceService.class);
        PlayerMapper mapper = new PlayerMapper();
        CacheWindowCalculator windowCalculator = new CacheWindowCalculator(300L);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        playerService = new PlayerService(
                tetraChannelClient,
                mapper,
                persistenceService,
                windowCalculator,
                fixedClock
        );
    }

    @Test
    void returnsCachedProfileWithoutUpstreamCallWhenCacheValidUntilIsInFuture() {
        PlayerDocument cachedDoc = samplePlayerDocument(
                FIXED_NOW.minusSeconds(60),
                FIXED_NOW.plusSeconds(240)
        );
        when(persistenceService.findByUsernameLower("5han")).thenReturn(Optional.of(cachedDoc));

        PlayerProfileResponse response = playerService.getByUsername(" 5HAN ");

        assertThat(response.playerId()).isEqualTo(PLAYER_ID);
        assertThat(response.username()).isEqualTo("5han");
        assertThat(response.dataSource()).isEqualTo(DataSource.CACHE);
        assertThat(response.lastSyncedAt()).isEqualTo(cachedDoc.lastSyncedAt());
        verify(tetraChannelClient, never()).fetchUser(anyString(), anyString());
        verify(tetraChannelClient, never()).fetchLeagueSummary(anyString(), anyString());
    }

    @Test
    void syncsLiveAndPersistsPlayerAndSnapshotWhenCacheExpired() {
        PlayerDocument expiredDoc = samplePlayerDocument(
                FIXED_NOW.minusSeconds(400),
                FIXED_NOW.minusSeconds(100)
        );
        when(persistenceService.findByExternalPlayerId(PLAYER_ID)).thenReturn(Optional.of(expiredDoc));

        TetraUserData user = new TetraUserData(PLAYER_ID, "5han", "JP", 68967953.6, 10482448.4);
        TetraLeagueSummaryData league = new TetraLeagueSummaryData(
                1711, 1471, 24863.06, 4662.16, 99.99, 99.9993,
                "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0, false
        );
        TetraChannelCache cache = new TetraChannelCache(
                "miss",
                1_700_000_100_000L,
                1_700_000_700_000L
        );

        when(tetraChannelClient.fetchUser(eq(PLAYER_ID), anyString()))
                .thenReturn(new TetraChannelResponse<>(user, cache));
        when(tetraChannelClient.fetchLeagueSummary(eq(PLAYER_ID), anyString()))
                .thenReturn(new TetraChannelResponse<>(league, cache));

        PlayerProfileResponse response = playerService.getByPlayerId(PLAYER_ID.toUpperCase());

        assertThat(response.playerId()).isEqualTo(PLAYER_ID);
        assertThat(response.dataSource()).isEqualTo(DataSource.LIVE);
        assertThat(response.lastSyncedAt()).isEqualTo(FIXED_NOW);

        ArgumentCaptor<PlayerDocument> playerCaptor = ArgumentCaptor.forClass(PlayerDocument.class);
        ArgumentCaptor<SnapshotDocument> snapshotCaptor = ArgumentCaptor.forClass(SnapshotDocument.class);
        verify(persistenceService).saveSyncedPlayerAndSnapshot(playerCaptor.capture(), snapshotCaptor.capture());

        assertThat(playerCaptor.getValue().activeCacheWindowKey())
                .isEqualTo(PLAYER_ID + ":1700000100000");
        assertThat(snapshotCaptor.getValue().cacheWindowKey())
                .isEqualTo(PLAYER_ID + ":1700000100000");
    }

    @Test
    void returnsStaleCopyWhenCacheExpiredAndUpstreamFailsOrRateLimits() {
        PlayerDocument expiredDoc = samplePlayerDocument(
                FIXED_NOW.minusSeconds(600),
                FIXED_NOW.minusSeconds(300)
        );
        when(persistenceService.findByUsernameLower("5han")).thenReturn(Optional.of(expiredDoc));
        when(tetraChannelClient.fetchUser(eq("5han"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.RATE_LIMITED,
                        "Presupuesto agotado",
                        5L
                ));

        PlayerProfileResponse response = playerService.getByUsername("5han");

        assertThat(response.playerId()).isEqualTo(PLAYER_ID);
        assertThat(response.dataSource()).isEqualTo(DataSource.STALE);
        assertThat(response.lastSyncedAt()).isEqualTo(expiredDoc.lastSyncedAt());
        verify(persistenceService, never()).saveSyncedPlayerAndSnapshot(any(), any());
    }

    @Test
    void rethrowsUpstreamErrorWhenNoLocalCopyExistsForStaleFallback() {
        when(persistenceService.findByUsernameLower("5han")).thenReturn(Optional.empty());
        when(tetraChannelClient.fetchUser(eq("5han"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "TETRA CHANNEL no disponible",
                        10L
                ));

        assertThatThrownBy(() -> playerService.getByUsername("5han"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE));
    }

    @Test
    void validatesPlayerIdFormat() {
        assertThatThrownBy(() -> playerService.getByPlayerId("not-24-hex"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException problem = (ApiProblemException) ex;
                    assertThat(problem.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(problem.errors()).hasSize(1);
                    assertThat(problem.errors().getFirst().field()).isEqualTo("playerId");
                });
    }

    @Test
    void getsSnapshotsAndValidatesRangeAndPaginationParameters() {
        PlayerDocument existingDoc = samplePlayerDocument(FIXED_NOW.minusSeconds(60), FIXED_NOW.plusSeconds(240));
        when(persistenceService.findByExternalPlayerId(PLAYER_ID)).thenReturn(Optional.of(existingDoc));

        SnapshotDocument snap = new SnapshotDocument(
                "s1", PLAYER_ID, PLAYER_ID + ":1", FIXED_NOW, FIXED_NOW.plusSeconds(300), FIXED_NOW,
                24863.06, 4662.16, 99.99, "x+",
                new SnapshotDocument.SnapshotMetrics(170.6, 2.73, 353.52, 99.9993, 1711, 1471, 1, 0.0, "x+", true, false),
                null, SnapshotDocument.CURRENT_SCHEMA_VERSION
        );
        when(persistenceService.findSnapshotsByPlayerId(PLAYER_ID, null, null, 0, 20))
                .thenReturn(PageResponse.of(List.of(snap), 0, 20, 1L));

        PageResponse<SnapshotItemDto> page = playerService.getSnapshots(PLAYER_ID, null, null, 0, 20);
        assertThat(page.totalItems()).isEqualTo(1L);
        assertThat(page.items().getFirst().tr()).isEqualTo(24863.06);

        assertThatThrownBy(() -> playerService.getSnapshots(PLAYER_ID, "not-iso", null, 0, 20))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errors().getFirst().field()).isEqualTo("from"));

        assertThatThrownBy(() -> playerService.getSnapshots(PLAYER_ID, null, null, -1, 20))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errors().getFirst().field()).isEqualTo("page"));
    }

    private static PlayerDocument samplePlayerDocument(Instant syncedAt, Instant validUntil) {
        LeagueSummaryDto league = new LeagueSummaryDto(
                1711, 1471, true, 24863.06, 4662.16, 99.99, 99.9993,
                "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0, false
        );
        return new PlayerDocument(
                "doc-1",
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(68967953.6, 10482448.4),
                league,
                syncedAt,
                syncedAt,
                validUntil,
                PLAYER_ID + ":" + syncedAt.toEpochMilli(),
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );
    }
}
