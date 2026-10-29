package com.tetrio.analytics.ranking.dto;

import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.problem.ApiProblemException;
import java.util.Locale;

public enum RankingMetric {
    TR("tr", "league.tr", true),
    APM("apm", "league.apm", false),
    PPS("pps", "league.pps", false),
    VS("vs", "league.vs", false);

    private final String wireName;
    private final String mongoField;
    private final boolean requiresRanked;

    RankingMetric(String wireName, String mongoField, boolean requiresRanked) {
        this.wireName = wireName;
        this.mongoField = mongoField;
        this.requiresRanked = requiresRanked;
    }

    public String wireName() {
        return wireName;
    }

    public String mongoField() {
        return mongoField;
    }

    public boolean requiresRanked() {
        return requiresRanked;
    }

    public Double extractValue(LeagueSummaryDto league) {
        if (league == null) {
            return null;
        }
        return switch (this) {
            case TR -> league.tr();
            case APM -> league.apm();
            case PPS -> league.pps();
            case VS -> league.vs();
        };
    }

    public static RankingMetric fromParameter(String rawMetric) {
        if (rawMetric == null || rawMetric.isBlank()) {
            throw ApiProblemException.validationFailed(
                    "metric",
                    "El parámetro 'metric' es obligatorio y debe ser uno de: tr, apm, pps, vs"
            );
        }
        String normalized = rawMetric.trim().toLowerCase(Locale.ROOT);
        for (RankingMetric candidate : values()) {
            if (candidate.wireName.equals(normalized)) {
                return candidate;
            }
        }
        throw ApiProblemException.validationFailed(
                "metric",
                "El parámetro 'metric' debe ser uno de: tr, apm, pps, vs"
        );
    }
}
