package com.tetrio.analytics.player;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TetraChannelClient tetraChannelClient;

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
}
