package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraRecordSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.dto.PlayerRecordsResponse.ModeRecordDto;
import com.tetrio.analytics.player.dto.PlayerStatisticsResponse;
import com.tetrio.analytics.player.dto.PlayerStatisticsResponse.DerivedStatisticsDto;
import com.tetrio.analytics.player.dto.PlayerStatisticsResponse.MetricDefinitionDto;
import com.tetrio.analytics.player.dto.PlayerStatisticsResponse.RawStatisticsDto;
import com.tetrio.analytics.player.dto.SnapshotItemDto;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.RecordDocument;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
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

    public PlayerStatisticsResponse toPlayerStatistics(PlayerDocument doc) {
        if (doc == null) {
            throw new IllegalArgumentException("PlayerDocument no puede ser nulo.");
        }
        LeagueSummaryDto league = doc.league();
        PlayerDocument.ProfileMetadata profile = doc.profile();

        int gamesPlayed = league != null ? league.gamesPlayed() : 0;
        int gamesWon = league != null ? league.gamesWon() : 0;
        Double apm = league != null ? league.apm() : null;
        Double pps = league != null ? league.pps() : null;
        Double vs = league != null ? league.vs() : null;
        Double xp = profile != null ? profile.xp() : null;
        Double gametimeSeconds = profile != null ? profile.gametime() : null;

        RawStatisticsDto raw = new RawStatisticsDto(
                gamesPlayed,
                gamesWon,
                league != null ? league.tr() : null,
                league != null ? league.glicko() : null,
                league != null ? league.rd() : null,
                league != null ? league.gxe() : null,
                apm,
                pps,
                vs,
                xp,
                gametimeSeconds
        );

        Integer gamesLost = (league != null && gamesPlayed >= gamesWon) ? (gamesPlayed - gamesWon) : null;
        Double winRate = (gamesPlayed > 0)
                ? round(BigDecimal.valueOf(gamesWon).divide(BigDecimal.valueOf(gamesPlayed), 8, RoundingMode.HALF_UP), 4)
                : null;
        Double attackPerPiece = (apm != null && pps != null && pps > 0.0d)
                ? round(BigDecimal.valueOf(apm).divide(BigDecimal.valueOf(pps).multiply(BigDecimal.valueOf(60)), 8, RoundingMode.HALF_UP), 4)
                : null;
        Double vsPerApm = (vs != null && apm != null && apm > 0.0d)
                ? round(BigDecimal.valueOf(vs).divide(BigDecimal.valueOf(apm), 8, RoundingMode.HALF_UP), 4)
                : null;
        Double gametimeHours = (gametimeSeconds != null && gametimeSeconds >= 0.0d)
                ? round(BigDecimal.valueOf(gametimeSeconds).divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP), 2)
                : null;

        DerivedStatisticsDto derived = new DerivedStatisticsDto(
                gamesLost,
                winRate,
                attackPerPiece,
                vsPerApm,
                gametimeHours
        );

        List<MetricDefinitionDto> definitions = List.of(
                new MetricDefinitionDto("gamesLost", "gamesPlayed - gamesWon", "games", List.of("league.gamesPlayed", "league.gamesWon")),
                new MetricDefinitionDto("winRate", "gamesWon / gamesPlayed (null si gamesPlayed == 0)", "ratio [0, 1]", List.of("league.gamesWon", "league.gamesPlayed")),
                new MetricDefinitionDto("attackPerPiece", "apm / (pps * 60) (null si pps == null o pps <= 0)", "attack/piece", List.of("league.apm", "league.pps")),
                new MetricDefinitionDto("vsPerApm", "vs / apm (null si apm == null o apm <= 0)", "ratio", List.of("league.vs", "league.apm")),
                new MetricDefinitionDto("gametimeHours", "gametimeSeconds / 3600 (null si gametimeSeconds == null)", "hours", List.of("profile.gametime"))
        );

        return new PlayerStatisticsResponse(
                doc.externalPlayerId(),
                doc.usernameLower(),
                doc.lastSyncedAt(),
                raw,
                derived,
                definitions
        );
    }

    public RecordDocument toRecordDocument(
            String playerId,
            String mode,
            TetraRecordSummaryData summaryData,
            TetraChannelCache cache,
            Instant syncedAt,
            Instant cacheValidUntil,
            String sessionId
    ) {
        String primaryMetric = "40l".equals(mode) ? "finalTimeMs" : "score";
        TetraRecordSummaryData.TetraRecordEntry entry = summaryData != null ? summaryData.record() : null;
        TetraRecordSummaryData.TetraRecordResults results = entry != null ? entry.results() : null;
        TetraRecordSummaryData.TetraRecordStats stats = results != null ? results.stats() : null;
        TetraRecordSummaryData.TetraRecordAggregateStats agg = results != null ? results.aggregateStats() : null;
        TetraRecordSummaryData.TetraRecordFinesse finesse = stats != null ? stats.finesse() : null;

        Double finalTimeMs = stats != null ? normalizeNonNegativeDouble(stats.finalTime()) : null;
        Long score = (stats != null && stats.score() != null && stats.score() >= 0L) ? stats.score() : null;
        Double primaryValue = "40l".equals(mode)
                ? finalTimeMs
                : (score != null ? score.doubleValue() : null);

        Instant achievedAt = null;
        if (entry != null && entry.ts() != null && !entry.ts().isBlank()) {
            try {
                achievedAt = Instant.parse(entry.ts().trim());
            } catch (DateTimeParseException ignored) {
                achievedAt = null;
            }
        }

        return new RecordDocument(
                null,
                playerId,
                mode,
                RecordDocument.RECORD_TYPE_PERSONAL_BEST,
                entry != null ? entry.id() : null,
                entry != null ? entry.replayId() : null,
                primaryMetric,
                primaryValue,
                finalTimeMs,
                score,
                stats != null ? normalizeNonNegativeInteger(stats.lines()) : null,
                stats != null ? normalizeNonNegativeInteger(stats.level()) : null,
                agg != null ? normalizeNonNegativeDouble(agg.pps()) : null,
                stats != null ? normalizeNonNegativeInteger(stats.piecesPlaced()) : null,
                stats != null ? normalizeNonNegativeInteger(stats.inputs()) : null,
                stats != null ? normalizeNonNegativeInteger(stats.tSpins()) : null,
                finesse != null ? normalizeNonNegativeInteger(finesse.faults()) : null,
                summaryData != null ? normalizePositiveInteger(summaryData.rank()) : null,
                summaryData != null ? normalizePositiveInteger(summaryData.rankLocal()) : null,
                achievedAt,
                syncedAt,
                cacheValidUntil,
                new RecordDocument.RecordSource(
                        cache != null ? cache.cachedAt() : null,
                        cache != null ? cache.cachedUntil() : null,
                        sessionId
                )
        );
    }

    public ModeRecordDto toModeRecordDto(RecordDocument doc) {
        if (doc == null || doc.recordId() == null) {
            return null;
        }
        Double kpp = (doc.inputs() != null && doc.piecesPlaced() != null && doc.piecesPlaced() > 0)
                ? round(BigDecimal.valueOf(doc.inputs()).divide(BigDecimal.valueOf(doc.piecesPlaced()), 8, RoundingMode.HALF_UP), 4)
                : null;
        return new ModeRecordDto(
                doc.mode(),
                doc.recordId(),
                doc.replayId(),
                doc.primaryMetric(),
                doc.value(),
                doc.finalTimeMs(),
                doc.score(),
                doc.lines(),
                doc.level(),
                doc.pps(),
                doc.piecesPlaced(),
                doc.inputs(),
                kpp,
                doc.tSpins(),
                doc.finesseFaults(),
                doc.globalRank(),
                doc.countryRank(),
                doc.achievedAt()
        );
    }

    private static Double round(BigDecimal val, int scale) {
        return val.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static Integer normalizeNonNegativeInteger(Integer value) {
        if (value == null || value < 0) {
            return null;
        }
        return value;
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
