package com.businessops.dashboard.repository;

import com.businessops.dashboard.exception.DatabaseOperationException;
import com.businessops.dashboard.model.Category;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Cloud Firestore implementation of {@link CategoryRepository}.
 *
 * <p>All documents reside strictly at {@code workspaces/{workspaceId}/categories/{categoryId}}.
 */
@Repository
public class FirestoreCategoryRepository implements CategoryRepository {

    private static final Logger log = LoggerFactory.getLogger(FirestoreCategoryRepository.class);

    private static final String WORKSPACES_COLLECTION = "workspaces";
    private static final String CATEGORIES_SUBCOLLECTION = "categories";

    private final Firestore firestore;

    public FirestoreCategoryRepository(@Autowired(required = false) Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Category createCategory(String workspaceId, Category category) {
        checkFirestoreAvailable();

        try {
            CollectionReference categoriesRef = getCategoriesCollection(workspaceId);
            DocumentReference docRef = (category.getId() != null && !category.getId().isBlank())
                    ? categoriesRef.document(category.getId())
                    : categoriesRef.document();

            String categoryId = docRef.getId();
            category.setId(categoryId);
            category.setWorkspaceId(workspaceId);

            Timestamp now = Timestamp.now();
            if (category.getCreatedAt() == null) {
                category.setCreatedAt(now);
            }
            category.setUpdatedAt(now);

            Map<String, Object> data = toDocumentMap(category);
            docRef.set(data).get();

            log.info("Category created: id='{}', name='{}' in workspace='{}'", categoryId, category.getName(), workspaceId);
            return category;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while creating category", e);
        } catch (ExecutionException e) {
            log.error("Failed to create category in Firestore for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to persist category: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Category> findById(String workspaceId, String categoryId) {
        checkFirestoreAvailable();

        try {
            DocumentSnapshot snap = getCategoriesCollection(workspaceId)
                    .document(categoryId)
                    .get()
                    .get();

            if (!snap.exists()) {
                return Optional.empty();
            }

            return Optional.ofNullable(mapDocumentToCategory(snap, workspaceId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while fetching category", e);
        } catch (ExecutionException e) {
            log.error("Failed to fetch category {} from workspace {}: {}", categoryId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to retrieve category: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Category> findByNameAndType(String workspaceId, String name, String type) {
        checkFirestoreAvailable();

        if (name == null || name.isBlank() || type == null || type.isBlank()) {
            return Optional.empty();
        }

        try {
            Query query = getCategoriesCollection(workspaceId)
                    .whereEqualTo("type", type.trim().toUpperCase());

            QuerySnapshot snapshot = query.get().get();
            String targetName = name.trim();

            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                Category category = mapDocumentToCategory(doc, workspaceId);
                if (category != null
                        && !category.isArchived()
                        && targetName.equalsIgnoreCase(category.getName())) {
                    return Optional.of(category);
                }
            }

            return Optional.empty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while checking duplicate category", e);
        } catch (ExecutionException e) {
            log.error("Failed to query category by name for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to query category: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Category> listCategories(String workspaceId, String type, boolean includeArchived) {
        checkFirestoreAvailable();

        try {
            Query query = getCategoriesCollection(workspaceId);
            if (type != null && !type.isBlank()) {
                query = query.whereEqualTo("type", type.trim().toUpperCase());
            }

            QuerySnapshot snapshot = query.get().get();
            List<Category> categories = new ArrayList<>();

            for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                Category cat = mapDocumentToCategory(doc, workspaceId);
                if (cat != null) {
                    if (!includeArchived && cat.isArchived()) {
                        continue; // Exclude archived categories from standard listings
                    }
                    categories.add(cat);
                }
            }

            // Sort deterministically: default categories first, then alphabetically by name
            categories.sort((a, b) -> {
                if (a.isDefault() != b.isDefault()) {
                    return a.isDefault() ? -1 : 1;
                }
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                return nameA.compareToIgnoreCase(nameB);
            });

            return categories;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while listing categories", e);
        } catch (ExecutionException e) {
            log.error("Failed to list categories for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to query categories: " + e.getMessage(), e);
        }
    }

    @Override
    public Category updateCategory(String workspaceId, Category category) {
        checkFirestoreAvailable();

        try {
            DocumentReference docRef = getCategoriesCollection(workspaceId).document(category.getId());

            Timestamp now = Timestamp.now();
            category.setUpdatedAt(now);

            Map<String, Object> updates = new HashMap<>();
            updates.put("name", category.getName());
            updates.put("color", category.getColor());
            updates.put("icon", category.getIcon());
            updates.put("updatedAt", now);
            updates.put("updatedBy", category.getUpdatedBy());

            docRef.update(updates).get();
            log.info("Category updated: id='{}' in workspace='{}'", category.getId(), workspaceId);

            return category;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while updating category", e);
        } catch (ExecutionException e) {
            log.error("Failed to update category {} in workspace {}: {}", category.getId(), workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to update category: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean archiveCategory(String workspaceId, String categoryId, String authenticatedUid) {
        checkFirestoreAvailable();

        try {
            DocumentReference docRef = getCategoriesCollection(workspaceId).document(categoryId);

            return firestore.runTransaction(transaction -> {
                DocumentSnapshot snap = transaction.get(docRef).get();
                if (!snap.exists()) {
                    return false;
                }

                Boolean isDefault = snap.getBoolean("isDefault");
                if (Boolean.TRUE.equals(isDefault)) {
                    throw new IllegalArgumentException("Default categories cannot be archived: " + categoryId);
                }

                Boolean isArchived = snap.getBoolean("isArchived");
                if (Boolean.TRUE.equals(isArchived)) {
                    return true; // Already archived; idempotent success
                }

                Timestamp now = Timestamp.now();
                Map<String, Object> updates = new HashMap<>();
                updates.put("isArchived", true);
                updates.put("archivedAt", now);
                updates.put("updatedAt", now);
                updates.put("updatedBy", authenticatedUid);

                transaction.update(docRef, updates);
                return true;
            }).get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while archiving category", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException iae) {
                throw iae;
            }
            log.error("Failed to archive category {} in workspace {}: {}", categoryId, workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to archive category: " + e.getMessage(), e);
        }
    }

    @Override
    public void seedDefaultCategories(String workspaceId, List<Category> defaultCategories) {
        checkFirestoreAvailable();

        if (defaultCategories == null || defaultCategories.isEmpty()) {
            return;
        }

        try {
            CollectionReference categoriesRef = getCategoriesCollection(workspaceId);
            WriteBatch batch = firestore.batch();

            for (Category category : defaultCategories) {
                DocumentReference docRef = categoriesRef.document(category.getId());
                Map<String, Object> data = toDocumentMap(category);
                batch.set(docRef, data, SetOptions.merge());
            }

            batch.commit().get();
            log.info("Seeded {} default categories for workspace='{}'", defaultCategories.size(), workspaceId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while seeding default categories", e);
        } catch (ExecutionException e) {
            log.error("Failed to seed default categories for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to seed default categories: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasAnyCategories(String workspaceId) {
        checkFirestoreAvailable();

        try {
            QuerySnapshot snap = getCategoriesCollection(workspaceId)
                    .limit(1)
                    .get()
                    .get();

            return !snap.isEmpty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseOperationException("Thread interrupted while checking category existence", e);
        } catch (ExecutionException e) {
            log.error("Failed to check category existence for workspace {}: {}", workspaceId, e.getMessage(), e);
            throw new DatabaseOperationException("Failed to check category existence: " + e.getMessage(), e);
        }
    }

    private CollectionReference getCategoriesCollection(String workspaceId) {
        return firestore.collection(WORKSPACES_COLLECTION)
                .document(workspaceId)
                .collection(CATEGORIES_SUBCOLLECTION);
    }

    private void checkFirestoreAvailable() {
        if (firestore == null) {
            throw new DatabaseOperationException("Firestore client is unconfigured. Set GOOGLE_APPLICATION_CREDENTIALS.");
        }
    }

    private Map<String, Object> toDocumentMap(Category category) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", category.getName());
        data.put("type", category.getType());
        data.put("isDefault", category.isDefault());
        data.put("color", category.getColor());
        data.put("icon", category.getIcon());
        data.put("isArchived", category.isArchived());
        data.put("archivedAt", category.getArchivedAt());
        data.put("createdAt", category.getCreatedAt());
        data.put("updatedAt", category.getUpdatedAt());
        data.put("createdBy", category.getCreatedBy());
        data.put("updatedBy", category.getUpdatedBy());
        return data;
    }

    private Category mapDocumentToCategory(DocumentSnapshot doc, String workspaceId) {
        if (doc == null || !doc.exists()) {
            return null;
        }

        Category cat = new Category();
        cat.setId(doc.getId());
        cat.setWorkspaceId(workspaceId);
        cat.setName(doc.getString("name"));
        cat.setType(doc.getString("type"));
        Boolean isDefault = doc.getBoolean("isDefault");
        cat.setDefault(Boolean.TRUE.equals(isDefault));
        cat.setColor(doc.getString("color"));
        cat.setIcon(doc.getString("icon"));
        Boolean isArchived = doc.getBoolean("isArchived");
        cat.setArchived(Boolean.TRUE.equals(isArchived));
        cat.setArchivedAt(doc.getTimestamp("archivedAt"));
        cat.setCreatedAt(doc.getTimestamp("createdAt"));
        cat.setUpdatedAt(doc.getTimestamp("updatedAt"));
        cat.setCreatedBy(doc.getString("createdBy"));
        cat.setUpdatedBy(doc.getString("updatedBy"));

        return cat;
    }
}

