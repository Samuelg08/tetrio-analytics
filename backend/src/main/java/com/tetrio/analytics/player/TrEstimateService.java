package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.aiservice.AiServiceClient;
import com.tetrio.analytics.integration.aiservice.dto.AiModelCardResponse;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateRequest;
import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.TrEstimatePublicResponse;
import com.tetrio.analytics.player.persistence.ModelVersionDocument;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.PredictionDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class TrEstimateService {

    private static final Logger log = LoggerFactory.getLogger(TrEstimateService.class);

    private final PlayerPersistenceService playerPersistenceService;
    private final AiServiceClient aiServiceClient;
    private final Clock clock;
    private final Set<String> registeredModelVersions = ConcurrentHashMap.newKeySet();

    public TrEstimateService(
            PlayerPersistenceService playerPersistenceService,
            AiServiceClient aiServiceClient,
            Clock clock
    ) {
        this.playerPersistenceService = playerPersistenceService;
        this.aiServiceClient = aiServiceClient;
        this.clock = clock;
    }

    public TrEstimatePublicResponse estimateTrForPlayer(String rawPlayerId) {
        String normalizedPlayerId = PlayerService.normalizeAndValidatePlayerId(rawPlayerId);
        PlayerDocument player = findByPlayerIdSafe(normalizedPlayerId);
        if (player == null) {
            throw new ApiProblemException(
                    ErrorCode.PLAYER_NOT_FOUND,
                    "El jugador solicitado no está almacenado en la base de datos local."
            );
        }

        LeagueSummaryDto league = player.league();
        if (league == null
                || league.apm() == null
                || league.pps() == null
                || league.vs() == null) {
            throw new ApiProblemException(
                    ErrorCode.PREDICTION_NOT_APPLICABLE,
                    "El jugador no dispone de todas las variables requeridas (apm, pps, vs, gamesPlayed) para estimar el TR; no se imputa 0."
            );
        }

        Map<String, Object> featuresUsed = new LinkedHashMap<>();
        featuresUsed.put("apm", league.apm());
        featuresUsed.put("pps", league.pps());
        featuresUsed.put("vs", league.vs());
        featuresUsed.put("gamesPlayed", league.gamesPlayed());

        String requestId = "tr-est-" + UUID.randomUUID();
        Optional<AiTrEstimateResponse> aiRespOpt = aiServiceClient.estimateTr(
                new AiTrEstimateRequest(
                        requestId,
                        TrEstimatePublicResponse.FEATURE_SCHEMA_V1,
                        featuresUsed
                )
        );

        Instant now = Instant.now(clock);
        if (aiRespOpt.isEmpty()) {
            return new TrEstimatePublicResponse(
                    TrEstimatePublicResponse.STATUS_MODEL_UNAVAILABLE,
                    normalizedPlayerId,
                    null,
                    league.tr(),
                    null,
                    null,
                    TrEstimatePublicResponse.FEATURE_SCHEMA_V1,
                    featuresUsed,
                    List.of(),
                    null,
                    TrEstimatePublicResponse.DEFAULT_DISCLAIMER,
                    now
            );
        }

        AiTrEstimateResponse aiResp = aiRespOpt.get();
        Double estimatedTr = aiResp.prediction() != null ? aiResp.prediction().tr() : null;
        List<AiTrEstimateResponse.AiFeatureContributionDto> explanation =
                aiResp.explanation() != null ? aiResp.explanation() : List.of();

        persistPredictionAndModelVersionSafe(
                normalizedPlayerId,
                featuresUsed,
                estimatedTr,
                league.tr(),
                aiResp,
                explanation,
                now,
                requestId
        );

        return new TrEstimatePublicResponse(
                TrEstimatePublicResponse.STATUS_OK,
                normalizedPlayerId,
                estimatedTr,
                league.tr(),
                aiResp.modelName(),
                aiResp.modelVersion(),
                aiResp.featureSchemaVersion(),
                featuresUsed,
                explanation,
                aiResp.explanationMethod(),
                TrEstimatePublicResponse.DEFAULT_DISCLAIMER,
                now
        );
    }

    private PlayerDocument findByPlayerIdSafe(String playerId) {
        try {
            Optional<PlayerDocument> found = playerPersistenceService.findByExternalPlayerId(playerId);
            return found != null ? found.orElse(null) : null;
        } catch (DataAccessException ex) {
            log.warn("No se pudo consultar MongoDB por playerId '{}': {}", playerId, ex.getMessage());
            return null;
        }
    }

    private void persistPredictionAndModelVersionSafe(
            String playerId,
            Map<String, Object> featuresUsed,
            Double estimatedTr,
            Double observedTr,
            AiTrEstimateResponse aiResp,
            List<AiTrEstimateResponse.AiFeatureContributionDto> explanation,
            Instant now,
            String requestId
    ) {
        try {
            Map<String, Object> evalCtx = new LinkedHashMap<>();
            if (observedTr != null) {
                evalCtx.put("observedTr", observedTr);
            }
            Map<String, Object> predMap = new LinkedHashMap<>();
            predMap.put("tr", estimatedTr);

            PredictionDocument predDoc = new PredictionDocument(
                    null,
                    playerId,
                    PredictionDocument.TASK_TR_CROSS_REGRESSION,
                    featuresUsed,
                    aiResp.featureSchemaVersion(),
                    predMap,
                    aiResp.modelName(),
                    aiResp.modelVersion(),
                    now,
                    evalCtx,
                    explanation
            );
            playerPersistenceService.savePrediction(predDoc);

            String modelKey = aiResp.modelName() + ":" + aiResp.modelVersion();
            if (registeredModelVersions.add(modelKey)) {
                Optional<AiModelCardResponse> cardOpt = aiServiceClient.fetchModelCard(requestId);
                if (cardOpt != null && cardOpt.isPresent()) {
                    AiModelCardResponse card = cardOpt.get();
                    Instant trainedAt = parseInstantOrFallback(card.trainedAt(), now);
                    ModelVersionDocument mvDoc = new ModelVersionDocument(
                            null,
                            card.modelName(),
                            card.modelVersion(),
                            trainedAt,
                            card.datasetVersion(),
                            card.featureSchemaVersion(),
                            card.metrics(),
                            "artifacts/model.joblib",
                            card.status()
                    );
                    playerPersistenceService.upsertModelVersion(mvDoc);
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo registrar predicción o versión de modelo en MongoDB: {}", ex.getMessage());
        }
    }

    private static Instant parseInstantOrFallback(String raw, Instant fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
