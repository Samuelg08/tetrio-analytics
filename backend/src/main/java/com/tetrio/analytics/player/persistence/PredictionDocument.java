package com.tetrio.analytics.player.persistence;

import com.tetrio.analytics.integration.aiservice.dto.AiTrEstimateResponse.AiFeatureContributionDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "predictions")
public record PredictionDocument(
        @Id String id,
        String playerId,
        String task,
        Map<String, Object> features,
        String featureSchemaVersion,
        Map<String, Object> prediction,
        String modelName,
        String modelVersion,
        Instant createdAt,
        Map<String, Object> evaluationContext,
        List<AiFeatureContributionDto> explanation
) {
    public static final String TASK_TR_CROSS_REGRESSION = "tr_cross_regression";
}
