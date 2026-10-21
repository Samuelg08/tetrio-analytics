package com.tetrio.analytics.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheWindowCalculatorTest {

    private static final String PLAYER_ID = "5e9d4eec530bc13f9fdcd2c0";
    private static final long TTL_SECONDS = 300L;
    private static final long TTL_MILLIS = 300_000L;

    private CacheWindowCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CacheWindowCalculator(TTL_SECONDS);
    }

    @Test
    void computesJointEffectiveWindowFromLeagueAndUserCaches() {
        Instant syncInstant = Instant.ofEpochMilli(1_700_000_050_000L);
        TetraChannelCache userCache = new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_600_000L);
        TetraChannelCache leagueCache = new TetraChannelCache("hit", 1_700_000_010_000L, 1_700_000_400_000L);

        EffectiveCacheWindow window = calculator.computeWindow(
                PLAYER_ID,
                userCache,
                leagueCache,
                syncInstant,
                null
        );

        assertThat(window.windowStartMs()).isEqualTo(1_700_000_010_000L);
        // max(userUntil=1700000600000, leagueUntil=1700000400000, sync+300s=1700000350000)
        assertThat(window.windowUntilMs()).isEqualTo(1_700_000_600_000L);
        assertThat(window.cacheWindowKey()).isEqualTo(PLAYER_ID + ":1700000010000");
    }

    @Test
    void fallsBackToUserCachedAtWhenLeagueCacheMissing() {
        Instant syncInstant = Instant.ofEpochMilli(1_700_000_050_000L);
        TetraChannelCache userCache = new TetraChannelCache("hit", 1_700_000_020_000L, 1_700_000_200_000L);

        EffectiveCacheWindow window = calculator.computeWindow(
                PLAYER_ID,
                userCache,
                null,
                syncInstant,
                null
        );

        assertThat(window.windowStartMs()).isEqualTo(1_700_000_020_000L);
        // localUntil = 1700000050000 + 300000 = 1700000350000 > userUntil (1700000200000)
        assertThat(window.windowUntilMs()).isEqualTo(1_700_000_350_000L);
        assertThat(window.cacheWindowKey()).isEqualTo(PLAYER_ID + ":1700000020000");
    }

    @Test
    void fallsBackToDeterministicLocalBucketWhenUpstreamCacheMetadataAbsent() {
        long tSyncMs = 1_700_000_123_456L;
        Instant syncInstant = Instant.ofEpochMilli(tSyncMs);

        EffectiveCacheWindow window = calculator.computeWindow(
                PLAYER_ID,
                null,
                null,
                syncInstant,
                null
        );

        long expectedBucketStart = (tSyncMs / TTL_MILLIS) * TTL_MILLIS;
        assertThat(window.windowStartMs()).isEqualTo(expectedBucketStart);
        assertThat(window.windowUntilMs()).isEqualTo(tSyncMs + TTL_MILLIS);
        assertThat(window.cacheWindowKey()).isEqualTo(PLAYER_ID + ":" + expectedBucketStart);
    }

    @Test
    void reusesActiveWindowWhenExistingPlayerWindowHasNotExpired() {
        Instant existingStart = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant existingUntil = Instant.ofEpochMilli(1_700_000_300_000L);
        String existingKey = PLAYER_ID + ":1700000000000";

        PlayerDocument existingPlayer = new PlayerDocument(
                "mongo-doc-id",
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                null,
                null,
                existingStart,
                existingStart,
                existingUntil,
                existingKey,
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );

        Instant syncInsideWindow = Instant.ofEpochMilli(1_700_000_150_000L);
        EffectiveCacheWindow reused = calculator.computeWindow(
                PLAYER_ID,
                new TetraChannelCache("hit", 1_700_000_140_000L, 1_700_000_440_000L),
                null,
                syncInsideWindow,
                existingPlayer
        );

        assertThat(reused.windowStartMs()).isEqualTo(existingStart.toEpochMilli());
        assertThat(reused.windowUntilMs()).isEqualTo(existingUntil.toEpochMilli());
        assertThat(reused.cacheWindowKey()).isEqualTo(existingKey);
    }

    @Test
    void createsNewWindowWhenExistingPlayerWindowHasExpired() {
        Instant existingStart = Instant.ofEpochMilli(1_700_000_000_000L);
        Instant existingUntil = Instant.ofEpochMilli(1_700_000_300_000L);
        String existingKey = PLAYER_ID + ":1700000000000";

        PlayerDocument existingPlayer = new PlayerDocument(
                "mongo-doc-id",
                PLAYER_ID,
                "5han",
                "5han",
                "JP",
                null,
                null,
                existingStart,
                existingStart,
                existingUntil,
                existingKey,
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );

        Instant syncAfterExpiry = Instant.ofEpochMilli(1_700_000_300_000L);
        TetraChannelCache leagueCache = new TetraChannelCache("miss", 1_700_000_300_000L, 1_700_000_600_000L);

        EffectiveCacheWindow nextWindow = calculator.computeWindow(
                PLAYER_ID,
                null,
                leagueCache,
                syncAfterExpiry,
                existingPlayer
        );

        assertThat(nextWindow.windowStartMs()).isEqualTo(1_700_000_300_000L);
        assertThat(nextWindow.windowUntilMs()).isEqualTo(1_700_000_600_000L);
        assertThat(nextWindow.cacheWindowKey()).isEqualTo(PLAYER_ID + ":1700000300000");
    }

    @Test
    void rejectsInvalidPlayerId() {
        assertThatThrownBy(() -> calculator.computeWindow(
                "not-a-mongo-id",
                null,
                null,
                Instant.now(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
