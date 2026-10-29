package com.tetrio.analytics.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tetrio.analytics.config.AppProperties;
import com.tetrio.analytics.extraction.ExtractionCheckpoint.Counters;
import com.tetrio.analytics.extraction.ExtractionManifest.ExtractionWindow;
import com.tetrio.analytics.extraction.ExtractionManifest.FieldQualityStats;
import com.tetrio.analytics.extraction.ExtractionManifest.ManifestCounts;
import com.tetrio.analytics.extraction.ExtractionManifest.SourceMetadata;
import com.tetrio.analytics.integration.tetrachannel.TetraChannelClient;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelCache;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraChannelResponse;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraGeneralStatsData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeaderboardEntry;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraLeagueSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraRecordSummaryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraSeasonHistoryData;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraSeasonHistoryEntry;
import com.tetrio.analytics.integration.tetrachannel.dto.TetraUserData;
import com.tetrio.analytics.player.PlayerMapper;
import com.tetrio.analytics.player.dto.LeagueSummaryDto;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator;
import com.tetrio.analytics.player.persistence.CacheWindowCalculator.EffectiveCacheWindow;
import com.tetrio.analytics.player.persistence.PlayerDocument;
import com.tetrio.analytics.player.persistence.PlayerPersistenceService;
import com.tetrio.analytics.player.persistence.RecordDocument;
import com.tetrio.analytics.player.persistence.SnapshotDocument;
import com.tetrio.analytics.problem.ApiProblemException;
import com.tetrio.analytics.problem.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardExtractionService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardExtractionService.class);
    public static final String PHASE_LEADERBOARD_LEAGUE = "leaderboard_league";
    public static final String PHASE_PLAYER_ENRICHMENT = "player_enrichment";
    public static final String PHASE_SEASON_HISTORY = "season_history";
    public static final String DEFAULT_HISTORY_SEASON = "1";
    public static final String FEATURE_SCHEMA_VERSION = "tr-cross-v1";
    private static final String SEEN_PLAYER_IDS_FILENAME = "seen_player_ids.txt";
    private static final Pattern MONGO_ID_PATTERN = Pattern.compile("^[0-9a-f]{24}$");
    private static final DateTimeFormatter RUN_ID_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final TetraChannelClient tetraChannelClient;
    private final PlayerMapper playerMapper;
    private final CacheWindowCalculator cacheWindowCalculator;
    private final PlayerPersistenceService playerPersistenceService;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final Clock clock;

    public LeaderboardExtractionService(
            TetraChannelClient tetraChannelClient,
            PlayerMapper playerMapper,
            CacheWindowCalculator cacheWindowCalculator,
            PlayerPersistenceService playerPersistenceService,
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper,
            AppProperties properties,
            Clock clock
    ) {
        this.tetraChannelClient = tetraChannelClient;
        this.playerMapper = playerMapper;
        this.cacheWindowCalculator = cacheWindowCalculator;
        this.playerPersistenceService = playerPersistenceService;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public ExtractionCheckpoint runPhase1Leaderboard() throws IOException {
        AppProperties.ExtractionProperties extProps = properties.extraction();
        Instant now = Instant.now(clock);

        String runId;
        String sessionId;
        String lastCursor;
        int pagesCompleted;
        int requestsSent;
        int playersSeen;
        int uniquePlayers;
        int duplicatesSkipped;
        int cachedSkipped;
        int rejectedRecords;
        Map<String, Integer> httpErrorsByStatus = new LinkedHashMap<>();
        Instant startedAt;
        Long upstreamRankedCountAtStart;
        String datasetVersion;

        Set<String> seenPlayerIds = new LinkedHashSet<>();

        if (extProps.resumeRunId() != null && !extProps.resumeRunId().isBlank()) {
            runId = extProps.resumeRunId().trim();
            Path rawRunDir = resolveRawRunDir(runId);
            Path checkpointFile = rawRunDir.resolve("checkpoint.json");
            if (!Files.exists(checkpointFile)) {
                throw new IllegalStateException("No existe checkpoint.json para reanudar runId=" + runId);
            }
            ExtractionCheckpoint existing = objectMapper.readValue(checkpointFile.toFile(), ExtractionCheckpoint.class);
            sessionId = existing.sessionId();
            lastCursor = existing.lastCursor();
            pagesCompleted = existing.pagesCompleted();
            Counters c = existing.counters();
            requestsSent = c.requestsSent();
            playersSeen = c.playersSeen();
            uniquePlayers = c.uniquePlayers();
            duplicatesSkipped = c.duplicatesSkipped();
            cachedSkipped = c.cachedSkipped();
            rejectedRecords = c.rejectedRecords();
            if (c.httpErrorsByStatus() != null) {
                httpErrorsByStatus.putAll(c.httpErrorsByStatus());
            }
            startedAt = existing.startedAt() != null ? existing.startedAt() : now;
            upstreamRankedCountAtStart = existing.upstreamRankedCountAtStart();
            datasetVersion = (existing.datasetVersion() != null && !existing.datasetVersion().isBlank())
                    ? existing.datasetVersion()
                    : resolveDatasetVersion(extProps, startedAt, runId);

            Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
            if (Files.exists(seenFile)) {
                for (String line : Files.readAllLines(seenFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        seenPlayerIds.add(trimmed);
                    }
                }
            }
        } else {
            startedAt = now;
            runId = RUN_ID_FMT.format(startedAt) + "-league";
            sessionId = UUID.randomUUID().toString();
            lastCursor = null;
            pagesCompleted = 0;
            requestsSent = 0;
            playersSeen = 0;
            uniquePlayers = 0;
            duplicatesSkipped = 0;
            cachedSkipped = 0;
            rejectedRecords = 0;
            datasetVersion = resolveDatasetVersion(extProps, startedAt, runId);
            upstreamRankedCountAtStart = fetchRankedCountSafe(sessionId);
        }

        Path rawRunDir = resolveRawRunDir(runId);
        Path processedDir = resolveProcessedDir(datasetVersion);
        Files.createDirectories(rawRunDir);
        Files.createDirectories(processedDir);

        Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
        Path playersJsonlFile = processedDir.resolve("players_league.jsonl");
        Path rejectedJsonlFile = processedDir.resolve("rejected.jsonl");

        int batchSize = extProps.batchSize();
        int maxPages = extProps.maxPages();
        int maxRetries = extProps.maxRetries();
        int maxConsecutiveErrors = extProps.maxConsecutiveErrors();
        int consecutiveErrors = 0;

        while (maxPages <= 0 || pagesCompleted < maxPages) {
            TetraChannelResponse<TetraLeaderboardData> pageResponse = null;
            boolean pageSucceeded = false;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                requestsSent++;
                try {
                    pageResponse = tetraChannelClient.fetchLeagueLeaderboardPage(batchSize, lastCursor, sessionId);
                    pageSucceeded = true;
                    consecutiveErrors = 0;
                    break;
                } catch (ApiProblemException ex) {
                    consecutiveErrors++;
                    String statusKey = String.valueOf(ex.errorCode().httpStatus().value());
                    httpErrorsByStatus.merge(statusKey, 1, Integer::sum);
                    log.warn(
                            "Error en página {} (intento {}/{}) para runId={}: {} ({})",
                            pagesCompleted + 1,
                            attempt + 1,
                            maxRetries + 1,
                            runId,
                            ex.errorCode(),
                             statusKey
                    );
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        break;
                    }
                }
            }

            if (!pageSucceeded || pageResponse == null) {
                ExtractionCheckpoint pausedCheckpoint = buildCheckpoint(
                        runId,
                        "paused",
                        sessionId,
                        lastCursor,
                        pagesCompleted,
                        requestsSent,
                        playersSeen,
                        uniquePlayers,
                        duplicatesSkipped,
                        cachedSkipped,
                        rejectedRecords,
                        httpErrorsByStatus,
                        startedAt,
                        Instant.now(clock),
                        upstreamRankedCountAtStart,
                        datasetVersion
                );
                writeCheckpointAtomically(rawRunDir, pausedCheckpoint);
                saveRunDocumentSafe(pausedCheckpoint, null, rawRunDir);
                return pausedCheckpoint;
            }

            List<TetraLeaderboardEntry> entries = pageResponse.data() != null && pageResponse.data().entries() != null
                    ? pageResponse.data().entries()
                    : List.of();

            if (entries.isEmpty()) {
                break;
            }

            int nextPageNumber = pagesCompleted + 1;
            writeRawPageFile(rawRunDir, nextPageNumber, pageResponse);

            Instant pageCapturedAt = Instant.now(clock);
            TetraChannelCache pageCache = pageResponse.cache();

            for (TetraLeaderboardEntry entry : entries) {
                playersSeen++;
                String rawId = entry != null ? entry.id() : null;
                String normalizedId = rawId != null ? rawId.trim().toLowerCase(Locale.ROOT) : null;

                if (normalizedId != null && seenPlayerIds.contains(normalizedId)) {
                    duplicatesSkipped++;
                    continue;
                }

                RejectedRecord rejection = validateLeaderboardEntry(runId, entry, pageCapturedAt);
                if (rejection != null) {
                    rejectedRecords++;
                    appendJsonLine(rejectedJsonlFile, rejection);
                    continue;
                }

                seenPlayerIds.add(normalizedId);
                uniquePlayers++;
                Files.writeString(
                        seenFile,
                        normalizedId + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                TetraUserData userData = entry.toUserData();
                LeagueSummaryDto leagueDto = playerMapper.toLeagueSummary(entry.league());

                ProcessedPlayerRecord processedRecord = toProcessedRecord(
                        userData,
                        leagueDto,
                        runId,
                        pageCapturedAt
                );
                appendJsonLine(playersJsonlFile, processedRecord);

                persistExtractedPlayerSafe(userData, leagueDto, pageCache, pageCapturedAt, sessionId);
            }

            pagesCompleted = nextPageNumber;
            TetraLeaderboardEntry lastEntry = entries.getLast();
            String nextCursor = (lastEntry != null && lastEntry.p() != null && lastEntry.p().isValid())
                    ? lastEntry.p().toCursorString()
                    : null;

            boolean reachedEnd = nextCursor == null
                    || nextCursor.equals(lastCursor)
                    || entries.size() < batchSize;

            lastCursor = nextCursor != null ? nextCursor : lastCursor;

            ExtractionCheckpoint runningCheckpoint = buildCheckpoint(
                    runId,
                    "running",
                    sessionId,
                    lastCursor,
                    pagesCompleted,
                    requestsSent,
                    playersSeen,
                    uniquePlayers,
                    duplicatesSkipped,
                    cachedSkipped,
                    rejectedRecords,
                    httpErrorsByStatus,
                    startedAt,
                    Instant.now(clock),
                    upstreamRankedCountAtStart,
                    datasetVersion
            );
            writeCheckpointAtomically(rawRunDir, runningCheckpoint);
            saveRunDocumentSafe(runningCheckpoint, null, rawRunDir);

            if (reachedEnd) {
                break;
            }
        }

        Instant finishedAt = Instant.now(clock);
        ExtractionCheckpoint completedCheckpoint = buildCheckpoint(
                runId,
                "completed",
                sessionId,
                lastCursor,
                pagesCompleted,
                requestsSent,
                playersSeen,
                uniquePlayers,
                duplicatesSkipped,
                cachedSkipped,
                rejectedRecords,
                httpErrorsByStatus,
                startedAt,
                finishedAt,
                upstreamRankedCountAtStart,
                datasetVersion
        );
        writeCheckpointAtomically(rawRunDir, completedCheckpoint);
        writeManifest(processedDir, playersJsonlFile, completedCheckpoint, startedAt, finishedAt, batchSize, "GET /users/by/league");
        saveRunDocumentSafe(completedCheckpoint, finishedAt, rawRunDir);

        return completedCheckpoint;
    }

    public ExtractionCheckpoint runPhase2PlayerEnrichment() throws IOException {
        AppProperties.ExtractionProperties extProps = properties.extraction();
        Instant now = Instant.now(clock);

        String runId;
        String sessionId;
        String lastCursor;
        int pagesCompleted;
        int requestsSent;
        int playersSeen;
        int uniquePlayers;
        int duplicatesSkipped;
        int cachedSkipped;
        int rejectedRecords;
        Map<String, Integer> httpErrorsByStatus = new LinkedHashMap<>();
        Instant startedAt;
        String datasetVersion;

        Set<String> seenPlayerIds = new LinkedHashSet<>();

        if (extProps.resumeRunId() != null && !extProps.resumeRunId().isBlank()) {
            runId = extProps.resumeRunId().trim();
            Path rawRunDir = resolveRawRunDir(runId);
            Path checkpointFile = rawRunDir.resolve("checkpoint.json");
            if (!Files.exists(checkpointFile)) {
                throw new IllegalStateException("No existe checkpoint.json para reanudar runId=" + runId);
            }
            ExtractionCheckpoint existing = objectMapper.readValue(checkpointFile.toFile(), ExtractionCheckpoint.class);
            sessionId = existing.sessionId();
            lastCursor = existing.lastCursor();
            pagesCompleted = existing.pagesCompleted();
            Counters c = existing.counters();
            requestsSent = c.requestsSent();
            playersSeen = c.playersSeen();
            uniquePlayers = c.uniquePlayers();
            duplicatesSkipped = c.duplicatesSkipped();
            cachedSkipped = c.cachedSkipped();
            rejectedRecords = c.rejectedRecords();
            if (c.httpErrorsByStatus() != null) {
                httpErrorsByStatus.putAll(c.httpErrorsByStatus());
            }
            startedAt = existing.startedAt() != null ? existing.startedAt() : now;
            datasetVersion = (existing.datasetVersion() != null && !existing.datasetVersion().isBlank())
                    ? existing.datasetVersion()
                    : resolveDatasetVersion(extProps, startedAt, runId);

            Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
            if (Files.exists(seenFile)) {
                for (String line : Files.readAllLines(seenFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        seenPlayerIds.add(trimmed);
                    }
                }
            }
        } else {
            startedAt = now;
            runId = RUN_ID_FMT.format(startedAt) + "-enrichment";
            sessionId = UUID.randomUUID().toString();
            lastCursor = null;
            pagesCompleted = 0;
            requestsSent = 0;
            playersSeen = 0;
            uniquePlayers = 0;
            duplicatesSkipped = 0;
            cachedSkipped = 0;
            rejectedRecords = 0;
            datasetVersion = resolveDatasetVersion(extProps, startedAt, runId);
        }

        Path rawRunDir = resolveRawRunDir(runId);
        Path processedDir = resolveProcessedDir(datasetVersion);
        Files.createDirectories(rawRunDir);
        Files.createDirectories(processedDir);

        Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
        Path enrichedJsonlFile = processedDir.resolve("player_records.jsonl");
        Path rejectedJsonlFile = processedDir.resolve("rejected.jsonl");

        int batchSize = extProps.batchSize();
        int maxPages = extProps.maxPages();
        int maxRetries = extProps.maxRetries();
        int maxConsecutiveErrors = extProps.maxConsecutiveErrors();
        int consecutiveErrors = 0;

        List<String> candidatePlayerIds = loadCandidatePlayerIds(processedDir, maxPages > 0 ? maxPages * batchSize : 0);

        List<String> remainingIds = new ArrayList<>();
        for (String id : candidatePlayerIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String normalizedId = id.trim().toLowerCase(Locale.ROOT);
            if (seenPlayerIds.contains(normalizedId)) {
                duplicatesSkipped++;
                continue;
            }
            remainingIds.add(normalizedId);
        }

        int cursorIdx = 0;
        while (cursorIdx < remainingIds.size() && (maxPages <= 0 || pagesCompleted < maxPages)) {
            int batchEnd = Math.min(remainingIds.size(), cursorIdx + batchSize);
            List<String> batchIds = remainingIds.subList(cursorIdx, batchEnd);
            int nextBatchNumber = pagesCompleted + 1;
            Path rawBatchFile = rawRunDir.resolve(String.format(Locale.ROOT, "batch-%04d.jsonl", nextBatchNumber));

            for (String playerId : batchIds) {
                playersSeen++;
                Instant checkNow = Instant.now(clock);
                if (hasValidCachedRecords(playerId, checkNow)) {
                    cachedSkipped++;
                    seenPlayerIds.add(playerId);
                    Files.writeString(
                            seenFile,
                            playerId + System.lineSeparator(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                    lastCursor = playerId;
                    continue;
                }

                String playerSessionId = "enrich-" + UUID.randomUUID();
                TetraChannelResponse<TetraRecordSummaryData> resp40l = null;
                TetraChannelResponse<TetraRecordSummaryData> respBlitz = null;
                boolean userNotFound = false;
                boolean fetchSucceeded = false;

                for (int attempt = 0; attempt <= maxRetries; attempt++) {
                    try {
                        requestsSent++;
                        resp40l = tetraChannelClient.fetchRecordSummary(playerId, "40l", playerSessionId);
                        requestsSent++;
                        respBlitz = tetraChannelClient.fetchRecordSummary(playerId, "blitz", playerSessionId);
                        fetchSucceeded = true;
                        consecutiveErrors = 0;
                        break;
                    } catch (ApiProblemException ex) {
                        if (ex.errorCode() == ErrorCode.PLAYER_NOT_FOUND) {
                            userNotFound = true;
                            consecutiveErrors = 0;
                            break;
                        }
                        consecutiveErrors++;
                        String statusKey = String.valueOf(ex.errorCode().httpStatus().value());
                        httpErrorsByStatus.merge(statusKey, 1, Integer::sum);
                        if (consecutiveErrors >= maxConsecutiveErrors) {
                            break;
                        }
                    }
                }

                if (userNotFound) {
                    rejectedRecords++;
                    appendJsonLine(
                            rejectedJsonlFile,
                            new RejectedRecord(runId, playerId, "playerId", playerId, "USER_NOT_FOUND", Instant.now(clock))
                    );
                    seenPlayerIds.add(playerId);
                    Files.writeString(
                            seenFile,
                            playerId + System.lineSeparator(),
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                    lastCursor = playerId;
                    continue;
                }

                if (!fetchSucceeded || resp40l == null || respBlitz == null) {
                    ExtractionCheckpoint pausedCheckpoint = buildCheckpoint(
                            runId,
                            "paused",
                            PHASE_PLAYER_ENRICHMENT,
                            sessionId,
                            lastCursor,
                            pagesCompleted,
                            requestsSent,
                            playersSeen,
                            uniquePlayers,
                            duplicatesSkipped,
                            cachedSkipped,
                            rejectedRecords,
                            httpErrorsByStatus,
                            startedAt,
                            Instant.now(clock),
                            null,
                            datasetVersion
                    );
                    writeCheckpointAtomically(rawRunDir, pausedCheckpoint);
                    saveRunDocumentSafe(pausedCheckpoint, null, rawRunDir);
                    return pausedCheckpoint;
                }

                Instant syncedAt = Instant.now(clock);
                EffectiveCacheWindow window = cacheWindowCalculator.computeWindow(
                        playerId,
                        resp40l.cache(),
                        respBlitz.cache(),
                        syncedAt,
                        null
                );
                RecordDocument doc40l = playerMapper.toRecordDocument(
                        playerId,
                        "40l",
                        resp40l.data(),
                        resp40l.cache(),
                        syncedAt,
                        window.windowUntilAt(),
                        playerSessionId
                );
                RecordDocument docBlitz = playerMapper.toRecordDocument(
                        playerId,
                        "blitz",
                        respBlitz.data(),
                        respBlitz.cache(),
                        syncedAt,
                        window.windowUntilAt(),
                        playerSessionId
                );
                saveEnrichedRecordsSafe(List.of(doc40l, docBlitz));

                Map<String, Object> rawBatchEntry = Map.of(
                        "playerId", playerId,
                        "capturedAt", syncedAt.toString(),
                        "mode40l", doc40l,
                        "modeBlitz", docBlitz
                );
                appendJsonLine(rawBatchFile, rawBatchEntry);
                appendJsonLine(enrichedJsonlFile, rawBatchEntry);

                seenPlayerIds.add(playerId);
                uniquePlayers++;
                lastCursor = playerId;
                Files.writeString(
                        seenFile,
                        playerId + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }

            pagesCompleted = nextBatchNumber;
            cursorIdx = batchEnd;

            ExtractionCheckpoint runningCheckpoint = buildCheckpoint(
                    runId,
                    "running",
                    PHASE_PLAYER_ENRICHMENT,
                    sessionId,
                    lastCursor,
                    pagesCompleted,
                    requestsSent,
                    playersSeen,
                    uniquePlayers,
                    duplicatesSkipped,
                    cachedSkipped,
                    rejectedRecords,
                    httpErrorsByStatus,
                    startedAt,
                    Instant.now(clock),
                    null,
                    datasetVersion
            );
            writeCheckpointAtomically(rawRunDir, runningCheckpoint);
            saveRunDocumentSafe(runningCheckpoint, null, rawRunDir);
        }

        Instant finishedAt = Instant.now(clock);
        ExtractionCheckpoint completedCheckpoint = buildCheckpoint(
                runId,
                "completed",
                PHASE_PLAYER_ENRICHMENT,
                sessionId,
                lastCursor,
                pagesCompleted,
                requestsSent,
                playersSeen,
                uniquePlayers,
                duplicatesSkipped,
                cachedSkipped,
                rejectedRecords,
                httpErrorsByStatus,
                startedAt,
                finishedAt,
                null,
                datasetVersion
        );
        writeCheckpointAtomically(rawRunDir, completedCheckpoint);
        saveRunDocumentSafe(completedCheckpoint, finishedAt, rawRunDir);
        return completedCheckpoint;
    }

    public ExtractionCheckpoint runPhase3SeasonHistory() throws IOException {
        return runPhase3SeasonHistory(DEFAULT_HISTORY_SEASON);
    }

    public ExtractionCheckpoint runPhase3SeasonHistory(String season) throws IOException {
        String targetSeason = (season == null || season.isBlank()) ? DEFAULT_HISTORY_SEASON : season.trim();
        AppProperties.ExtractionProperties extProps = properties.extraction();
        Instant now = Instant.now(clock);

        String runId;
        String sessionId;
        String lastCursor;
        int pagesCompleted;
        int requestsSent;
        int playersSeen;
        int uniquePlayers;
        int duplicatesSkipped;
        int cachedSkipped;
        int rejectedRecords;
        Map<String, Integer> httpErrorsByStatus = new LinkedHashMap<>();
        Instant startedAt;
        String datasetVersion;

        Set<String> seenPlayerIds = new LinkedHashSet<>();

        if (extProps.resumeRunId() != null && !extProps.resumeRunId().isBlank()) {
            runId = extProps.resumeRunId().trim();
            Path rawRunDir = resolveRawRunDir(runId);
            Path checkpointFile = rawRunDir.resolve("checkpoint.json");
            if (!Files.exists(checkpointFile)) {
                throw new IllegalStateException("No existe checkpoint.json para reanudar runId=" + runId);
            }
            ExtractionCheckpoint existing = objectMapper.readValue(checkpointFile.toFile(), ExtractionCheckpoint.class);
            sessionId = existing.sessionId();
            lastCursor = existing.lastCursor();
            pagesCompleted = existing.pagesCompleted();
            Counters c = existing.counters();
            requestsSent = c.requestsSent();
            playersSeen = c.playersSeen();
            uniquePlayers = c.uniquePlayers();
            duplicatesSkipped = c.duplicatesSkipped();
            cachedSkipped = c.cachedSkipped();
            rejectedRecords = c.rejectedRecords();
            if (c.httpErrorsByStatus() != null) {
                httpErrorsByStatus.putAll(c.httpErrorsByStatus());
            }
            startedAt = existing.startedAt() != null ? existing.startedAt() : now;
            datasetVersion = (existing.datasetVersion() != null && !existing.datasetVersion().isBlank())
                    ? existing.datasetVersion()
                    : resolveDatasetVersion(extProps, startedAt, runId);

            Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
            if (Files.exists(seenFile)) {
                for (String line : Files.readAllLines(seenFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        seenPlayerIds.add(trimmed);
                    }
                }
            }
        } else {
            startedAt = now;
            runId = RUN_ID_FMT.format(startedAt) + "-season-" + targetSeason;
            sessionId = UUID.randomUUID().toString();
            lastCursor = null;
            pagesCompleted = 0;
            requestsSent = 0;
            playersSeen = 0;
            uniquePlayers = 0;
            duplicatesSkipped = 0;
            cachedSkipped = 0;
            rejectedRecords = 0;
            datasetVersion = resolveDatasetVersion(extProps, startedAt, runId);
        }

        Path rawRunDir = resolveRawRunDir(runId);
        Path processedDir = resolveProcessedDir(datasetVersion);
        Files.createDirectories(rawRunDir);
        Files.createDirectories(processedDir);

        Path seenFile = rawRunDir.resolve(SEEN_PLAYER_IDS_FILENAME);
        Path seasonJsonlFile = processedDir.resolve("season_league.jsonl");
        Path rejectedJsonlFile = processedDir.resolve("rejected.jsonl");

        int batchSize = extProps.batchSize();
        int maxPages = extProps.maxPages();
        int maxRetries = extProps.maxRetries();
        int maxConsecutiveErrors = extProps.maxConsecutiveErrors();
        int consecutiveErrors = 0;

        while (maxPages <= 0 || pagesCompleted < maxPages) {
            TetraChannelResponse<TetraSeasonHistoryData> pageResponse = null;
            boolean pageSucceeded = false;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                requestsSent++;
                try {
                    pageResponse = tetraChannelClient.fetchSeasonLeaderboardPage(targetSeason, batchSize, lastCursor, sessionId);
                    pageSucceeded = true;
                    consecutiveErrors = 0;
                    break;
                } catch (ApiProblemException ex) {
                    consecutiveErrors++;
                    String statusKey = String.valueOf(ex.errorCode().httpStatus().value());
                    httpErrorsByStatus.merge(statusKey, 1, Integer::sum);
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        break;
                    }
                }
            }

            if (!pageSucceeded || pageResponse == null) {
                ExtractionCheckpoint pausedCheckpoint = buildCheckpoint(
                        runId,
                        "paused",
                        PHASE_SEASON_HISTORY,
                        sessionId,
                        lastCursor,
                        pagesCompleted,
                        requestsSent,
                        playersSeen,
                        uniquePlayers,
                        duplicatesSkipped,
                        cachedSkipped,
                        rejectedRecords,
                        httpErrorsByStatus,
                        startedAt,
                        Instant.now(clock),
                        null,
                        datasetVersion
                );
                writeCheckpointAtomically(rawRunDir, pausedCheckpoint);
                saveRunDocumentSafe(pausedCheckpoint, null, rawRunDir);
                return pausedCheckpoint;
            }

            List<TetraSeasonHistoryEntry> entries = pageResponse.data() != null && pageResponse.data().entries() != null
                    ? pageResponse.data().entries()
                    : List.of();

            if (entries.isEmpty()) {
                break;
            }

            int nextPageNumber = pagesCompleted + 1;
            writeRawPageFile(rawRunDir, nextPageNumber, pageResponse);

            Instant pageCapturedAt = Instant.now(clock);

            for (TetraSeasonHistoryEntry seasonEntry : entries) {
                playersSeen++;
                TetraLeaderboardEntry entry = seasonEntry != null ? seasonEntry.toLeaderboardEntry() : null;
                String rawId = entry != null ? entry.id() : null;
                String normalizedId = rawId != null ? rawId.trim().toLowerCase(Locale.ROOT) : null;

                if (normalizedId != null && seenPlayerIds.contains(normalizedId)) {
                    duplicatesSkipped++;
                    continue;
                }

                RejectedRecord rejection = validateLeaderboardEntry(runId, entry, pageCapturedAt);
                if (rejection != null) {
                    rejectedRecords++;
                    appendJsonLine(rejectedJsonlFile, rejection);
                    continue;
                }

                seenPlayerIds.add(normalizedId);
                uniquePlayers++;
                Files.writeString(
                        seenFile,
                        normalizedId + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );

                TetraUserData userData = entry.toUserData();
                LeagueSummaryDto leagueDto = playerMapper.toLeagueSummary(entry.league());
                ProcessedPlayerRecord processedRecord = toProcessedRecord(userData, leagueDto, runId, pageCapturedAt);
                appendJsonLine(seasonJsonlFile, processedRecord);
            }

            pagesCompleted = nextPageNumber;
            TetraSeasonHistoryEntry lastEntry = entries.getLast();
            String nextCursor = (lastEntry != null && lastEntry.p() != null && lastEntry.p().isValid())
                    ? lastEntry.p().toCursorString()
                    : null;

            boolean reachedEnd = nextCursor == null
                    || nextCursor.equals(lastCursor)
                    || entries.size() < batchSize;

            lastCursor = nextCursor != null ? nextCursor : lastCursor;

            ExtractionCheckpoint runningCheckpoint = buildCheckpoint(
                    runId,
                    "running",
                    PHASE_SEASON_HISTORY,
                    sessionId,
                    lastCursor,
                    pagesCompleted,
                    requestsSent,
                    playersSeen,
                    uniquePlayers,
                    duplicatesSkipped,
                    cachedSkipped,
                    rejectedRecords,
                    httpErrorsByStatus,
                    startedAt,
                    Instant.now(clock),
                    null,
                    datasetVersion
            );
            writeCheckpointAtomically(rawRunDir, runningCheckpoint);
            saveRunDocumentSafe(runningCheckpoint, null, rawRunDir);

            if (reachedEnd) {
                break;
            }
        }

        Instant finishedAt = Instant.now(clock);
        ExtractionCheckpoint completedCheckpoint = buildCheckpoint(
                runId,
                "completed",
                PHASE_SEASON_HISTORY,
                sessionId,
                lastCursor,
                pagesCompleted,
                requestsSent,
                playersSeen,
                uniquePlayers,
                duplicatesSkipped,
                cachedSkipped,
                rejectedRecords,
                httpErrorsByStatus,
                startedAt,
                finishedAt,
                null,
                datasetVersion
        );
        writeCheckpointAtomically(rawRunDir, completedCheckpoint);
        writeManifest(
                processedDir,
                seasonJsonlFile,
                completedCheckpoint,
                startedAt,
                finishedAt,
                batchSize,
                "GET /users/history/league/" + targetSeason
        );
        saveRunDocumentSafe(completedCheckpoint, finishedAt, rawRunDir);

        return completedCheckpoint;
    }

    private List<String> loadCandidatePlayerIds(Path processedDir, int limit) throws IOException {
        Path playersJsonlFile = processedDir.resolve("players_league.jsonl");
        if (Files.exists(playersJsonlFile)) {
            List<String> ids = new ArrayList<>();
            for (String line : Files.readAllLines(playersJsonlFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                ProcessedPlayerRecord rec = objectMapper.readValue(line, ProcessedPlayerRecord.class);
                if (rec != null && rec.playerId() != null && !rec.playerId().isBlank()) {
                    ids.add(rec.playerId());
                    if (limit > 0 && ids.size() >= limit) {
                        break;
                    }
                }
            }
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        try {
            return playerPersistenceService.findAllExternalPlayerIds(limit);
        } catch (DataAccessException ex) {
            log.warn("No se pudo obtener lista de playerIds desde MongoDB para Fase 2: {}", ex.getMessage());
            return List.of();
        }
    }

    private boolean hasValidCachedRecords(String playerId, Instant now) {
        try {
            List<RecordDocument> stored = playerPersistenceService.findRecordsByPlayerId(playerId);
            if (stored == null || stored.isEmpty()) {
                return false;
            }
            RecordDocument doc40l = null;
            RecordDocument docBlitz = null;
            for (RecordDocument doc : stored) {
                if (doc == null) {
                    continue;
                }
                if ("40l".equals(doc.mode())) {
                    doc40l = doc;
                } else if ("blitz".equals(doc.mode())) {
                    docBlitz = doc;
                }
            }
            return doc40l != null
                    && doc40l.cacheValidUntil() != null
                    && now.isBefore(doc40l.cacheValidUntil())
                    && docBlitz != null
                    && docBlitz.cacheValidUntil() != null
                    && now.isBefore(docBlitz.cacheValidUntil());
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private void saveEnrichedRecordsSafe(List<RecordDocument> records) {
        try {
            playerPersistenceService.savePlayerRecords(records);
        } catch (DataAccessException ex) {
            log.warn("No se pudo persistir récords enriquecidos en MongoDB: {}", ex.getMessage());
        }
    }

    private RejectedRecord validateLeaderboardEntry(
            String runId,
            TetraLeaderboardEntry entry,
            Instant timestamp
    ) {
        if (entry == null) {
            return new RejectedRecord(runId, null, "entry", "null", "NULL_ENTRY", timestamp);
        }
        String id = entry.id() != null ? entry.id().trim().toLowerCase(Locale.ROOT) : null;
        if (id == null || !MONGO_ID_PATTERN.matcher(id).matches()) {
            return new RejectedRecord(runId, entry.id(), "_id", String.valueOf(entry.id()), "INVALID_PLAYER_ID", timestamp);
        }
        if (entry.username() == null || entry.username().isBlank()) {
            return new RejectedRecord(runId, id, "username", String.valueOf(entry.username()), "BLANK_USERNAME", timestamp);
        }
        TetraLeagueSummaryData league = entry.league();
        if (league == null) {
            return new RejectedRecord(runId, id, "league", "null", "MISSING_LEAGUE_OBJECT", timestamp);
        }
        if (!isValidMetricOrSentinel(league.tr())) {
            return new RejectedRecord(runId, id, "league.tr", String.valueOf(league.tr()), "INVALID_METRIC_VALUE", timestamp);
        }
        if (!isValidMetricOrSentinel(league.apm())) {
            return new RejectedRecord(runId, id, "league.apm", String.valueOf(league.apm()), "INVALID_METRIC_VALUE", timestamp);
        }
        if (!isValidMetricOrSentinel(league.pps())) {
            return new RejectedRecord(runId, id, "league.pps", String.valueOf(league.pps()), "INVALID_METRIC_VALUE", timestamp);
        }
        if (!isValidMetricOrSentinel(league.vs())) {
            return new RejectedRecord(runId, id, "league.vs", String.valueOf(league.vs()), "INVALID_METRIC_VALUE", timestamp);
        }
        if (league.gamesPlayed() != null && league.gamesPlayed() < -1) {
            return new RejectedRecord(runId, id, "league.gamesplayed", String.valueOf(league.gamesPlayed()), "INVALID_GAMES_PLAYED", timestamp);
        }
        return null;
    }

    private static boolean isValidMetricOrSentinel(Double val) {
        if (val == null) {
            return true;
        }
        if (!Double.isFinite(val)) {
            return false;
        }
        return val >= 0.0d || val == -1.0d;
    }

    private static ProcessedPlayerRecord toProcessedRecord(
            TetraUserData userData,
            LeagueSummaryDto league,
            String runId,
            Instant capturedAt
    ) {
        String normalizedCountry = (userData.country() == null || userData.country().isBlank())
                ? null
                : userData.country().trim().toUpperCase(Locale.ROOT);
        Double normalizedXp = (userData.xp() != null && Double.isFinite(userData.xp()) && userData.xp() >= 0.0d)
                ? userData.xp()
                : null;
        Double normalizedGametime = (userData.gametime() != null && Double.isFinite(userData.gametime()) && userData.gametime() >= 0.0d)
                ? userData.gametime()
                : null;

        return new ProcessedPlayerRecord(
                userData.id().trim().toLowerCase(Locale.ROOT),
                userData.username().trim().toLowerCase(Locale.ROOT),
                normalizedCountry,
                normalizedXp,
                normalizedGametime,
                league.gamesPlayed(),
                league.gamesWon(),
                league.ranked(),
                league.tr(),
                league.glicko(),
                league.rd(),
                league.gxe(),
                league.rank(),
                league.bestRank(),
                league.apm(),
                league.pps(),
                league.vs(),
                league.standing(),
                league.percentile(),
                league.decaying(),
                runId,
                capturedAt
        );
    }

    private void persistExtractedPlayerSafe(
            TetraUserData userData,
            LeagueSummaryDto leagueDto,
            TetraChannelCache pageCache,
            Instant capturedAt,
            String sessionId
    ) {
        try {
            String playerId = userData.id().trim().toLowerCase(Locale.ROOT);
            PlayerDocument existing = playerPersistenceService.findByExternalPlayerId(playerId).orElse(null);
            EffectiveCacheWindow window = cacheWindowCalculator.computeWindow(
                    playerId,
                    pageCache,
                    pageCache,
                    capturedAt,
                    existing
            );
            PlayerDocument playerDoc = playerMapper.toPlayerDocument(userData, leagueDto, window, capturedAt);
            SnapshotDocument snapshotDoc = playerMapper.toSnapshotDocument(
                    userData,
                    leagueDto,
                    window,
                    pageCache,
                    pageCache,
                    capturedAt,
                    sessionId
            );
            playerPersistenceService.saveSyncedPlayerAndSnapshot(playerDoc, snapshotDoc);
        } catch (DataAccessException ex) {
            log.warn("No se pudo persistir en MongoDB el jugador extraído '{}': {}", userData.id(), ex.getMessage());
        }
    }

    private Long fetchRankedCountSafe(String sessionId) {
        try {
            TetraChannelResponse<TetraGeneralStatsData> stats = tetraChannelClient.fetchGeneralStats(sessionId);
            return stats.data() != null ? stats.data().rankedCount() : null;
        } catch (ApiProblemException ex) {
            log.warn("No se pudo obtener rankedcount inicial desde /general/stats: {}", ex.errorCode());
            return null;
        }
    }

    private void writeRawPageFile(
            Path rawRunDir,
            int pageNumber,
            TetraChannelResponse<?> pageResponse
    ) throws IOException {
        String fileName = String.format(Locale.ROOT, "page-%04d.json", pageNumber);
        Path pagePath = rawRunDir.resolve(fileName);
        String content = (pageResponse.rawJson() != null && !pageResponse.rawJson().isBlank())
                ? pageResponse.rawJson()
                : objectMapper.writeValueAsString(pageResponse.data());
        Files.writeString(
                pagePath,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC
        );
    }

    private void writeCheckpointAtomically(Path rawRunDir, ExtractionCheckpoint checkpoint) throws IOException {
        Path tmpFile = rawRunDir.resolve("checkpoint.json.tmp");
        Path targetFile = rawRunDir.resolve("checkpoint.json");
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(checkpoint);
        Files.writeString(
                tmpFile,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC
        );
        try {
            Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicEx) {
            Files.move(tmpFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeManifest(
            Path processedDir,
            Path playersJsonlFile,
            ExtractionCheckpoint checkpoint,
            Instant startedAt,
            Instant finishedAt,
            int batchSize,
            String endpointPath
    ) throws IOException {
        int totalExported = 0;
        int trNulls = 0;
        int apmNulls = 0;
        int ppsNulls = 0;
        int vsNulls = 0;
        int xpNulls = 0;
        int gametimeNulls = 0;

        if (Files.exists(playersJsonlFile)) {
            for (String line : Files.readAllLines(playersJsonlFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                ProcessedPlayerRecord rec = objectMapper.readValue(line, ProcessedPlayerRecord.class);
                totalExported++;
                if (rec.tr() == null) {
                    trNulls++;
                }
                if (rec.apm() == null) {
                    apmNulls++;
                }
                if (rec.pps() == null) {
                    ppsNulls++;
                }
                if (rec.vs() == null) {
                    vsNulls++;
                }
                if (rec.xp() == null) {
                    xpNulls++;
                }
                if (rec.gametime() == null) {
                    gametimeNulls++;
                }
            }
        }

        Map<String, FieldQualityStats> fields = new LinkedHashMap<>();
        fields.put("tr", buildFieldStats("float", true, trNulls, totalExported));
        fields.put("apm", buildFieldStats("float", true, apmNulls, totalExported));
        fields.put("pps", buildFieldStats("float", true, ppsNulls, totalExported));
        fields.put("vs", buildFieldStats("float", true, vsNulls, totalExported));
        fields.put("gamesPlayed", buildFieldStats("int", false, 0, totalExported));
        fields.put("xp", buildFieldStats("float", true, xpNulls, totalExported));
        fields.put("gametime", buildFieldStats("float", true, gametimeNulls, totalExported));

        Counters c = checkpoint.counters();
        ManifestCounts counts = new ManifestCounts(
                checkpoint.upstreamRankedCountAtStart(),
                checkpoint.pagesCompleted(),
                c.playersSeen(),
                totalExported,
                c.duplicatesSkipped(),
                c.cachedSkipped(),
                c.rejectedRecords(),
                c.httpErrorsByStatus()
        );

        long durationSeconds = Math.max(0L, Duration.between(startedAt, finishedAt).getSeconds());
        ExtractionManifest manifest = new ExtractionManifest(
                checkpoint.datasetVersion(),
                checkpoint.runId(),
                finishedAt,
                new ExtractionWindow(startedAt, finishedAt, durationSeconds),
                List.of(new SourceMetadata(
                        endpointPath,
                        properties.tetraChannel().baseUrl(),
                        batchSize,
                        true
                )),
                FEATURE_SCHEMA_VERSION,
                List.of(
                        "sentinel_-1_to_null",
                        "rank_z_to_null_and_ranked_false",
                        "username_trim_lowercase",
                        "deduplicate_by_external_player_id"
                ),
                counts,
                fields
        );

        Path manifestFile = processedDir.resolve("manifest.json");
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        Files.writeString(
                manifestFile,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC
        );
    }

    private static FieldQualityStats buildFieldStats(String type, boolean nullable, int nullCount, int total) {
        double rate = total <= 0 ? 0.0d : (double) nullCount / (double) total;
        return new FieldQualityStats(type, nullable, nullCount, rate);
    }

    private void appendJsonLine(Path file, Object record) throws IOException {
        String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
        Files.writeString(
                file,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private void saveRunDocumentSafe(
            ExtractionCheckpoint checkpoint,
            Instant finishedAt,
            Path rawRunDir
    ) {
        try {
            AppProperties.ExtractionProperties extProps = properties.extraction();
            ExtractionRunDocument doc = new ExtractionRunDocument(
                    checkpoint.runId(),
                    checkpoint.startedAt(),
                    checkpoint.updatedAt(),
                    finishedAt,
                    checkpoint.status(),
                    checkpoint.phase(),
                    checkpoint.lastCursor(),
                    checkpoint.sessionId(),
                    new ExtractionRunDocument.RunParameters(
                            extProps.phases(),
                            properties.tetraChannel().minIntervalMs(),
                            extProps.batchSize(),
                            extProps.maxPages()
                    ),
                    checkpoint.counters(),
                    rawRunDir.toString(),
                    checkpoint.datasetVersion()
            );
            mongoTemplate.save(doc);
        } catch (DataAccessException ex) {
            log.warn("No se pudo actualizar extraction_runs en MongoDB para runId={}: {}", checkpoint.runId(), ex.getMessage());
        }
    }

    private static ExtractionCheckpoint buildCheckpoint(
            String runId,
            String status,
            String sessionId,
            String lastCursor,
            int pagesCompleted,
            int requestsSent,
            int playersSeen,
            int uniquePlayers,
            int duplicatesSkipped,
            int cachedSkipped,
            int rejectedRecords,
            Map<String, Integer> httpErrorsByStatus,
            Instant startedAt,
            Instant updatedAt,
            Long upstreamRankedCountAtStart,
            String datasetVersion
    ) {
        return buildCheckpoint(
                runId,
                status,
                PHASE_LEADERBOARD_LEAGUE,
                sessionId,
                lastCursor,
                pagesCompleted,
                requestsSent,
                playersSeen,
                uniquePlayers,
                duplicatesSkipped,
                cachedSkipped,
                rejectedRecords,
                httpErrorsByStatus,
                startedAt,
                updatedAt,
                upstreamRankedCountAtStart,
                datasetVersion
        );
    }

    private static ExtractionCheckpoint buildCheckpoint(
            String runId,
            String status,
            String phase,
            String sessionId,
            String lastCursor,
            int pagesCompleted,
            int requestsSent,
            int playersSeen,
            int uniquePlayers,
            int duplicatesSkipped,
            int cachedSkipped,
            int rejectedRecords,
            Map<String, Integer> httpErrorsByStatus,
            Instant startedAt,
            Instant updatedAt,
            Long upstreamRankedCountAtStart,
            String datasetVersion
    ) {
        return new ExtractionCheckpoint(
                runId,
                status,
                phase,
                sessionId,
                lastCursor,
                pagesCompleted,
                SEEN_PLAYER_IDS_FILENAME,
                new Counters(
                        requestsSent,
                        playersSeen,
                        uniquePlayers,
                        duplicatesSkipped,
                        cachedSkipped,
                        rejectedRecords,
                        Map.copyOf(httpErrorsByStatus)
                ),
                startedAt,
                updatedAt,
                upstreamRankedCountAtStart,
                datasetVersion
        );
    }

    private Path resolveRawRunDir(String runId) {
        return Path.of(properties.extraction().rawDir(), "tetra-channel", runId);
    }

    private Path resolveProcessedDir(String datasetVersion) {
        return Path.of(properties.extraction().processedDir(), datasetVersion);
    }

    private static String resolveDatasetVersion(
            AppProperties.ExtractionProperties extProps,
            Instant startedAt,
            String runId
    ) {
        if (extProps.datasetVersion() != null && !extProps.datasetVersion().isBlank()) {
            return extProps.datasetVersion().trim();
        }
        return DATE_FMT.format(startedAt) + "-" + runId;
    }
}
