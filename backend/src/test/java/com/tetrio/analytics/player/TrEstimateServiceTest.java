package com.tetrio.analytics.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tetrio.analytics.integration.aiservice.AiServiceClient;
import com.tetrio.analytics.integration.aiservice.dto.AiModelCardResponse;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.TrEstimatePublicResponse;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrEstimateServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-28T20:00:00Z");
    private static final String PLAYER_ID = "5e9d4eec530bc13f9fdcd2c0";

    private PlayerPersistenceService playerPersistenceService;
    private AiServiceClient aiServiceClient;
    private TrEstimateService trEstimateService;

    @BeforeEach
    void setUp() {
        playerPersistenceService = mock(PlayerPersistenceService.class);
        aiServiceClient = mock(AiServiceClient.class);
        trEstimateService = new TrEstimateService(
                playerPersistenceService,
                aiServiceClient,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void estimatesTrAndPersistsPredictionAndModelVersion() {
        PlayerDocument player = new PlayerDocument(
                null,
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(50000.0, 12000.0),
                new LeagueSummaryDto(
                        1711, 1471, true, 24863.06, 4662.13, 62.58, 99.99,
                        "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0001, false
                ),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(300),
                PLAYER_ID + ":1",
                1
        );
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_ID))
                .thenReturn(Optional.of(player));

        AiTrEstimateResponse aiResp = new AiTrEstimateResponse(
                "ok",
                new AiTrEstimateResponse.AiTrPredictionDto(24461.42),
                "tr-cross-ridge-baseline",
                "0.2.0",
                "tr-cross-v1",
                List.of(new AiTrEstimateResponse.AiFeatureContributionDto("vs", 1159.11)),
                "shap"
        );
        when(aiServiceClient.estimateTr(any()))
                .thenReturn(Optional.of(aiResp));

        AiModelCardResponse card = new AiModelCardResponse(
                "tr-cross-ridge-baseline",
                "0.2.0",
                "20261020-sample500",
                "tr-cross-v1",
                "2026-10-28T19:00:00Z",
                Map.of("mae", 185.121, "rmse", 225.0113, "r2", 0.8757),
                "ready"
        );
        when(aiServiceClient.fetchModelCard(anyString()))
                .thenReturn(Optional.of(card));

        TrEstimatePublicResponse result = trEstimateService.estimateTrForPlayer(PLAYER_ID);

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.estimatedTr()).isEqualTo(24461.42);
        assertThat(result.observedTr()).isEqualTo(24863.06);
        assertThat(result.modelName()).isEqualTo("tr-cross-ridge-baseline");
        assertThat(result.explanationMethod()).isEqualTo("shap");

        verify(playerPersistenceService, times(1)).savePrediction(any());
        verify(playerPersistenceService, times(1)).upsertModelVersion(any());
    }

    @Test
    void returnsModelUnavailableWhenAiServiceHasNoModelLoaded() {
        PlayerDocument player = new PlayerDocument(
                null,
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                null,
                new LeagueSummaryDto(
                        1711, 1471, true, 24863.06, 4662.13, 62.58, 99.99,
                        "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0001, false
                ),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(300),
                PLAYER_ID + ":1",
                1
        );
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_ID))
                .thenReturn(Optional.of(player));
        when(aiServiceClient.estimateTr(any()))
                .thenReturn(Optional.empty());

        TrEstimatePublicResponse result = trEstimateService.estimateTrForPlayer(PLAYER_ID);

        assertThat(result.status()).isEqualTo("model_unavailable");
        assertThat(result.estimatedTr()).isNull();
        assertThat(result.observedTr()).isEqualTo(24863.06);
        assertThat(result.explanation()).isEmpty();
    }

    @Test
    void throwsPredictionNotApplicableWhenRequiredFeatureIsNull() {
        PlayerDocument unranked = new PlayerDocument(
                null,
                PLAYER_ID,
                "unranked",
                "unranked",
                "US",
                null,
                new LeagueSummaryDto(
                        3, 1, false, null, null, null, null,
                        null, null, null, null, null, null, null, false
                ),
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW.plusSeconds(300),
                PLAYER_ID + ":1",
                1
        );
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_ID))
                .thenReturn(Optional.of(unranked));

        assertThatThrownBy(() -> trEstimateService.estimateTrForPlayer(PLAYER_ID))
                .isInstanceOf(ApiProblemException.class)
                .extracting(ex -> ((ApiProblemException) ex).errorCode())
                .isEqualTo(ErrorCode.PREDICTION_NOT_APPLICABLE);
    }
}
