package com.tetrio.analytics.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tetrio.analytics.extraction.ExtractionCliRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
@AutoConfigureMockMvc
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void healthReturnsUpAndRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Request-Id", "valid-req-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "valid-req-12345"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsEndpointExposesContractPathsAndWebProfileExcludesExtractionRunner() throws Exception {
        assertThat(applicationContext.getBeansOfType(ExtractionCliRunner.class)).isEmpty();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("0.2.0"))
                .andExpect(jsonPath("$.paths['/api/health']").exists())
                .andExpect(jsonPath("$.paths['/api/players/by-username/{username}']").exists())
                .andExpect(jsonPath("$.paths['/api/players/{playerId}']").exists())
                .andExpect(jsonPath("$.paths['/api/players/{playerId}/snapshots']").exists())
                .andExpect(jsonPath("$.paths['/api/rankings/local']").exists())
                .andExpect(jsonPath("$.paths['/api/comparisons']").exists());
    }
}
