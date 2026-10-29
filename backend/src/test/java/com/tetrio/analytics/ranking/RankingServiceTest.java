package com.tetrio.analytics.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService.LocalRankingQueryResult;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import com.tetrio.analytics.ranking.dto.LocalRankingResponse;
import com.tetrio.analytics.ranking.dto.RankingMetric;
import com.tetrio.analytics.ranking.dto.RankingUniverseDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankingServiceTest {

    private PlayerPersistenceService playerPersistenceService;
    private RankingService rankingService;

    @BeforeEach
    void setUp() {
        playerPersistenceService = mock(PlayerPersistenceService.class);
        rankingService = new RankingService(playerPersistenceService);
    }

    @Test
    void computesOneBasedPositionsAndUniverseMetadataAcrossPages() {
        Instant syncedAt = Instant.parse("2026-10-20T12:00:00Z");
        PlayerDocument p3 = new PlayerDocument(
                "doc-3",
                "000000000000000000000003",
                "player3",
                "player3",
                "JP",
                null,
                new LeagueSummaryDto(100, 60, true, 24100.0, 2100.0, 65.0, 99.1, "x", "x", 155.4, 2.6, 320.0, 3, 0.01, false),
                syncedAt,
                syncedAt,
                syncedAt,
                "k3",
                1
        );
        PlayerDocument p4 = new PlayerDocument(
                "doc-4",
                "000000000000000000000004",
                "player4",
                "player4",
                "JP",
                null,
                new LeagueSummaryDto(90, 50, true, 23900.0, 2050.0, 65.0, 98.8, "u", "x", 142.1, 2.4, 305.0, 4, 0.02, false),
                syncedAt,
                syncedAt,
                syncedAt,
                "k4",
                1
        );

        when(playerPersistenceService.queryLocalRanking(RankingMetric.APM, "JP", 1, 2))
                .thenReturn(new LocalRankingQueryResult(
                        PageResponse.of(List.of(p3, p4), 1, 2, 10L),
                        10L,
                        2L,
                        syncedAt
                ));

        LocalRankingResponse response = rankingService.getLocalRanking(" APM ", " jp ", 1, 2);

        assertThat(response.scope()).isEqualTo("local");
        assertThat(response.metric()).isEqualTo("apm");
        assertThat(response.universe().description()).isEqualTo(RankingUniverseDto.LOCAL_UNIVERSE_DESCRIPTION);
        assertThat(response.universe().playersIncluded()).isEqualTo(10L);
        assertThat(response.universe().playersExcludedNullMetric()).isEqualTo(2L);
        assertThat(response.universe().dataAsOf()).isEqualTo(syncedAt);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(10L);
        assertThat(response.totalPages()).isEqualTo(5);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).position()).isEqualTo(3);
        assertThat(response.items().get(0).value()).isEqualTo(155.4);
        assertThat(response.items().get(1).position()).isEqualTo(4);
        assertThat(response.items().get(1).value()).isEqualTo(142.1);
    }

    @Test
    void rejectsInvalidMetricCountryAndPaginationParameters() {
        assertThatThrownBy(() -> rankingService.getLocalRanking(null, null, 0, 20))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("metric");
                });

        assertThatThrownBy(() -> rankingService.getLocalRanking("glicko", null, 0, 20))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("metric");
                });

        assertThatThrownBy(() -> rankingService.getLocalRanking("tr", "ESP", 0, 20))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("country");
                });

        assertThatThrownBy(() -> rankingService.getLocalRanking("tr", null, -1, 20))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("page");
                });

        assertThatThrownBy(() -> rankingService.getLocalRanking("tr", null, 0, 101))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("size");
                });
    }
}
