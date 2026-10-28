package com.tetrio.analytics.player;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraRecordSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@AutoConfigureMockMvc
class PlayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TetraChannelClient tetraChannelClient;

    @MockitoBean
    private PlayerPersistenceService playerPersistenceService;

    @Test
    void returnsLivePlayerProfileForNormalizedUsername() throws Exception {
        TetraUserData user = new TetraUserData(
                "5e9d4eec530bc13f9fdcd2c0",
                "5han",
                "JP",
                68967953.6,
                10482448.4
        );
        TetraLeagueSummaryData league = new TetraLeagueSummaryData(
                1711,
                1471,
                24863.06,
                4662.16,
                99.99,
                99.9993,
                "x+",
                "x+",
                170.6,
                2.73,
                353.52,
                1,
                0.0,
                false
        );
        TetraChannelCache cache = new TetraChannelCache("hit", 1792538893167L, 1792539193167L);

        when(tetraChannelClient.fetchUser(eq("5han"), anyString()))
                .thenReturn(new TetraChannelResponse<>(user, cache));
        when(tetraChannelClient.fetchLeagueSummary(eq("5han"), anyString()))
                .thenReturn(new TetraChannelResponse<>(league, cache));

        mockMvc.perform(get("/api/players/by-username/  5HAN  ")
                        .header("X-Request-Id", "trace-id-12345678"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-id-12345678"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerId").value("5e9d4eec530bc13f9fdcd2c0"))
                .andExpect(jsonPath("$.username").value("5han"))
                .andExpect(jsonPath("$.country").value("JP"))
                .andExpect(jsonPath("$.dataSource").value("live"))
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.league.gamesPlayed").value(1711))
                .andExpect(jsonPath("$.league.gamesWon").value(1471))
                .andExpect(jsonPath("$.league.ranked").value(true))
                .andExpect(jsonPath("$.league.tr").value(24863.06))
                .andExpect(jsonPath("$.league.rank").value("x+"))
                .andExpect(jsonPath("$.league.standing").value(1));
    }

    @Test
    void returns400ValidationFailedProblemDetailForInvalidUsername() throws Exception {
        mockMvc.perform(get("/api/players/by-username/ab")
                        .header("X-Request-Id", "invalid!trace!id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/players/by-username/ab"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId", matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.errors[0].field").value("username"))
                .andExpect(jsonPath("$.errors[0].message").value("Formato de username no válido"));
    }

    @Test
    void returns404PlayerNotFoundProblemDetailWhenPlayerDoesNotExist() throws Exception {
        when(tetraChannelClient.fetchUser(eq("ghostuser"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.PLAYER_NOT_FOUND,
                        "El jugador solicitado no existe en TETRA CHANNEL."
                ));

        mockMvc.perform(get("/api/players/by-username/ghostuser"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/player-not-found"))
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void returns429RateLimitedWithRetryAfterHeaderWhenLocalBudgetExhausted() throws Exception {
        when(tetraChannelClient.fetchUser(eq("5han"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.RATE_LIMITED,
                        "El presupuesto local de consultas hacia TETRA CHANNEL está agotado temporalmente.",
                        4L
                ));

        mockMvc.perform(get("/api/players/by-username/5han"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "4"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/rate-limited"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void returns502And503ProblemDetailsAccordingToUpstreamErrorClassification() throws Exception {
        when(tetraChannelClient.fetchUser(eq("badupstream"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.UPSTREAM_BAD_RESPONSE,
                        "TETRA CHANNEL indicó un fallo al procesar la consulta."
                ));

        mockMvc.perform(get("/api/players/by-username/badupstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_BAD_RESPONSE"));

        when(tetraChannelClient.fetchUser(eq("timeoutuser"), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "TETRA CHANNEL limitó temporalmente las solicitudes.",
                        10L
                ));

        mockMvc.perform(get("/api/players/by-username/timeoutuser"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void allowsConfiguredCorsOriginAndRejectsUnlistedOrigin() throws Exception {
        mockMvc.perform(options("/api/players/by-username/5han")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Content-Type,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-Id, Retry-After"));

        mockMvc.perform(options("/api/players/by-username/5han")
                        .header("Origin", "http://malicious.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsCachedAndStaleProfilesByPlayerIdEndpoint() throws Exception {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        Instant now = Instant.now();
        LeagueSummaryDto league = new LeagueSummaryDto(
                1711, 1471, true, 24863.06, 4662.16, 99.99, 99.9993,
                "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0, false
        );

        PlayerDocument cachedDoc = new PlayerDocument(
                "doc-1",
                playerId,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(68967953.6, 10482448.4),
                league,
                now.minusSeconds(30),
                now.minusSeconds(30),
                now.plusSeconds(270),
                playerId + ":" + now.minusSeconds(30).toEpochMilli(),
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );
        when(playerPersistenceService.findByExternalPlayerId(playerId))
                .thenReturn(Optional.of(cachedDoc));

        mockMvc.perform(get("/api/players/" + playerId.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.username").value("5han"))
                .andExpect(jsonPath("$.dataSource").value("cache"));

        PlayerDocument expiredDoc = new PlayerDocument(
                "doc-1",
                playerId,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(68967953.6, 10482448.4),
                league,
                now.minusSeconds(600),
                now.minusSeconds(600),
                now.minusSeconds(300),
                playerId + ":" + now.minusSeconds(600).toEpochMilli(),
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );
        when(playerPersistenceService.findByExternalPlayerId(playerId))
                .thenReturn(Optional.of(expiredDoc));
        when(tetraChannelClient.fetchUser(eq(playerId), anyString()))
                .thenThrow(new ApiProblemException(
                        ErrorCode.UPSTREAM_UNAVAILABLE,
                        "TETRA CHANNEL no disponible",
                        10L
                ));

        mockMvc.perform(get("/api/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.dataSource").value("stale"));
    }

    @Test
    void returns400ValidationFailedProblemDetailForInvalidPlayerId() throws Exception {
        mockMvc.perform(get("/api/players/invalid-mongo-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/validation-failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("playerId"))
                .andExpect(jsonPath("$.errors[0].message").value("Formato de playerId no válido"));
    }

    @Test
    void returnsPaginatedSnapshotsInAscendingOrderAndValidatesFilters() throws Exception {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        Instant t1 = Instant.parse("2026-10-20T10:00:00Z");
        Instant t2 = Instant.parse("2026-10-20T12:00:00Z");

        PlayerDocument storedPlayer = new PlayerDocument(
                "doc-1", playerId, "5han", "5han", "JP", null, null, t2, t1, t2.plusSeconds(300),
                playerId + ":1", PlayerDocument.CURRENT_SCHEMA_VERSION
        );
        when(playerPersistenceService.findByExternalPlayerId(playerId))
                .thenReturn(Optional.of(storedPlayer));

        SnapshotDocument s1 = new SnapshotDocument(
                "snap-1", playerId, playerId + ":1", t1, t1.plusSeconds(300), t1,
                24800.0, 4600.0, 62.0, "x+",
                new SnapshotDocument.SnapshotMetrics(168.0, 2.70, 349.0, 99.98, 1700, 1460, 2, 0.0, "x+", true, false),
                null, SnapshotDocument.CURRENT_SCHEMA_VERSION
        );
        SnapshotDocument s2 = new SnapshotDocument(
                "snap-2", playerId, playerId + ":2", t2, t2.plusSeconds(300), t2,
                24863.06, 4662.16, 60.5, "x+",
                new SnapshotDocument.SnapshotMetrics(170.6, 2.73, 353.52, 99.9993, 1711, 1471, 1, 0.0, "x+", true, false),
                null, SnapshotDocument.CURRENT_SCHEMA_VERSION
        );

        when(playerPersistenceService.findSnapshotsByPlayerId(playerId, t1, t2, 0, 20))
                .thenReturn(PageResponse.of(List.of(s1, s2), 0, 20, 2L));

        mockMvc.perform(get("/api/players/" + playerId + "/snapshots")
                        .param("from", "2026-10-20T10:00:00Z")
                        .param("to", "2026-10-20T12:00:00Z")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].capturedAt").value("2026-10-20T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].tr").value(24800.0))
                .andExpect(jsonPath("$.items[0].standing").value(2))
                .andExpect(jsonPath("$.items[1].capturedAt").value("2026-10-20T12:00:00Z"))
                .andExpect(jsonPath("$.items[1].tr").value(24863.06))
                .andExpect(jsonPath("$.items[1].standing").value(1));

        // from > to -> 400 VALIDATION_FAILED
        mockMvc.perform(get("/api/players/" + playerId + "/snapshots")
                        .param("from", "2026-10-20T15:00:00Z")
                        .param("to", "2026-10-20T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("from"));

        // size > 100 -> 400 VALIDATION_FAILED
        mockMvc.perform(get("/api/players/" + playerId + "/snapshots")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void returns404PlayerNotFoundWhenRequestingSnapshotsForUnstoredPlayer() throws Exception {
        String unstoredId = "000000000000000000000099";
        when(playerPersistenceService.findByExternalPlayerId(unstoredId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/players/" + unstoredId + "/snapshots"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void returnsPlayerStatisticsWithDerivedMetricsAndDefinitions() throws Exception {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        Instant syncedAt = Instant.parse("2026-10-20T18:00:00Z");
        PlayerDocument storedPlayer = new PlayerDocument(
                "doc-1",
                playerId,
                "5han",
                "5han",
                "JP",
                new PlayerDocument.ProfileMetadata(50000.0, 3600.0),
                new LeagueSummaryDto(100, 80, true, 24800.0, 2200.0, 65.0, 99.5, "x+", "x+", 150.0, 2.5, 300.0, 1, 0.0, false),
                syncedAt,
                syncedAt,
                syncedAt.plusSeconds(300),
                playerId + ":1",
                PlayerDocument.CURRENT_SCHEMA_VERSION
        );
        when(playerPersistenceService.findByExternalPlayerId(playerId)).thenReturn(Optional.of(storedPlayer));

        mockMvc.perform(get("/api/players/" + playerId + "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.username").value("5han"))
                .andExpect(jsonPath("$.derived.gamesLost").value(20))
                .andExpect(jsonPath("$.derived.winRate").value(0.8))
                .andExpect(jsonPath("$.derived.attackPerPiece").value(1.0))
                .andExpect(jsonPath("$.derived.vsPerApm").value(2.0))
                .andExpect(jsonPath("$.derived.gametimeHours").value(1.0))
                .andExpect(jsonPath("$.definitions.length()").value(5));
    }

    @Test
    void returnsLivePlayerRecordsFor40lAndBlitzModes() throws Exception {
        String playerId = "5e9d4eec530bc13f9fdcd2c0";
        TetraChannelCache cache = new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_300_000L);

        TetraRecordSummaryData rec40l = new TetraRecordSummaryData(
                new TetraRecordSummaryData.TetraRecordEntry(
                        "6870bcd3ee681267d377b740",
                        "rep-40l",
                        "40l",
                        "2025-07-11T07:27:15.930Z",
                        new TetraRecordSummaryData.TetraRecordResults(
                                new TetraRecordSummaryData.TetraRecordAggregateStats(0.0, 6.2668, 0.0),
                                new TetraRecordSummaryData.TetraRecordStats(
                                        16130.0, 13668L, 40, 1, 303, 101, 0,
                                        new TetraRecordSummaryData.TetraRecordFinesse(10, 6, 95)
                                )
                        )
                ),
                36,
                -1
        );
        TetraRecordSummaryData recBlitz = new TetraRecordSummaryData(
                new TetraRecordSummaryData.TetraRecordEntry(
                        "6901c2851e0307a55a2dc92b",
                        "rep-blitz",
                        "blitz",
                        "2025-10-29T07:30:13.289Z",
                        new TetraRecordSummaryData.TetraRecordResults(
                                new TetraRecordSummaryData.TetraRecordAggregateStats(0.0, 3.6167, 0.0),
                                new TetraRecordSummaryData.TetraRecordStats(
                                        120000.0, 1015993L, 170, 13, 1615, 434, 61,
                                        new TetraRecordSummaryData.TetraRecordFinesse(2, 95, 379)
                                )
                        )
                ),
                166,
                12
        );

        when(playerPersistenceService.findRecordsByPlayerId(playerId)).thenReturn(List.of());
        when(tetraChannelClient.fetchRecordSummary(eq(playerId), eq("40l"), anyString()))
                .thenReturn(new TetraChannelResponse<>(rec40l, cache));
        when(tetraChannelClient.fetchRecordSummary(eq(playerId), eq("blitz"), anyString()))
                .thenReturn(new TetraChannelResponse<>(recBlitz, cache));

        mockMvc.perform(get("/api/players/" + playerId + "/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.dataSource").value("live"))
                .andExpect(jsonPath("$.modes['40l'].primaryMetric").value("finalTimeMs"))
                .andExpect(jsonPath("$.modes['40l'].primaryValue").value(16130.0))
                .andExpect(jsonPath("$.modes['40l'].kpp").value(3.0))
                .andExpect(jsonPath("$.modes['40l'].globalRank").value(36))
                .andExpect(jsonPath("$.modes['40l'].countryRank").value(nullValue()))
                .andExpect(jsonPath("$.modes['blitz'].primaryMetric").value("score"))
                .andExpect(jsonPath("$.modes['blitz'].primaryValue").value(1015993.0))
                .andExpect(jsonPath("$.modes['blitz'].tSpins").value(61))
                .andExpect(jsonPath("$.modes['blitz'].countryRank").value(12));
    }
}
