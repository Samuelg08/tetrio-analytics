package com.tetrio.analytics.integration.tetrachannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TetraChannelRateLimiterTest {

    static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long initialMillis) {
            this.millis = initialMillis;
        }

        void advanceMillis(long delta) {
            this.millis += delta;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    @Test
    void enforcesMinIntervalAndRejectsWhenWaitExceedsAcquireTimeout() {
        MutableClock clock = new MutableClock(10_000L);
        List<Long> sleeps = new ArrayList<>();
        TetraChannelRateLimiter limiter = new TetraChannelRateLimiter(
                1100L,
                2000L,
                clock,
                ms -> {
                    sleeps.add(ms);
                    clock.advanceMillis(ms);
                }
        );

        // 1st permit at t=10000: immediate, next slot = 11100
        limiter.acquirePermit();
        assertThat(sleeps).isEmpty();

        // 2nd permit at t=10200: waits 900ms (<= 2000ms), next slot = 12200
        clock.advanceMillis(200L);
        limiter.acquirePermit();
        assertThat(sleeps).containsExactly(900L);
        assertThat(clock.millis()).isEqualTo(11_100L);

        // If another thread already reserved up to t=13300 and a 4th request arrives at t=11100:
        // Wait for 3rd is 1100ms (<= 2000ms), next slot becomes 13300 (simulate without advancing clock yet)
        MutableClock fixedClock = new MutableClock(10_000L);
        TetraChannelRateLimiter queueLimiter = new TetraChannelRateLimiter(
                1100L,
                1500L,
                fixedClock,
                ms -> {} // do not advance clock to simulate concurrent burst at same instant
        );

        queueLimiter.acquirePermit(); // wait=0, next=11100
        queueLimiter.acquirePermit(); // wait=1100 (<=1500), next=12200

        // 3rd request at t=10000 would need to wait 2200ms (> 1500ms acquireTimeout)
        assertThatThrownBy(queueLimiter::acquirePermit)
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.errorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
                    assertThat(apiEx.retryAfterSeconds()).isEqualTo(3L);
                });
    }

    @Test
    void rejectsRequestsWithUpstreamUnavailableDuringUpstreamCooldown() {
        MutableClock clock = new MutableClock(50_000L);
        TetraChannelRateLimiter limiter = new TetraChannelRateLimiter(1100L, 3000L, clock, ms -> {});

        limiter.recordUpstreamCooldown(8_000L);

        assertThatThrownBy(limiter::acquirePermit)
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> {
                    ApiProblemException apiEx = (ApiProblemException) ex;
                    assertThat(apiEx.errorCode()).isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE);
                    assertThat(apiEx.retryAfterSeconds()).isEqualTo(8L);
                });

        clock.advanceMillis(8_001L);
        limiter.acquirePermit();
    }
}
