package com.tetrio.analytics.player.dto;

import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse.AiFeatureContributionDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TrEstimatePublicResponse(
        String status,
        String playerId,
        Double estimatedTr,
        Double observedTr,
        String modelName,
        String modelVersion,
        String featureSchemaVersion,
        Map<String, Object> featuresUsed,
        List<AiFeatureContributionDto> explanation,
        String explanationMethod,
        String disclaimer,
        Instant generatedAt
) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_MODEL_UNAVAILABLE = "model_unavailable";
    public static final String FEATURE_SCHEMA_V1 = "tr-cross-v1";
    public static final String DEFAULT_DISCLAIMER =
            "Estimación estadística transversal; no es un pronóstico ni implica causalidad.";
}
