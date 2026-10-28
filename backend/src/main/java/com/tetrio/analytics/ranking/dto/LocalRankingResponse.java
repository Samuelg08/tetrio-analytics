package com.tetrio.analytics.ranking.dto;

import java.util.List;

public record LocalRankingResponse(
        String scope,
        String metric,
        RankingUniverseDto universe,
        List<RankingItemDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static final String LOCAL_SCOPE = "local";
}
