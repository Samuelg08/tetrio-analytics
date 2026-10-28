package com.tetrio.analytics.ranking.dto;

import java.time.Instant;

public record RankingUniverseDto(
        String description,
        long playersIncluded,
        long playersExcludedNullMetric,
        Instant dataAsOf
) {
    public static final String LOCAL_UNIVERSE_DESCRIPTION =
            "Jugadores almacenados en la base de datos del proyecto. No es la clasificación global de TETR.IO.";
}
