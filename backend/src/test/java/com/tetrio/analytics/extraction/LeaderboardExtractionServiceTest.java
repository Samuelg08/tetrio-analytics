package com.tetrio.analytics.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraGeneralStatsData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardEntry;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraPrisecter;
import com.tetrio.analytics.player.PlayerMapper;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.MongoTemplate;

class LeaderboardExtractionServiceTest {

    private static final Instant FIXED_START = Instant.parse("2026-10-20T12:00:00Z");
    private static final String ID_1 = "5e9d4eec530bc13f9fdcd2c0";
    private static final String ID_2 = "5e32fc85ab319c2ab1beb07c";
    private static final String ID_3 = "0123456789abcdef01234567";

    @TempDir
    Path tempDir;

    private TetraChannelClient tetraChannelClient;
    private PlayerPersistenceService playerPersistenceService;
    private MongoTemplate mongoTemplate;
    private ObjectMapper objectMapper;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        tetraChannelClient = mock(TetraChannelClient.class);
        playerPersistenceService = mock(PlayerPersistenceService.class);
        mongoTemplate = mock(MongoTemplate.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        fixedClock = Clock.fixed(FIXED_START, ZoneOffset.UTC);

        when(playerPersistenceService.findByExternalPlayerId(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void extractsPagesDeduplicatesRejectsInvalidAndWritesManifest() throws Exception {
        Path rawDir = tempDir.resolve("raw");
        Path processedDir = tempDir.resolve("processed");

        LeaderboardExtractionService service = createService(rawDir, processedDir, "", "20261020-v1", 2, 0, 1, 2);

        when(tetraChannelClient.fetchGeneralStats(anyString()))
                .thenReturn(new TetraChannelResponse<>(new TetraGeneralStatsData(100000L, 36227L), null));

        TetraChannelCache cache = new TetraChannelCache("hit", 1_700_000_000_000L, 1_700_000_600_000L);

        // Page 1: 2 entries (ID_1 valid ranked, and 1 corrupted entry with invalid ID)
        TetraLeaderboardEntry entry1 = validEntry(ID_1, "5han", "JP", 24863.06, 170.6, "24863.06", "0", "0");
        TetraLeaderboardEntry badEntry = validEntry("not-a-valid-mongo-id", "bad", "US", 20000.0, 100.0, "24000.0", "0", "0");

        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), isNull(), anyString()))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of(entry1, badEntry)),
                        cache,
                        "{\"success\":true,\"page\":1}"
                ));

        // Page 2: 2 entries (ID_1 duplicate across pages, ID_2 valid with null apm)
        TetraLeaderboardEntry duplicateEntry1 = validEntry(ID_1, "5han", "JP", 24860.0, 170.0, "24860.0", "0", "0");
        TetraLeaderboardEntry entry2WithSentinel = new TetraLeaderboardEntry(
                ID_2,
                "osk",
                null,
                1000.0,
                500.0,
                100.0,
                50,
                25,
                new TetraLeagueSummaryData(50, 25, 22000.0, 2100.0, 65.0, 95.0, "u", "x", -1.0, 2.2, 250.0, 150, 0.05, false),
                new TetraPrisecter(new BigDecimal("22000.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        );

        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), eq("24000:0:0"), anyString()))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of(duplicateEntry1, entry2WithSentinel)),
                        cache,
                        "{\"success\":true,\"page\":2}"
                ));

        // Page 3: 1 final entry (< batchSize=2, so pagination finishes)
        TetraLeaderboardEntry entry3 = validEntry(ID_3, "czsmall0402", "TW", 21500.0, 140.0, "21500", "0", "0");
        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), eq("22000:0:0"), anyString()))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of(entry3)),
                        cache,
                        "{\"success\":true,\"page\":3}"
                ));

        ExtractionCheckpoint result = service.runPhase1Leaderboard();

        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.pagesCompleted()).isEqualTo(3);
        assertThat(result.counters().playersSeen()).isEqualTo(5);
        assertThat(result.counters().uniquePlayers()).isEqualTo(3);
        assertThat(result.counters().duplicatesSkipped()).isEqualTo(1);
        assertThat(result.counters().rejectedRecords()).isEqualTo(1);

        Path runRawDir = rawDir.resolve("tetra-channel").resolve(result.runId());
        assertThat(runRawDir.resolve("page-0001.json")).exists();
        assertThat(runRawDir.resolve("page-0002.json")).exists();
        assertThat(runRawDir.resolve("page-0003.json")).exists();
        assertThat(runRawDir.resolve("checkpoint.json")).exists();

        Path datasetDir = processedDir.resolve("20261020-v1");
        List<String> exportedLines = Files.readAllLines(datasetDir.resolve("players_league.jsonl"), StandardCharsets.UTF_8);
        assertThat(exportedLines).hasSize(3);

        List<String> rejectedLines = Files.readAllLines(datasetDir.resolve("rejected.jsonl"), StandardCharsets.UTF_8);
        assertThat(rejectedLines).hasSize(1);
        assertThat(rejectedLines.getFirst()).contains("INVALID_PLAYER_ID");

        ExtractionManifest manifest = objectMapper.readValue(
                datasetDir.resolve("manifest.json").toFile(),
                ExtractionManifest.class
        );
        assertThat(manifest.datasetVersion()).isEqualTo("20261020-v1");
        assertThat(manifest.featureSchemaVersion()).isEqualTo("tr-cross-v1");
        assertThat(manifest.counts().upstreamRankedCountAtStart()).isEqualTo(36227L);
        assertThat(manifest.counts().pagesFetched()).isEqualTo(3);
        assertThat(manifest.counts().uniquePlayersExported()).isEqualTo(3);
        assertThat(manifest.counts().duplicatesSkipped()).isEqualTo(1);
        assertThat(manifest.counts().rejectedRecords()).isEqualTo(1);
        // entry2 had apm=-1 -> converted to null -> 1 null out of 3 exported records
        assertThat(manifest.fields().get("apm").nullCount()).isEqualTo(1);
        assertThat(manifest.fields().get("tr").nullCount()).isZero();

        verify(playerPersistenceService, times(3)).saveSyncedPlayerAndSnapshot(any(), any());
    }

    @Test
    void pausesOnConsecutiveErrorsAndResumesFromCheckpointWithoutDuplicates() throws Exception {
        Path rawDir = tempDir.resolve("raw-resume");
        Path processedDir = tempDir.resolve("processed-resume");

        LeaderboardExtractionService firstRunService =
                createService(rawDir, processedDir, "", "20261020-resume", 2, 0, 1, 2);

        when(tetraChannelClient.fetchGeneralStats(anyString()))
                .thenReturn(new TetraChannelResponse<>(new TetraGeneralStatsData(100000L, 36227L), null));

        TetraLeaderboardEntry entry1 = validEntry(ID_1, "5han", "JP", 24863.06, 170.6, "24863.06", "0", "0");
        TetraLeaderboardEntry entry2 = validEntry(ID_2, "osk", "CA", 22000.0, 120.0, "22000", "0", "0");

        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), isNull(), anyString()))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of(entry1, entry2)),
                        null,
                        "{\"success\":true,\"page\":1}"
                ));

        // Page 2 fails on both attempts -> reaches maxConsecutiveErrors=2 -> pauses
        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), eq("22000:0:0"), anyString()))
                .thenThrow(new ApiProblemException(ErrorCode.UPSTREAM_UNAVAILABLE, "Timeout", 5L));

        ExtractionCheckpoint paused = firstRunService.runPhase1Leaderboard();
        assertThat(paused.status()).isEqualTo("paused");
        assertThat(paused.pagesCompleted()).isEqualTo(1);
        assertThat(paused.lastCursor()).isEqualTo("22000:0:0");
        assertThat(paused.counters().httpErrorsByStatus()).containsEntry("503", 2);

        // Now resume with resumeRunId = paused.runId()
        TetraLeaderboardEntry duplicateEntry2 = validEntry(ID_2, "osk", "CA", 22000.0, 120.0, "22000", "0", "0");
        TetraLeaderboardEntry entry3 = validEntry(ID_3, "czsmall0402", "TW", 21500.0, 140.0, "21500", "0", "0");

        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), eq("22000:0:0"), eq(paused.sessionId())))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of(duplicateEntry2, entry3)),
                        null,
                        "{\"success\":true,\"page\":2}"
                ));
        when(tetraChannelClient.fetchLeagueLeaderboardPage(eq(2), eq("21500:0:0"), eq(paused.sessionId())))
                .thenReturn(new TetraChannelResponse<>(
                        new TetraLeaderboardData(List.of()),
                        null,
                        "{\"success\":true,\"page\":3}"
                ));

        LeaderboardExtractionService resumedService =
                createService(rawDir, processedDir, paused.runId(), "20261020-resume", 2, 0, 1, 2);

        ExtractionCheckpoint completed = resumedService.runPhase1Leaderboard();
        assertThat(completed.status()).isEqualTo("completed");
        assertThat(completed.pagesCompleted()).isEqualTo(2);
        assertThat(completed.counters().uniquePlayers()).isEqualTo(3);
        assertThat(completed.counters().duplicatesSkipped()).isEqualTo(1);
    }

    private LeaderboardExtractionService createService(
            Path rawDir,
            Path processedDir,
            String resumeRunId,
            String datasetVersion,
            int batchSize,
            int maxPages,
            int maxRetries,
            int maxConsecutiveErrors
    ) {
        AppProperties props = new AppProperties(
                new AppProperties.CorsProperties(List.of("http://localhost:5173")),
                new AppProperties.TetraChannelProperties("https://ch.tetr.io/api", 5000L, 1100L, 3000L, "test-agent"),
                new AppProperties.CacheProperties(300L),
                new AppProperties.ExtractionProperties(
                        true,
                        List.of("leaderboard_league"),
                        batchSize,
                        maxPages,
                        maxRetries,
                        maxConsecutiveErrors,
                        rawDir.toString(),
                        processedDir.toString(),
                        resumeRunId,
                        datasetVersion
                )
        );
        return new LeaderboardExtractionService(
                tetraChannelClient,
                new PlayerMapper(),
                new CacheWindowCalculator(300L),
                playerPersistenceService,
                mongoTemplate,
                objectMapper,
                props,
                fixedClock
        );
    }

    private static TetraLeaderboardEntry validEntry(
            String id,
            String username,
            String country,
            Double tr,
            Double apm,
            String pri,
            String sec,
            String ter
    ) {
        return new TetraLeaderboardEntry(
                id,
                username,
                country,
                50000.0,
                10000.0,
                100.0,
                200,
                120,
                new TetraLeagueSummaryData(
                        200, 120, tr, 2300.0, 62.0, 98.5, "x", "x", apm, 2.5, 310.0, 10, 0.01, false
                ),
                new TetraPrisecter(new BigDecimal(pri), new BigDecimal(sec), new BigDecimal(ter))
        );
    }
}
