package com.tetrio.analytics.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tetrio.analytics.comparison.dto.ComparisonRequest;
import com.tetrio.analytics.comparison.dto.ComparisonResponse;
import com.tetrio.analytics.player.PlayerMapper;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComparisonServiceTest {

    private static final String PLAYER_A_ID = "5e9d4eec530bc13f9fdcd2c0";
    private static final String PLAYER_B_ID = "5eb2a07a0f991a70c58fb7cb";
    private static final Instant NOW = Instant.parse("2026-10-20T19:00:00Z");

    private PlayerPersistenceService playerPersistenceService;
    private ComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        playerPersistenceService = mock(PlayerPersistenceService.class);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        comparisonService = new ComparisonService(playerPersistenceService, new PlayerMapper(), fixedClock);
    }

    @Test
    void computesExactDifferencesAndPreservesNullsWhenMetricIsMissing() {
        PlayerDocument docA = new PlayerDocument(
                "doc-a",
                PLAYER_A_ID,
                "5han",
                "5han",
                "JP",
                null,
                new LeagueSummaryDto(1711, 1471, true, 24800.0, 2200.5, 65.0, null, "x+", "x+", 150.3, 2.73, 353.52, 1, 0.0, false),
                NOW,
                NOW,
                NOW,
                "ka",
                1
        );
        PlayerDocument docB = new PlayerDocument(
                "doc-b",
                PLAYER_B_ID,
                "bplayer",
                "bplayer",
                "US",
                null,
                new LeagueSummaryDto(800, 500, true, 23800.0, 2100.2, 65.0, 95.0, "x", "x", 120.1, null, 310.02, 50, 0.01, false),
                NOW,
                NOW,
                NOW,
                "kb",
                1
        );

        when(playerPersistenceService.findByExternalPlayerId(PLAYER_A_ID)).thenReturn(Optional.of(docA));
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_B_ID)).thenReturn(Optional.of(docB));

        ComparisonResponse response = comparisonService.comparePlayers(
                new ComparisonRequest(" " + PLAYER_A_ID.toUpperCase() + " ", PLAYER_B_ID)
        );

        assertThat(response.playerA().playerId()).isEqualTo(PLAYER_A_ID);
        assertThat(response.playerB().playerId()).isEqualTo(PLAYER_B_ID);
        assertThat(response.headToHead()).isNull();
        assertThat(response.generatedAt()).isEqualTo(NOW);

        assertThat(response.differences().tr()).isEqualTo(1000.0);
        assertThat(response.differences().glicko()).isEqualTo(100.3);
        assertThat(response.differences().gxe()).isNull();
        assertThat(response.differences().apm()).isEqualTo(30.2);
        assertThat(response.differences().pps()).isNull();
        assertThat(response.differences().vs()).isEqualTo(43.5);
    }

    @Test
    void rejectsIdenticalOrInvalidPlayerIdsWithValidationFailed() {
        assertThatThrownBy(() -> comparisonService.comparePlayers(
                new ComparisonRequest(PLAYER_A_ID.toUpperCase(), " " + PLAYER_A_ID + " ")
        ))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("playerBId");
                });

        assertThatThrownBy(() -> comparisonService.comparePlayers(
                new ComparisonRequest("not-a-mongo-id", null)
        ))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.errors()).extracting("field").containsExactly("playerAId", "playerBId");
                });
    }

    @Test
    void identifiesMissingPlayerIn404Detail() {
        PlayerDocument docA = new PlayerDocument(
                "doc-a", PLAYER_A_ID, "5han", "5han", "JP", null, null, NOW, NOW, NOW, "ka", 1
        );
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_A_ID)).thenReturn(Optional.of(docA));
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_B_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comparisonService.comparePlayers(new ComparisonRequest(PLAYER_A_ID, PLAYER_B_ID)))
                .isInstanceOfSatisfying(ApiProblemException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.PLAYER_NOT_FOUND);
                    assertThat(ex.getMessage()).contains("jugador B").contains(PLAYER_B_ID);
                });
    }
}
