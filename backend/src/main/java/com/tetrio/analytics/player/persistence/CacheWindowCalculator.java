package com.tetrio.analytics.player.persistence;

import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CacheWindowCalculator {

    private static final Pattern MONGO_ID_PATTERN = Pattern.compile("^[0-9a-f]{24}$");

    public record EffectiveCacheWindow(
            long windowStartMs,
            long windowUntilMs,
            String cacheWindowKey
    ) {
        public EffectiveCacheWindow {
            if (cacheWindowKey == null || cacheWindowKey.isBlank()) {
                throw new IllegalArgumentException("cacheWindowKey es obligatorio y no puede estar vacío.");
            }
            if (windowStartMs <= 0L || windowUntilMs < windowStartMs) {
                throw new IllegalArgumentException("Marcas temporales de ventana de caché inválidas.");
            }
        }

        public Instant windowStartAt() {
            return Instant.ofEpochMilli(windowStartMs);
        }

        public Instant windowUntilAt() {
            return Instant.ofEpochMilli(windowUntilMs);
        }
    }

    private final long ttlMillis;

    @Autowired
    public CacheWindowCalculator(AppProperties properties) {
        this(properties.cache().ttlSeconds());
    }

    public CacheWindowCalculator(long ttlSeconds) {
        if (ttlSeconds <= 0L) {
            throw new IllegalArgumentException("CACHE_TTL_SECONDS debe ser mayor que 0.");
        }
        this.ttlMillis = ttlSeconds * 1000L;
    }

    public EffectiveCacheWindow computeWindow(
            String playerId,
            TetraChannelCache userCache,
            TetraChannelCache leagueCache,
            Instant syncInstant,
            PlayerDocument existingPlayer
    ) {
        if (playerId == null || !MONGO_ID_PATTERN.matcher(playerId).matches()) {
            throw new IllegalArgumentException("playerId debe ser un MongoID válido de 24 caracteres hexadecimales.");
        }
        if (syncInstant == null) {
            throw new IllegalArgumentException("syncInstant no puede ser nulo.");
        }

        long tSyncMs = syncInstant.toEpochMilli();

        // 2. Reutilización de ventana activa si ya existe una ventana vigente
        if (existingPlayer != null
                && existingPlayer.cacheValidUntil() != null
                && existingPlayer.cacheWindowStartAt() != null
                && existingPlayer.activeCacheWindowKey() != null
                && !existingPlayer.activeCacheWindowKey().isBlank()
                && tSyncMs < existingPlayer.cacheValidUntil().toEpochMilli()) {
            return new EffectiveCacheWindow(
                    existingPlayer.cacheWindowStartAt().toEpochMilli(),
                    existingPlayer.cacheValidUntil().toEpochMilli(),
                    existingPlayer.activeCacheWindowKey()
            );
        }

        // 3a. Cálculo determinista de windowStartMs
        long windowStartMs;
        if (leagueCache != null && leagueCache.cachedAt() != null && leagueCache.cachedAt() > 0L) {
            windowStartMs = leagueCache.cachedAt();
        } else if (userCache != null && userCache.cachedAt() != null && userCache.cachedAt() > 0L) {
            windowStartMs = userCache.cachedAt();
        } else {
            windowStartMs = (tSyncMs / ttlMillis) * ttlMillis;
        }

        // 3b. Cálculo determinista de windowUntilMs (estrategia conjunta con max)
        long upstreamUntilMs = 0L;
        if (leagueCache != null && leagueCache.cachedUntil() != null && leagueCache.cachedUntil() > 0L) {
            upstreamUntilMs = Math.max(upstreamUntilMs, leagueCache.cachedUntil());
        }
        if (userCache != null && userCache.cachedUntil() != null && userCache.cachedUntil() > 0L) {
            upstreamUntilMs = Math.max(upstreamUntilMs, userCache.cachedUntil());
        }

        long localUntilMs = tSyncMs + ttlMillis;
        long windowUntilMs = Math.max(upstreamUntilMs, localUntilMs);
        if (windowUntilMs < windowStartMs) {
            windowUntilMs = windowStartMs + ttlMillis;
        }

        // 4. Clave de idempotencia obligatoria
        String cacheWindowKey = playerId + ":" + windowStartMs;

        return new EffectiveCacheWindow(windowStartMs, windowUntilMs, cacheWindowKey);
    }
}
