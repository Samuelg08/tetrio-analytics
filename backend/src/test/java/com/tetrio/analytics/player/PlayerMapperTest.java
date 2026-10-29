package com.tetrio.analytics.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraRecordSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.dto.PlayerRecordsResponse.ModeRecordDto;
import com.tetrio.analytics.player.dto.PlayerStatisticsResponse;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.RecordDocument;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerMapperTest {

    private PlayerMapper mapper;
    private static final Instant FIXED_NOW = Instant.parse("2026-10-20T18:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new PlayerMapper();
    }

    @Test
    void mapsRankedPlayerPreservingMetricsAndScale() {
        TetraUserData user = new TetraUserData(
                "5e9d4eec530bc13f9fdcd2c0",
                "5han",
                "jp",
                68967953.6,
                10482448.4
        );
        TetraLeagueSummaryData league = new TetraLeagueSummaryData(
                1711,
                1471,
                24863.06,
                4662.16,
                99.99,
                99.9993,
                "x+",
                "x+",
                170.6,
                2.73,
                353.52,
                1,
                0.0,
                false
        );

        PlayerProfileResponse profile = mapper.toPlayerProfile(user, league, FIXED_NOW, DataSource.LIVE);

        assertThat(profile.playerId()).isEqualTo("5e9d4eec530bc13f9fdcd2c0");
        assertThat(profile.username()).isEqualTo("5han");
        assertThat(profile.country()).isEqualTo("JP");
        assertThat(profile.lastSyncedAt()).isEqualTo(FIXED_NOW);
        assertThat(profile.dataSource()).isEqualTo(DataSource.LIVE);

        LeagueSummaryDto l = profile.league();
        assertThat(l).isNotNull();
        assertThat(l.gamesPlayed()).isEqualTo(1711);
        assertThat(l.gamesWon()).isEqualTo(1471);
        assertThat(l.ranked()).isTrue();
        assertThat(l.tr()).isEqualTo(24863.06);
        assertThat(l.glicko()).isEqualTo(4662.16);
        assertThat(l.rd()).isEqualTo(99.99);
        assertThat(l.gxe()).isEqualTo(99.9993);
        assertThat(l.rank()).isEqualTo("x+");
        assertThat(l.bestRank()).isEqualTo("x+");
        assertThat(l.apm()).isEqualTo(170.6);
        assertThat(l.pps()).isEqualTo(2.73);
        assertThat(l.vs()).isEqualTo(353.52);
        assertThat(l.standing()).isEqualTo(1);
        assertThat(l.percentile()).isEqualTo(0.0);
        assertThat(l.decaying()).isFalse();
    }

    @Test
    void mapsUnrankedRankZToNullRankAndFalseRankedWhileKeepingBestRank() {
        TetraUserData user = new TetraUserData(
                "5e32fc85ab319c2ab1beb07c",
                "osk",
                null,
                1000.0,
                500.0
        );
        TetraLeagueSummaryData league = new TetraLeagueSummaryData(
                14,
                8,
                9499.13,
                1621.84,
                350.0,
                60.9097,
                "z",
                "a-",
                35.59,
                1.44,
                75.57,
                -1,
                0.4881,
                true
        );

        PlayerProfileResponse profile = mapper.toPlayerProfile(user, league, FIXED_NOW, DataSource.LIVE);

        assertThat(profile.country()).isNull();
        LeagueSummaryDto l = profile.league();
        assertThat(l.ranked()).isFalse();
        assertThat(l.rank()).isNull();
        assertThat(l.bestRank()).isEqualTo("a-");
        assertThat(l.standing()).isNull();
        assertThat(l.decaying()).isTrue();
    }

    @Test
    void mapsNegativeOneSentinelsToNullNeverZero() {
        TetraUserData user = new TetraUserData(
                "0123456789abcdef01234567",
                "newplayer",
                "",
                0.0,
                0.0
        );
        TetraLeagueSummaryData league = new TetraLeagueSummaryData(
                0,
                0,
                -1.0,
                -1.0,
                -1.0,
                -1.0,
                "z",
                "z",
                null,
                null,
                null,
                -1,
                -1.0,
                false
        );

        PlayerProfileResponse profile = mapper.toPlayerProfile(user, league, FIXED_NOW, DataSource.LIVE);

        LeagueSummaryDto l = profile.league();
        assertThat(l.gamesPlayed()).isZero();
        assertThat(l.gamesWon()).isZero();
        assertThat(l.ranked()).isFalse();
        assertThat(l.tr()).isNull();
        assertThat(l.glicko()).isNull();
        assertThat(l.rd()).isNull();
        assertThat(l.gxe()).isNull();
        assertThat(l.rank()).isNull();
        assertThat(l.bestRank()).isNull();
        assertThat(l.apm()).isNull();
        assertThat(l.pps()).isNull();
        assertThat(l.vs()).isNull();
        assertThat(l.standing()).isNull();
        assertThat(l.percentile()).isNull();
    }

    @Test
    void rejectsInvalidMongoIdInUpstreamUserData() {
        TetraUserData corrupted = new TetraUserData("invalid-id", "5han", "JP", 0.0, 0.0);

        assertThatThrownBy(() -> mapper.toPlayerProfile(corrupted, null, FIXED_NOW, DataSource.LIVE))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).errorCode())
                        .isEqualTo(ErrorCode.UPSTREAM_BAD_RESPONSE));
    }

    @Test
    void mapsToAndFromPlayerDocumentAndSnapshotDocumentDeterministically() {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        TetraUserData user = new TetraUserData(playerId, " 5HAN ", "jp", 68967953.6, 10482448.4);
        TetraLeagueSummaryData rawLeague = new TetraLeagueSummaryData(
                1711, 1471, 24863.06, 4662.16, 99.99, 99.9993,
                "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0, false
        );
        LeagueSummaryDto league = mapper.toLeagueSummary(rawLeague);
        EffectiveCacheWindow window = new EffectiveCacheWindow(
                1_700_000_000_000L,
                1_700_000_600_000L,
                playerId + ":1700000000000"
        );
        TetraChannelCache userCache = new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_600_000L);
        TetraChannelCache leagueCache = new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_600_000L);

        PlayerDocument playerDoc = mapper.toPlayerDocument(user, league, window, FIXED_NOW);
        SnapshotDocument snapshotDoc = mapper.toSnapshotDocument(
                user, league, window, userCache, leagueCache, FIXED_NOW, "live-session-1"
        );

        assertThat(playerDoc.externalPlayerId()).isEqualTo(playerId);
        assertThat(playerDoc.usernameLower()).isEqualTo("5han");
        assertThat(playerDoc.country()).isEqualTo("JP");
        assertThat(playerDoc.activeCacheWindowKey()).isEqualTo(playerId + ":1700000000000");

        assertThat(snapshotDoc.playerId()).isEqualTo(playerId);
        assertThat(snapshotDoc.cacheWindowKey()).isEqualTo(playerId + ":1700000000000");
        assertThat(snapshotDoc.rating()).isEqualTo(24863.06);
        assertThat(snapshotDoc.source().sessionId()).isEqualTo("live-session-1");

        PlayerProfileResponse fromCache = mapper.fromPlayerDocument(playerDoc, DataSource.CACHE);
        assertThat(fromCache.playerId()).isEqualTo(playerId);
        assertThat(fromCache.username()).isEqualTo("5han");
        assertThat(fromCache.dataSource()).isEqualTo(DataSource.CACHE);
        assertThat(fromCache.league()).isEqualTo(league);
    }

    @Test
    void computesDerivedStatisticsAndPropagatesNullWhenDenominatorIsZeroOrMissing() {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        PlayerDocument activeDoc = new PlayerDocument(
                "doc-1",
                playerId,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(50000.0, 7200.0),
                new LeagueSummaryDto(100, 75, true, 24000.0, 2200.0, 65.0, 99.0, "x", "x", 150.0, 2.5, 300.0, 10, 0.01, false),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW,
                "k1",
                1
        );

        PlayerStatisticsResponse stats = mapper.toPlayerStatistics(activeDoc);
        assertThat(stats.derived().gamesLost()).isEqualTo(25);
        assertThat(stats.derived().winRate()).isEqualTo(0.75);
        assertThat(stats.derived().attackPerPiece()).isEqualTo(1.0);
        assertThat(stats.derived().vsPerApm()).isEqualTo(2.0);
        assertThat(stats.derived().gametimeHours()).isEqualTo(2.0);
        assertThat(stats.definitions()).hasSize(5);

        PlayerDocument zeroDoc = new PlayerDocument(
                "doc-2",
                playerId,
                "newuser",
                "newuser",
                null,
                new PlayerDocument.ProfileMetadata(0.0, null),
                new LeagueSummaryDto(0, 0, false, null, null, null, null, null, null, 0.0, 0.0, 0.0, null, null, false),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW,
                "k2",
                1
        );

        PlayerStatisticsResponse zeroStats = mapper.toPlayerStatistics(zeroDoc);
        assertThat(zeroStats.derived().gamesLost()).isZero();
        assertThat(zeroStats.derived().winRate()).isNull();
        assertThat(zeroStats.derived().attackPerPiece()).isNull();
        assertThat(zeroStats.derived().vsPerApm()).isNull();
        assertThat(zeroStats.derived().gametimeHours()).isNull();
    }

    @Test
    void mapsRecordSummaryDataWithNegativeOneRankToNullAndCalculatesKpp() {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        TetraRecordSummaryData summary40l = new TetraRecordSummaryData(
                new TetraRecordSummaryData.TetraRecordEntry(
                        "6870bcd3ee681267d377b740",
                        "replay-40l",
                        "40l",
                        "2025-07-11T07:27:15.930Z",
                        new TetraRecordSummaryData.TetraRecordResults(
                                new TetraRecordSummaryData.TetraRecordAggregateStats(0.0, 6.2668, 0.0),
                                new TetraRecordSummaryData.TetraRecordStats(
                                        16130.0,
                                        13668L,
                                        40,
                                        1,
                                        303,
                                        101,
                                        0,
                                        new TetraRecordSummaryData.TetraRecordFinesse(10, 6, 95)
                                )
                        )
                ),
                36,
                -1
        );

        RecordDocument doc40l = mapper.toRecordDocument(
                playerId,
                "40l",
                summary40l,
                new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_300_000L),
                FIXED_NOW,
                FIXED_NOW.plusSeconds(300),
                "sess-1"
        );

        ModeRecordDto dto = mapper.toModeRecordDto(doc40l);
        assertThat(dto).isNotNull();
        assertThat(dto.mode()).isEqualTo("40l");
        assertThat(dto.primaryMetric()).isEqualTo("finalTimeMs");
        assertThat(dto.primaryValue()).isEqualTo(16130.0);
        assertThat(dto.kpp()).isEqualTo(3.0);
        assertThat(dto.globalRank()).isEqualTo(36);
        assertThat(dto.countryRank()).isNull();
        assertThat(dto.finesseFaults()).isEqualTo(6);
    }
}
