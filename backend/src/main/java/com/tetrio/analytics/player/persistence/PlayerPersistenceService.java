package com.tetrio.analytics.player.persistence;

import com.tetrio.analytics.common.PageResponse;
import com.tetrio.analytics.ranking.dto.RankingMetric;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerPersistenceService {

    private final MongoTemplate mongoTemplate;

    public PlayerPersistenceService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<PlayerDocument> findByUsernameLower(String usernameLower) {
        if (usernameLower == null || usernameLower.isBlank()) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("usernameLower").is(usernameLower))
                .with(Sort.by(Sort.Direction.DESC, "lastSyncedAt"))
                .limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, PlayerDocument.class));
    }

    public Optional<PlayerDocument> findByExternalPlayerId(String externalPlayerId) {
        if (externalPlayerId == null || externalPlayerId.isBlank()) {
            return Optional.empty();
        }
        Query query = Query.query(Criteria.where("externalPlayerId").is(externalPlayerId)).limit(1);
        return Optional.ofNullable(mongoTemplate.findOne(query, PlayerDocument.class));
    }

    public PageResponse<SnapshotDocument> findSnapshotsByPlayerId(
            String playerId,
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Criteria criteria = Criteria.where("playerId").is(playerId);
        if (from != null && to != null) {
            criteria = criteria.and("capturedAt").gte(from).lte(to);
        } else if (from != null) {
            criteria = criteria.and("capturedAt").gte(from);
        } else if (to != null) {
            criteria = criteria.and("capturedAt").lte(to);
        }

        Query countQuery = Query.query(criteria);
        long totalItems = mongoTemplate.count(countQuery, SnapshotDocument.class);
        if (totalItems <= 0L) {
            return PageResponse.of(List.of(), page, size, 0L);
        }

        Query pageQuery = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "capturedAt"))
                .skip((long) page * size)
                .limit(size);
        List<SnapshotDocument> docs = mongoTemplate.find(pageQuery, SnapshotDocument.class);
        return PageResponse.of(docs, page, size, totalItems);
    }

    public LocalRankingQueryResult queryLocalRanking(
            RankingMetric metric,
            String country,
            int page,
            int size
    ) {
        Query baseScopeQuery = country != null
                ? Query.query(Criteria.where("country").is(country))
                : new Query();
        long totalPlayersInScope = mongoTemplate.count(baseScopeQuery, PlayerDocument.class);

        Criteria includedCriteria = Criteria.where(metric.mongoField()).ne(null);
        if (metric.requiresRanked()) {
            includedCriteria = includedCriteria.and("league.ranked").is(true);
        }
        if (country != null) {
            includedCriteria = includedCriteria.and("country").is(country);
        }

        Query countIncludedQuery = Query.query(includedCriteria);
        long playersIncluded = mongoTemplate.count(countIncludedQuery, PlayerDocument.class);
        long playersExcludedNullMetric = Math.max(0L, totalPlayersInScope - playersIncluded);

        Instant dataAsOf = null;
        if (totalPlayersInScope > 0L) {
            Query latestSyncQuery = (country != null
                    ? Query.query(Criteria.where("country").is(country))
                    : new Query())
                    .with(Sort.by(Sort.Direction.DESC, "lastSyncedAt"))
                    .limit(1);
            PlayerDocument latestSyncDoc = mongoTemplate.findOne(latestSyncQuery, PlayerDocument.class);
            if (latestSyncDoc != null) {
                dataAsOf = latestSyncDoc.lastSyncedAt();
            }
        }

        if (playersIncluded <= 0L) {
            return new LocalRankingQueryResult(
                    PageResponse.of(List.of(), page, size, 0L),
                    0L,
                    playersExcludedNullMetric,
                    dataAsOf
            );
        }

        Query pageQuery = Query.query(includedCriteria)
                .with(Sort.by(
                        Sort.Order.desc(metric.mongoField()),
                        Sort.Order.asc("externalPlayerId")
                ))
                .skip((long) page * size)
                .limit(size);
        List<PlayerDocument> pageDocs = mongoTemplate.find(pageQuery, PlayerDocument.class);

        return new LocalRankingQueryResult(
                PageResponse.of(pageDocs, page, size, playersIncluded),
                playersIncluded,
                playersExcludedNullMetric,
                dataAsOf
        );
    }

    public record LocalRankingQueryResult(
            PageResponse<PlayerDocument> page,
            long playersIncluded,
            long playersExcludedNullMetric,
            Instant dataAsOf
    ) {}

    public void saveSyncedPlayerAndSnapshot(PlayerDocument player, SnapshotDocument snapshot) {
        validateForPersistence(player, snapshot);

        Query playerQuery = Query.query(Criteria.where("externalPlayerId").is(player.externalPlayerId()));
        Update playerUpdate = new Update()
                .set("externalPlayerId", player.externalPlayerId())
                .set("username", player.username())
                .set("usernameLower", player.usernameLower())
                .set("country", player.country())
                .set("profile", player.profile())
                .set("league", player.league())
                .set("lastSyncedAt", player.lastSyncedAt())
                .set("cacheWindowStartAt", player.cacheWindowStartAt())
                .set("cacheValidUntil", player.cacheValidUntil())
                .set("activeCacheWindowKey", player.activeCacheWindowKey())
                .set("sourceSchemaVersion", player.sourceSchemaVersion());
        mongoTemplate.upsert(playerQuery, playerUpdate, PlayerDocument.class);

        Query snapshotQuery = Query.query(
                Criteria.where("playerId").is(snapshot.playerId())
                        .and("cacheWindowKey").is(snapshot.cacheWindowKey())
        );
        Update snapshotUpdate = new Update()
                .setOnInsert("playerId", snapshot.playerId())
                .setOnInsert("cacheWindowKey", snapshot.cacheWindowKey())
                .setOnInsert("windowStartAt", snapshot.windowStartAt())
                .setOnInsert("windowUntilAt", snapshot.windowUntilAt())
                .setOnInsert("capturedAt", snapshot.capturedAt())
                .setOnInsert("rating", snapshot.rating())
                .setOnInsert("glicko", snapshot.glicko())
                .setOnInsert("rd", snapshot.rd())
                .setOnInsert("rank", snapshot.rank())
                .setOnInsert("metrics", snapshot.metrics())
                .setOnInsert("source", snapshot.source())
                .setOnInsert("schemaVersion", snapshot.schemaVersion());
        mongoTemplate.upsert(snapshotQuery, snapshotUpdate, SnapshotDocument.class);
    }

    public List<RecordDocument> findRecordsByPlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("playerId").is(playerId));
        return mongoTemplate.find(query, RecordDocument.class);
    }

    public void savePlayerRecords(List<RecordDocument> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        for (RecordDocument rec : records) {
            if (rec == null || rec.playerId() == null || rec.mode() == null || rec.recordType() == null) {
                continue;
            }
            Query query = Query.query(
                    Criteria.where("playerId").is(rec.playerId())
                            .and("mode").is(rec.mode())
                            .and("recordType").is(rec.recordType())
            );
            Update update = new Update()
                    .set("playerId", rec.playerId())
                    .set("mode", rec.mode())
                    .set("recordType", rec.recordType())
                    .set("recordId", rec.recordId())
                    .set("replayId", rec.replayId())
                    .set("primaryMetric", rec.primaryMetric())
                    .set("value", rec.value())
                    .set("finalTimeMs", rec.finalTimeMs())
                    .set("score", rec.score())
                    .set("lines", rec.lines())
                    .set("level", rec.level())
                    .set("pps", rec.pps())
                    .set("piecesPlaced", rec.piecesPlaced())
                    .set("inputs", rec.inputs())
                    .set("tSpins", rec.tSpins())
                    .set("finesseFaults", rec.finesseFaults())
                    .set("globalRank", rec.globalRank())
                    .set("countryRank", rec.countryRank())
                    .set("achievedAt", rec.achievedAt())
                    .set("lastSyncedAt", rec.lastSyncedAt())
                    .set("cacheValidUntil", rec.cacheValidUntil())
                    .set("source", rec.source());
            mongoTemplate.upsert(query, update, RecordDocument.class);
        }
    }

    public void savePrediction(PredictionDocument prediction) {
        if (prediction == null || prediction.playerId() == null) {
            return;
        }
        mongoTemplate.save(prediction);
    }

    public void upsertModelVersion(ModelVersionDocument modelVersion) {
        if (modelVersion == null || modelVersion.modelName() == null || modelVersion.version() == null) {
            return;
        }
        Query query = Query.query(
                Criteria.where("modelName").is(modelVersion.modelName())
                        .and("version").is(modelVersion.version())
        );
        Update update = new Update()
                .set("modelName", modelVersion.modelName())
                .set("version", modelVersion.version())
                .set("trainedAt", modelVersion.trainedAt())
                .set("datasetVersion", modelVersion.datasetVersion())
                .set("featureSchemaVersion", modelVersion.featureSchemaVersion())
                .set("metrics", modelVersion.metrics())
                .set("artifactPath", modelVersion.artifactPath())
                .set("status", modelVersion.status());
        mongoTemplate.upsert(query, update, ModelVersionDocument.class);
    }

    private static void validateForPersistence(PlayerDocument player, SnapshotDocument snapshot) {
        if (player == null
                || player.externalPlayerId() == null
                || player.externalPlayerId().isBlank()
                || player.activeCacheWindowKey() == null
                || player.activeCacheWindowKey().isBlank()) {
            throw new IllegalArgumentException("PlayerDocument requiere externalPlayerId y activeCacheWindowKey no vacíos.");
        }
        if (snapshot == null
                || snapshot.playerId() == null
                || snapshot.playerId().isBlank()
                || snapshot.cacheWindowKey() == null
                || snapshot.cacheWindowKey().isBlank()) {
            throw new IllegalArgumentException("SnapshotDocument requiere playerId y cacheWindowKey obligatorios (NOT NULL).");
        }
        if (!player.externalPlayerId().equals(snapshot.playerId())) {
            throw new IllegalArgumentException("El playerId del snapshot debe coincidir con externalPlayerId del jugador.");
        }
    }
}
