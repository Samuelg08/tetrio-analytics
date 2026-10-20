package com.tetrio.analytics.player;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_TETRA_TESTS", matches = "true")
class PlayerLiveSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchesRealPlayerAndHandlesReal404FromTetraChannel() throws Exception {
        mockMvc.perform(get("/api/players/by-username/5han"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value("5e9d4eec530bc13f9fdcd2c0"))
                .andExpect(jsonPath("$.username").value("5han"))
                .andExpect(jsonPath("$.dataSource").value("live"))
                .andExpect(jsonPath("$.league.gamesPlayed").isNumber());

        mockMvc.perform(get("/api/players/by-username/no_user_zz9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }
}
