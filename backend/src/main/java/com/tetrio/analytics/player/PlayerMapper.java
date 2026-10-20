package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PlayerMapper {

    private static final Pattern MONGO_ID_PATTERN = Pattern.compile("^[0-9a-f]{24}$");

    public PlayerProfileResponse toPlayerProfile(
            TetraUserData userData,
            TetraLeagueSummaryData leagueData,
            Instant lastSyncedAt,
            DataSource dataSource
    ) {
        if (userData == null
                || userData.id() == null
                || !MONGO_ID_PATTERN.matcher(userData.id()).matches()
                || userData.username() == null
                || userData.username().isBlank()) {
            throw new ApiProblemException(
                    ErrorCode.UPSTREAM_BAD_RESPONSE,
                    "TETRA CHANNEL devolvió datos de perfil incompletos o con identificador inválido."
            );
        }

        String normalizedCountry = (userData.country() == null || userData.country().isBlank())
                ? null
                : userData.country().trim().toUpperCase(Locale.ROOT);

        return new PlayerProfileResponse(
                userData.id(),
                userData.username().trim().toLowerCase(Locale.ROOT),
                normalizedCountry,
                lastSyncedAt,
                dataSource,
                toLeagueSummary(leagueData)
        );
    }

    public LeagueSummaryDto toLeagueSummary(TetraLeagueSummaryData raw) {
        if (raw == null) {
            return null;
        }

        int gamesPlayed = (raw.gamesPlayed() != null && raw.gamesPlayed() >= 0) ? raw.gamesPlayed() : 0;
        int gamesWon = (raw.gamesWon() != null && raw.gamesWon() >= 0) ? raw.gamesWon() : 0;

        String normalizedRank = normalizeRank(raw.rank());
        String normalizedBestRank = normalizeRank(raw.bestRank());
        boolean ranked = normalizedRank != null;

        return new LeagueSummaryDto(
                gamesPlayed,
                gamesWon,
                ranked,
                normalizeNonNegativeDouble(raw.tr()),
                normalizeNonNegativeDouble(raw.glicko()),
                normalizeNonNegativeDouble(raw.rd()),
                normalizeNonNegativeDouble(raw.gxe()),
                normalizedRank,
                normalizedBestRank,
                normalizeNonNegativeDouble(raw.apm()),
                normalizeNonNegativeDouble(raw.pps()),
                normalizeNonNegativeDouble(raw.vs()),
                normalizePositiveInteger(raw.standing()),
                normalizeNonNegativeDouble(raw.percentile()),
                Boolean.TRUE.equals(raw.decaying())
        );
    }

    private static String normalizeRank(String rawRank) {
        if (rawRank == null) {
            return null;
        }
        String trimmed = rawRank.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || "z".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static Double normalizeNonNegativeDouble(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0d) {
            return null;
        }
        return value;
    }

    private static Integer normalizePositiveInteger(Integer value) {
        if (value == null || value <= 0) {
            return null;
        }
        return value;
    }
}
