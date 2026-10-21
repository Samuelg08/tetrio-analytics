package com.tetrio.analytics.player.persistence;

import com.tetrio.analytics.common.PageResponse;
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
