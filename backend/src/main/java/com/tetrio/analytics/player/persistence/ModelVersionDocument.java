package com.tetrio.analytics.player.persistence;

import java.time.Instant;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "model_versions")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_model_versions_name_version_unique",
                def = "{'modelName': 1, 'version': 1}",
                unique = true
        )
})
public record ModelVersionDocument(
        @Id String id,
        String modelName,
        String version,
        Instant trainedAt,
        String datasetVersion,
        String featureSchemaVersion,
        Map<String, Double> metrics,
        String artifactPath,
        String status
) {}
