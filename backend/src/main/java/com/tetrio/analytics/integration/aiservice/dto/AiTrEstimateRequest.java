package com.tetrio.analytics.integration.aiservice.dto;

import java.util.Map;

public record AiTrEstimateRequest(
        String requestId,
        String featureSchemaVersion,
        Map<String, Object> features
) {}
