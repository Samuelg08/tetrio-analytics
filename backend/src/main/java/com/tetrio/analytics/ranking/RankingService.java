package com.tetrio.analytics.ranking;

import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService.LocalRankingQueryResult;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.ranking.dto.LocalRankingResponse;
import com.tetrio.analytics.ranking.dto.RankingItemDto;
import com.tetrio.analytics.ranking.dto.RankingMetric;
import com.tetrio.analytics.ranking.dto.RankingUniverseDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RankingService {

    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^[A-Z]{2}$");

    private final PlayerPersistenceService playerPersistenceService;

    public RankingService(PlayerPersistenceService playerPersistenceService) {
        this.playerPersistenceService = playerPersistenceService;
    }

    public LocalRankingResponse getLocalRanking(
            String rawMetric,
            String rawCountry,
            int page,
            int size
    ) {
        RankingMetric metric = RankingMetric.fromParameter(rawMetric);
        String normalizedCountry = normalizeAndValidateCountry(rawCountry);

        if (page < 0) {
            throw ApiProblemException.validationFailed("page", "El parámetro 'page' debe ser mayor o igual a 0");
        }
        if (size < 1 || size > 100) {
            throw ApiProblemException.validationFailed("size", "El parámetro 'size' debe estar entre 1 y 100");
        }

        LocalRankingQueryResult result = playerPersistenceService.queryLocalRanking(
                metric,
                normalizedCountry,
                page,
                size
        );

        List<PlayerDocument> docs = result.page().items();
        List<RankingItemDto> items = new ArrayList<>(docs.size());
        int baseOffset = page * size;
        for (int i = 0; i < docs.size(); i++) {
            PlayerDocument doc = docs.get(i);
            items.add(new RankingItemDto(
                    baseOffset + i + 1,
                    doc.externalPlayerId(),
                    doc.username(),
                    doc.country(),
                    metric.extractValue(doc.league()),
                    doc.lastSyncedAt()
            ));
        }

        RankingUniverseDto universe = new RankingUniverseDto(
                RankingUniverseDto.LOCAL_UNIVERSE_DESCRIPTION,
                result.playersIncluded(),
                result.playersExcludedNullMetric(),
                result.dataAsOf()
        );

        return new LocalRankingResponse(
                LocalRankingResponse.LOCAL_SCOPE,
                metric.wireName(),
                universe,
                items,
                result.page().page(),
                result.page().size(),
                result.page().totalItems(),
                result.page().totalPages()
        );
    }

    private static String normalizeAndValidateCountry(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return null;
        }
        String normalized = rawCountry.trim().toUpperCase(Locale.ROOT);
        if (!COUNTRY_PATTERN.matcher(normalized).matches()) {
            throw ApiProblemException.validationFailed(
                    "country",
                    "El parámetro 'country' debe ser un código ISO 3166-1 alfa-2 de 2 letras"
            );
        }
        return normalized;
    }
}
