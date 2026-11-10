package com.businessops.dashboard.repository;

import com.businessops.dashboard.model.Category;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for category persistence.
 *
 * <p>All operations are strictly scoped to {@code workspaces/{workspaceId}/categories}.
 */
public interface CategoryRepository {

    /**
     * Persists a category document in the workspace.
     *
     * @param workspaceId target workspace ID
     * @param category category domain entity
     * @return saved category with generated or assigned ID
     */
    Category createCategory(String workspaceId, Category category);

    /**
     * Finds a category by its ID within the specified workspace.
     *
     * @param workspaceId target workspace ID
     * @param categoryId category document ID
     * @return optional containing the category if found
     */
    Optional<Category> findById(String workspaceId, String categoryId);

    /**
     * Finds an active (non-archived) category by exact case-insensitive name and type within the workspace.
     *
     * @param workspaceId target workspace ID
     * @param name category name
     * @param type category type (INCOME or EXPENSE)
     * @return optional containing matching category if found
     */
    Optional<Category> findByNameAndType(String workspaceId, String name, String type);

    /**
     * Lists categories belonging to a workspace with optional type filtering and archival inclusion.
     *
     * @param workspaceId target workspace ID
     * @param type optional type filter (INCOME or EXPENSE)
     * @param includeArchived whether to include soft-deleted categories
     * @return list of categories sorted by default status and name
     */
    List<Category> listCategories(String workspaceId, String type, boolean includeArchived);

    /**
     * Updates an existing custom category's mutable fields.
     *
     * @param workspaceId target workspace ID
     * @param category category entity with updated values
     * @return updated category
     */
    Category updateCategory(String workspaceId, Category category);

    /**
     * Soft-deletes a category by setting isArchived = true using Firestore transaction isolation.
     *
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @param authenticatedUid actor Firebase UID
     * @return true if archived, false if not found
     */
    boolean archiveCategory(String workspaceId, String categoryId, String authenticatedUid);

    /**
     * Seeds default categories in an idempotent batch operation.
     *
     * @param workspaceId target workspace ID
     * @param defaultCategories default categories to seed
     */
    void seedDefaultCategories(String workspaceId, List<Category> defaultCategories);

    /**
     * Checks if any categories already exist in the workspace.
     *
     * @param workspaceId target workspace ID
     * @return true if at least one category exists
     */
    boolean hasAnyCategories(String workspaceId);
}

