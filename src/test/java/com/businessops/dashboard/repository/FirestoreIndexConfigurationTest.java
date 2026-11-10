package com.businessops.dashboard.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FirestoreIndexConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("firestore.indexes.json: parses as valid JSON with zero syntax errors")
    void testFirestoreIndexesJson_ValidJson() throws Exception {
        Path indexPath = Path.of("firestore.indexes.json");
        assertThat(Files.exists(indexPath)).isTrue();

        JsonNode root = objectMapper.readTree(indexPath.toFile());
        assertThat(root).isNotNull();
        assertThat(root.has("indexes")).isTrue();
        assertThat(root.has("fieldOverrides")).isTrue();
    }

    @Test
    @DisplayName("firestore.indexes.json: contains exactly 11 composite indexes")
    void testFirestoreIndexesJson_Contains11Indexes() throws Exception {
        JsonNode root = objectMapper.readTree(new File("firestore.indexes.json"));
        JsonNode indexes = root.get("indexes");

        assertThat(indexes.isArray()).isTrue();
        assertThat(indexes).hasSize(11);
    }

    @Test
    @DisplayName("firestore.indexes.json: contains Phase 5 Indexes #10 and #11")
    void testFirestoreIndexesJson_ContainsPhase5Indexes() throws Exception {
        JsonNode root = objectMapper.readTree(new File("firestore.indexes.json"));
        JsonNode indexes = root.get("indexes");

        boolean hasIndex10 = false;
        boolean hasIndex11 = false;

        for (JsonNode index : indexes) {
            String collection = index.get("collectionGroup").asText();
            String queryScope = index.get("queryScope").asText();
            JsonNode fields = index.get("fields");

            if ("transactions".equals(collection) && "COLLECTION".equals(queryScope)) {
                if (fields.size() == 4
                        && "currency".equals(fields.get(0).get("fieldPath").asText())
                        && "status".equals(fields.get(1).get("fieldPath").asText())
                        && "type".equals(fields.get(2).get("fieldPath").asText())
                        && "transactionDate".equals(fields.get(3).get("fieldPath").asText())) {
                    hasIndex10 = true;
                }

                if (fields.size() == 3
                        && "currency".equals(fields.get(0).get("fieldPath").asText())
                        && "status".equals(fields.get(1).get("fieldPath").asText())
                        && "transactionDate".equals(fields.get(2).get("fieldPath").asText())) {
                    hasIndex11 = true;
                }
            }
        }

        assertThat(hasIndex10).describedAs("Index #10 (currency, status, type, transactionDate) must exist").isTrue();
        assertThat(hasIndex11).describedAs("Index #11 (currency, status, transactionDate) must exist").isTrue();
    }

    @Test
    @DisplayName("firestore.indexes.json: has zero duplicate composite index configurations")
    void testFirestoreIndexesJson_ZeroDuplicates() throws Exception {
        JsonNode root = objectMapper.readTree(new File("firestore.indexes.json"));
        JsonNode indexes = root.get("indexes");

        Set<String> uniqueSignatures = new HashSet<>();
        for (JsonNode index : indexes) {
            StringBuilder sig = new StringBuilder();
            sig.append(index.get("collectionGroup").asText()).append("::");
            sig.append(index.get("queryScope").asText()).append("::");
            for (JsonNode field : index.get("fields")) {
                sig.append(field.get("fieldPath").asText()).append(":")
                   .append(field.get("order").asText()).append(",");
            }
            boolean added = uniqueSignatures.add(sig.toString());
            assertThat(added).describedAs("Duplicate index configuration found: " + sig).isTrue();
        }
    }

    @Test
    @DisplayName("firestore.indexes.json: preserves members.uid field override")
    void testFirestoreIndexesJson_PreservesFieldOverrides() throws Exception {
        JsonNode root = objectMapper.readTree(new File("firestore.indexes.json"));
        JsonNode overrides = root.get("fieldOverrides");

        assertThat(overrides.isArray()).isTrue();
        assertThat(overrides).hasSize(1);

        JsonNode override = overrides.get(0);
        assertThat(override.get("collectionGroup").asText()).isEqualTo("members");
        assertThat(override.get("fieldPath").asText()).isEqualTo("uid");
        assertThat(override.get("indexes").get(0).get("queryScope").asText()).isEqualTo("COLLECTION_GROUP");
    }
}

