package com.tetrio.analytics.integration.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTrEstimateResponse(
        String status,
        AiTrPredictionDto prediction,
        String modelName,
        String modelVersion,
        String featureSchemaVersion,
        List<AiFeatureContributionDto> explanation,
        String explanationMethod
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiTrPredictionDto(
            Double tr
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiFeatureContributionDto(
            String feature,
            Double contribution
    ) {}
}
