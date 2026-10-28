package com.tetrio.analytics.integration.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModelCardResponse(
        String modelName,
        String modelVersion,
        String datasetVersion,
        String featureSchemaVersion,
        String trainedAt,
        Map<String, Double> metrics,
        String status
) {}
