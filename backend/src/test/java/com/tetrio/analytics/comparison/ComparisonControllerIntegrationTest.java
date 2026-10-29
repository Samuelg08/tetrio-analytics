package com.tetrio.analytics.comparison;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import java.time.Instant;
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
class ComparisonControllerIntegrationTest {

    private static final String PLAYER_A_ID = "5e9d4eec530bc13f9fdcd2c0";
    private static final String PLAYER_B_ID = "5eb2a07a0f991a70c58fb7cb";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerPersistenceService playerPersistenceService;

    @Test
    void returnsDescriptiveComparisonAndHeadToHeadNull() throws Exception {
        Instant syncedAt = Instant.parse("2026-10-20T18:30:00Z");
        PlayerDocument docA = new PlayerDocument(
                "doc-a",
                PLAYER_A_ID,
                "5han",
                "5han",
                "JP",
                null,
                new LeagueSummaryDto(1711, 1471, true, 24800.0, 2200.0, 65.0, 99.5, "x+", "x+", 150.0, 2.75, 350.0, 1, 0.0, false),
                syncedAt,
                syncedAt,
                syncedAt,
                "ka",
                1
        );
        PlayerDocument docB = new PlayerDocument(
                "doc-b",
                PLAYER_B_ID,
                "bplayer",
                "bplayer",
                "US",
                null,
                new LeagueSummaryDto(800, 500, true, 23800.0, 2100.0, 65.0, 95.0, "x", "x", 120.0, null, 310.0, 50, 0.01, false),
                syncedAt,
                syncedAt,
                syncedAt,
                "kb",
                1
        );

        when(playerPersistenceService.findByExternalPlayerId(PLAYER_A_ID)).thenReturn(Optional.of(docA));
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_B_ID)).thenReturn(Optional.of(docB));

        mockMvc.perform(post("/api/comparisons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerAId": "%s",
                                  "playerBId": "%s"
                                }
                                """.formatted(PLAYER_A_ID, PLAYER_B_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerA.playerId").value(PLAYER_A_ID))
                .andExpect(jsonPath("$.playerB.playerId").value(PLAYER_B_ID))
                .andExpect(jsonPath("$.differences.tr").value(1000.0))
                .andExpect(jsonPath("$.differences.glicko").value(100.0))
                .andExpect(jsonPath("$.differences.gxe").value(4.5))
                .andExpect(jsonPath("$.differences.apm").value(30.0))
                .andExpect(jsonPath("$.differences.pps").value(nullValue()))
                .andExpect(jsonPath("$.differences.vs").value(40.0))
                .andExpect(jsonPath("$.headToHead").value(nullValue()))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());
    }

    @Test
    void returns400ForInvalidOrIdenticalPlayerIdsAndMalformedJson() throws Exception {
        mockMvc.perform(post("/api/comparisons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerAId": "%s",
                                  "playerBId": "%s"
                                }
                                """.formatted(PLAYER_A_ID, PLAYER_A_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("playerBId"));

        mockMvc.perform(post("/api/comparisons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ malformed json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void returns404PlayerNotFoundIndicatingWhichPlayerIsMissing() throws Exception {
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_A_ID)).thenReturn(Optional.empty());
        when(playerPersistenceService.findByExternalPlayerId(PLAYER_B_ID)).thenReturn(Optional.of(
                new PlayerDocument("doc-b", PLAYER_B_ID, "bplayer", "bplayer", "US", null, null, Instant.now(), Instant.now(), Instant.now(), "kb", 1)
        ));

        mockMvc.perform(post("/api/comparisons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "playerAId": "%s",
                                  "playerBId": "%s"
                                }
                                """.formatted(PLAYER_A_ID, PLAYER_B_ID)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString("jugador A")))
                .andExpect(jsonPath("$.detail", containsString(PLAYER_A_ID)));
    }
}
