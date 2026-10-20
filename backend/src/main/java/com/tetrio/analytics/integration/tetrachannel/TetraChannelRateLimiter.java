package com.tetrio.analytics.integration.tetrachannel;

import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TetraChannelRateLimiter {

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Object lock = new Object();
    private final long minIntervalMs;
    private final long acquireTimeoutMs;
    private final Clock clock;
    private final Sleeper sleeper;

    private long nextAvailableSlotMs = 0L;
    private long cooldownUntilMs = 0L;

    @Autowired
    public TetraChannelRateLimiter(AppProperties properties, Clock clock) {
        this(
                properties.tetraChannel().minIntervalMs(),
                properties.tetraChannel().acquireTimeoutMs(),
                clock,
                Thread::sleep
        );
    }

    public TetraChannelRateLimiter(
            long minIntervalMs,
            long acquireTimeoutMs,
            Clock clock,
            Sleeper sleeper
    ) {
        this.minIntervalMs = Math.max(0L, minIntervalMs);
        this.acquireTimeoutMs = Math.max(0L, acquireTimeoutMs);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public void acquirePermit() {
        long waitMs;
        synchronized (lock) {
            long now = clock.millis();
            if (cooldownUntilMs > now) {
                long retryAfterSec = Math.max(1L, (cooldownUntilMs - now + 999L) / 1000L);
                throw new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "El proveedor TETRA CHANNEL se encuentra temporalmente en pausa por límite de solicitudes.",
                        retryAfterSec
                );
            }

            long earliestAllowedMs = Math.max(now, nextAvailableSlotMs);
            waitMs = earliestAllowedMs - now;

            if (waitMs > acquireTimeoutMs) {
                long retryAfterSec = Math.max(1L, (waitMs + 999L) / 1000L);
                throw new ApiProblemException(
                        ErrorCode.RATE_LIMITED,
                        "El presupuesto local de consultas hacia TETRA CHANNEL está agotado temporalmente.",
                        retryAfterSec
                );
            }

            nextAvailableSlotMs = earliestAllowedMs + minIntervalMs;
        }

        if (waitMs > 0L) {
            try {
                sleeper.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "La espera para consultar TETRA CHANNEL fue interrumpida.",
                        e
                );
            }
        }
    }

    public void recordUpstreamCooldown(long cooldownDurationMs) {
        long effectiveDurationMs = Math.max(cooldownDurationMs, minIntervalMs);
        synchronized (lock) {
            long until = clock.millis() + effectiveDurationMs;
            cooldownUntilMs = Math.max(cooldownUntilMs, until);
            nextAvailableSlotMs = Math.max(nextAvailableSlotMs, cooldownUntilMs);
        }
    }
}
