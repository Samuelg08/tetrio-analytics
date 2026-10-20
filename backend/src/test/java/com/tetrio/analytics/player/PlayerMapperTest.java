package com.tetrio.analytics.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
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
}
