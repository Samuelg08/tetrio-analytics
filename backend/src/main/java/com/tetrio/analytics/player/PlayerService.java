package com.tetrio.analytics.player;

import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.DataSource;
import com.tetrio.analytics.player.dto.PlayerProfileResponse;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,16}$");
    private static final Pattern PLAYER_ID_PATTERN = Pattern.compile("^[0-9a-f]{24}$");

    private final TetraChannelClient tetraChannelClient;
    private final PlayerMapper playerMapper;
    private final PlayerPersistenceService playerPersistenceService;
    private final CacheWindowCalculator cacheWindowCalculator;
    private final Clock clock;

    public PlayerService(
            TetraChannelClient tetraChannelClient,
            PlayerMapper playerMapper,
            PlayerPersistenceService playerPersistenceService,
            CacheWindowCalculator cacheWindowCalculator,
            Clock clock
    ) {
        this.tetraChannelClient = tetraChannelClient;
        this.playerMapper = playerMapper;
        this.playerPersistenceService = playerPersistenceService;
        this.cacheWindowCalculator = cacheWindowCalculator;
        this.clock = clock;
    }

    public PlayerProfileResponse getByUsername(String rawUsername) {
        String normalizedUsername = normalizeAndValidateUsername(rawUsername);
        Instant now = Instant.now(clock);

        PlayerDocument existing = findByUsernameSafe(normalizedUsername);
        if (isCacheValid(existing, now)) {
            return playerMapper.fromPlayerDocument(existing, DataSource.CACHE);
        }

        return syncFromUpstream(normalizedUsername, existing);
    }

    public PlayerProfileResponse getByPlayerId(String rawPlayerId) {
        String normalizedPlayerId = normalizeAndValidatePlayerId(rawPlayerId);
        Instant now = Instant.now(clock);

        PlayerDocument existing = findByPlayerIdSafe(normalizedPlayerId);
        if (isCacheValid(existing, now)) {
            return playerMapper.fromPlayerDocument(existing, DataSource.CACHE);
        }

        return syncFromUpstream(normalizedPlayerId, existing);
    }

    private PlayerProfileResponse syncFromUpstream(String identifier, PlayerDocument existing) {
        String sessionId = "live-" + UUID.randomUUID();
        final TetraChannelResponse<TetraUserData> userResponse;
        final TetraChannelResponse<TetraLeagueSummaryData> leagueResponse;

        try {
            userResponse = tetraChannelClient.fetchUser(identifier, sessionId);
            leagueResponse = tetraChannelClient.fetchLeagueSummary(identifier, sessionId);
        } catch (ApiProblemException ex) {
            if (existing != null && isStaleFallbackEligible(ex.errorCode())) {
                log.warn(
                        "Fallo o límite al consultar TETRA CHANNEL para '{}' ({}); sirviendo copia local stale.",
                        identifier,
                        ex.errorCode()
                );
                return playerMapper.fromPlayerDocument(existing, DataSource.STALE);
            }
            throw ex;
        }

        Instant syncedAt = Instant.now(clock);
        PlayerProfileResponse liveProfile = playerMapper.toPlayerProfile(
                userResponse.data(),
                leagueResponse.data(),
                syncedAt,
                DataSource.LIVE
        );

        PlayerDocument existingForWindow = existing != null
                ? existing
                : findByPlayerIdSafe(liveProfile.playerId());

        EffectiveCacheWindow cacheWindow = cacheWindowCalculator.computeWindow(
                liveProfile.playerId(),
                userResponse.cache(),
                leagueResponse.cache(),
                syncedAt,
                existingForWindow
        );

        PlayerDocument playerDoc = playerMapper.toPlayerDocument(
                userResponse.data(),
                liveProfile.league(),
                cacheWindow,
                syncedAt
        );
        SnapshotDocument snapshotDoc = playerMapper.toSnapshotDocument(
                userResponse.data(),
                liveProfile.league(),
                cacheWindow,
                userResponse.cache(),
                leagueResponse.cache(),
                syncedAt,
                sessionId
        );

        persistSafe(playerDoc, snapshotDoc);
        return liveProfile;
    }

    private static boolean isCacheValid(PlayerDocument doc, Instant now) {
        return doc != null
                && doc.cacheValidUntil() != null
                && now.isBefore(doc.cacheValidUntil());
    }

    private static boolean isStaleFallbackEligible(ErrorCode errorCode) {
        return errorCode == ErrorCode.RATE_LIMITED
                || errorCode == ErrorCode.UPSTREAM_UNAVAILABLE
                || errorCode == ErrorCode.UPSTREAM_BAD_RESPONSE;
    }

    private PlayerDocument findByUsernameSafe(String usernameLower) {
        try {
            Optional<PlayerDocument> found = playerPersistenceService.findByUsernameLower(usernameLower);
            return found != null ? found.orElse(null) : null;
        } catch (DataAccessException ex) {
            log.warn("No se pudo consultar MongoDB por username '{}': {}", usernameLower, ex.getMessage());
            return null;
        }
    }

    private PlayerDocument findByPlayerIdSafe(String externalPlayerId) {
        try {
            Optional<PlayerDocument> found = playerPersistenceService.findByExternalPlayerId(externalPlayerId);
            return found != null ? found.orElse(null) : null;
        } catch (DataAccessException ex) {
            log.warn("No se pudo consultar MongoDB por playerId '{}': {}", externalPlayerId, ex.getMessage());
            return null;
        }
    }

    private void persistSafe(PlayerDocument playerDoc, SnapshotDocument snapshotDoc) {
        try {
            playerPersistenceService.saveSyncedPlayerAndSnapshot(playerDoc, snapshotDoc);
        } catch (DataAccessException ex) {
            log.warn(
                    "No se pudo persistir jugador '{}' y snapshot '{}': {}",
                    playerDoc.externalPlayerId(),
                    snapshotDoc.cacheWindowKey(),
                    ex.getMessage()
            );
        }
    }

    public static String normalizeAndValidateUsername(String rawUsername) {
        if (rawUsername == null) {
            throw ApiProblemException.validationFailed("username", "Formato de username no válido");
        }
        String normalized = rawUsername.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw ApiProblemException.validationFailed("username", "Formato de username no válido");
        }
        return normalized;
    }

    public static String normalizeAndValidatePlayerId(String rawPlayerId) {
        if (rawPlayerId == null) {
            throw ApiProblemException.validationFailed("playerId", "Formato de playerId no válido");
        }
        String normalized = rawPlayerId.trim().toLowerCase(Locale.ROOT);
        if (!PLAYER_ID_PATTERN.matcher(normalized).matches()) {
            throw ApiProblemException.validationFailed("playerId", "Formato de playerId no válido");
        }
        return normalized;
    }
}
