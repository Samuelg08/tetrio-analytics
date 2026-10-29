package com.tetrio.analytics.ranking;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService.LocalRankingQueryResult;
import com.tetrio.analytics.ranking.dto.RankingMetric;
import com.tetrio.analytics.ranking.dto.RankingUniverseDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@AutoConfigureMockMvc
class RankingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerPersistenceService playerPersistenceService;

    @Test
    void returnsLocalRankingWithUniverseMetadataAndPositions() throws Exception {
        Instant syncedAt = Instant.parse("2026-10-20T18:00:00Z");
        PlayerDocument first = new PlayerDocument(
                "doc-1",
                "5e9d4eec530bc13f9fdcd2c0",
                "5han",
                "5han",
                "JP",
                null,
                new LeagueSummaryDto(1711, 1471, true, 24863.06, 4662.16, 99.99, 99.9993, "x+", "x+", 170.6, 2.73, 353.52, 1, 0.0, false),
                syncedAt,
                syncedAt,
                syncedAt,
                "k1",
                1
        );

        when(playerPersistenceService.queryLocalRanking(RankingMetric.TR, null, 0, 20))
                .thenReturn(new LocalRankingQueryResult(
                        PageResponse.of(List.of(first), 0, 20, 500L),
                        500L,
                        12L,
                        syncedAt
                ));

        mockMvc.perform(get("/api/rankings/local")
                        .param("metric", "tr"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.scope").value("local"))
                .andExpect(jsonPath("$.metric").value("tr"))
                .andExpect(jsonPath("$.universe.description").value(RankingUniverseDto.LOCAL_UNIVERSE_DESCRIPTION))
                .andExpect(jsonPath("$.universe.playersIncluded").value(500))
                .andExpect(jsonPath("$.universe.playersExcludedNullMetric").value(12))
                .andExpect(jsonPath("$.universe.dataAsOf").value("2026-10-20T18:00:00Z"))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[0].playerId").value("5e9d4eec530bc13f9fdcd2c0"))
                .andExpect(jsonPath("$.items[0].username").value("5han"))
                .andExpect(jsonPath("$.items[0].country").value("JP"))
                .andExpect(jsonPath("$.items[0].value").value(24863.06))
                .andExpect(jsonPath("$.items[0].lastSyncedAt").value("2026-10-20T18:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(500))
                .andExpect(jsonPath("$.totalPages").value(25));
    }

    @Test
    void returns400ValidationFailedWhenMetricOrCountryIsInvalid() throws Exception {
        mockMvc.perform(get("/api/rankings/local"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("metric"));

        mockMvc.perform(get("/api/rankings/local")
                        .param("metric", "winrate"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("metric"));

        mockMvc.perform(get("/api/rankings/local")
                        .param("metric", "pps")
                        .param("country", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("country"));
    }
}
