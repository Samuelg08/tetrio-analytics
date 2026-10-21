package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.dto.SnapshotItemDto;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
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
        validateUserData(userData);

        String normalizedCountry = normalizeCountry(userData.country());
        String normalizedUsername = userData.username().trim().toLowerCase(Locale.ROOT);

        return new PlayerProfileResponse(
                userData.id(),
                normalizedUsername,
                normalizedCountry,
                lastSyncedAt,
                dataSource,
                toLeagueSummary(leagueData)
        );
    }

    public PlayerProfileResponse fromPlayerDocument(PlayerDocument document, DataSource dataSource) {
        if (document == null) {
            throw new IllegalArgumentException("PlayerDocument no puede ser nulo.");
        }
        return new PlayerProfileResponse(
                document.externalPlayerId(),
                document.usernameLower(),
                document.country(),
                document.lastSyncedAt(),
                dataSource,
                document.league()
        );
    }

    public SnapshotItemDto toSnapshotItem(SnapshotDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("SnapshotDocument no puede ser nulo.");
        }
        SnapshotDocument.SnapshotMetrics metrics = doc.metrics();
        return new SnapshotItemDto(
                doc.capturedAt(),
                doc.rating(),
                doc.glicko(),
                doc.rd(),
                metrics != null ? metrics.gxe() : null,
                doc.rank(),
                metrics != null ? metrics.apm() : null,
                metrics != null ? metrics.pps() : null,
                metrics != null ? metrics.vs() : null,
                metrics != null ? metrics.gamesPlayed() : 0,
                metrics != null ? metrics.gamesWon() : 0,
                metrics != null ? metrics.standing() : null
        );
    }

    public PlayerDocument toPlayerDocument(
            TetraUserData userData,
            LeagueSummaryDto leagueSummary,
            EffectiveCacheWindow cacheWindow,
            Instant syncedAt
    ) {
        validateUserData(userData);
        String normalizedUsername = userData.username().trim().toLowerCase(Locale.ROOT);
        String normalizedCountry = normalizeCountry(userData.country());

        PlayerDocument.ProfileMetadata profileMetadata = new PlayerDocument.ProfileMetadata(
                normalizeNonNegativeDouble(userData.xp()),
                normalizeNonNegativeDouble(userData.gametime())
        );

        return new PlayerDocument(
                null,
                userData.id(),
                normalizedUsername,
                normalizedUsername,
                normalizedCountry,
                profileMetadata,
                leagueSummary,
                syncedAt,
                cacheWindow.windowStartAt(),
                cacheWindow.windowUntilAt(),
                cacheWindow.cacheWindowKey(),
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );
    }

    public SnapshotDocument toSnapshotDocument(
            TetraUserData userData,
            LeagueSummaryDto leagueSummary,
            EffectiveCacheWindow cacheWindow,
            TetraChannelCache userCache,
            TetraChannelCache leagueCache,
            Instant capturedAt,
            String sessionId
    ) {
        validateUserData(userData);

        SnapshotDocument.SnapshotMetrics metrics = leagueSummary == null
                ? null
                : new SnapshotDocument.SnapshotMetrics(
                        leagueSummary.apm(),
                        leagueSummary.pps(),
                        leagueSummary.vs(),
                        leagueSummary.gxe(),
                        leagueSummary.gamesPlayed(),
                        leagueSummary.gamesWon(),
                        leagueSummary.standing(),
                        leagueSummary.percentile(),
                        leagueSummary.bestRank(),
                        leagueSummary.ranked(),
                        leagueSummary.decaying()
                );

        SnapshotDocument.SnapshotSource source = new SnapshotDocument.SnapshotSource(
                userCache != null ? userCache.cachedAt() : null,
                userCache != null ? userCache.cachedUntil() : null,
                leagueCache != null ? leagueCache.cachedAt() : null,
                leagueCache != null ? leagueCache.cachedUntil() : null,
                sessionId
        );

        return new SnapshotDocument(
                null,
                userData.id(),
                cacheWindow.cacheWindowKey(),
                cacheWindow.windowStartAt(),
                cacheWindow.windowUntilAt(),
                capturedAt,
                leagueSummary != null ? leagueSummary.tr() : null,
                leagueSummary != null ? leagueSummary.glicko() : null,
                leagueSummary != null ? leagueSummary.rd() : null,
                leagueSummary != null ? leagueSummary.rank() : null,
                metrics,
                source,
                SnapshotDocument.CURRENT_SCHEMA_VERSION
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

    private static void validateUserData(TetraUserData userData) {
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
    }

    private static String normalizeCountry(String rawCountry) {
        if (rawCountry == null || rawCountry.isBlank()) {
            return null;
        }
        return rawCountry.trim().toUpperCase(Locale.ROOT);
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
