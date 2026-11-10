package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.CategoryCreateRequest;
import com.businessops.dashboard.dto.CategoryResponse;
import com.businessops.dashboard.dto.CategoryUpdateRequest;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.model.Category;
import com.businessops.dashboard.model.DefaultCategories;
import com.businessops.dashboard.model.TransactionType;
import com.businessops.dashboard.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service managing workspace categories, validation, seeding, and role-based authorization.
 *
 * <p><strong>Authorization Matrix:</strong>
 * <ul>
 *   <li>Read operations (list, get): {@code OWNER}, {@code ADMIN}, {@code MEMBER}.</li>
 *   <li>Write operations (create, update, archive): {@code OWNER}, {@code ADMIN}.</li>
 *   <li>Non-members: rejected with HTTP 403 Forbidden.</li>
 * </ul>
 */
@Service
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final WorkspaceService workspaceService;
    private final CategoryRepository categoryRepository;

    public CategoryService(WorkspaceService workspaceService, CategoryRepository categoryRepository) {
        this.workspaceService = workspaceService;
        this.categoryRepository = categoryRepository;
    }

    /**
     * Creates a custom category in the specified workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param request validated creation request
     * @return created category response
     */
    public CategoryResponse createCategory(String authenticatedUid, String workspaceId, CategoryCreateRequest request) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        String normalizedType = TransactionType.fromString(request.type()).name();
        String normalizedName = request.name().trim();

        // Enforce uniqueness within workspace and category type
        categoryRepository.findByNameAndType(workspaceId, normalizedName, normalizedType).ifPresent(cat -> {
            throw new IllegalArgumentException(
                    "A category with name '" + normalizedName + "' already exists for type " + normalizedType
            );
        });

        Category category = new Category();
        category.setWorkspaceId(workspaceId);
        category.setName(normalizedName);
        category.setType(normalizedType);
        category.setDefault(false);
        category.setColor(request.color() != null ? request.color().trim() : "#4F46E5");
        category.setIcon(request.icon() != null ? request.icon().trim() : "tag");
        category.setArchived(false);
        category.setCreatedBy(authenticatedUid);
        category.setUpdatedBy(authenticatedUid);

        Category created = categoryRepository.createCategory(workspaceId, category);
        log.info("Category created: id='{}' in workspace='{}' by user='{}'", created.getId(), workspaceId, authenticatedUid);

        return CategoryResponse.from(created);
    }

    /**
     * Retrieves category details by ID within the workspace.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @return category response
     */
    public CategoryResponse getCategoryById(String authenticatedUid, String workspaceId, String categoryId) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        Category category = categoryRepository.findById(workspaceId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + categoryId + " in workspace: " + workspaceId
                ));

        return CategoryResponse.from(category);
    }

    /**
     * Lists categories belonging to a workspace with self-healing default category seeding.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param type optional category type filter (INCOME or EXPENSE)
     * @param includeArchived whether to include soft-deleted categories
     * @return list of category responses
     */
    public List<CategoryResponse> listCategories(String authenticatedUid, String workspaceId,
                                                String type, boolean includeArchived) {
        workspaceService.verifyWorkspaceAccess(authenticatedUid, workspaceId);

        String normalizedType = null;
        if (type != null && !type.isBlank()) {
            normalizedType = TransactionType.fromString(type).name();
        }

        // Self-healing: if workspace has no categories (e.g. legacy workspace), seed defaults on-demand
        if (!categoryRepository.hasAnyCategories(workspaceId)) {
            log.info("Self-healing: No categories found for workspace '{}'. Seeding default categories.", workspaceId);
            seedDefaultCategories(workspaceId, authenticatedUid);
        }

        List<Category> categories = categoryRepository.listCategories(workspaceId, normalizedType, includeArchived);
        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * Updates an existing custom category.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @param request update payload
     * @return updated category response
     */
    public CategoryResponse updateCategory(String authenticatedUid, String workspaceId,
                                          String categoryId, CategoryUpdateRequest request) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        Category existing = categoryRepository.findById(workspaceId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + categoryId + " in workspace: " + workspaceId
                ));

        if (existing.isDefault()) {
            throw new IllegalArgumentException("Default categories cannot be renamed or modified: " + categoryId);
        }

        if (existing.isArchived()) {
            throw new IllegalArgumentException("Archived category cannot be updated: " + categoryId);
        }

        String updatedName = request.name().trim();
        if (!existing.getName().equalsIgnoreCase(updatedName)) {
            categoryRepository.findByNameAndType(workspaceId, updatedName, existing.getType()).ifPresent(cat -> {
                throw new IllegalArgumentException(
                        "A category with name '" + updatedName + "' already exists for type " + existing.getType()
                );
            });
            existing.setName(updatedName);
        }

        if (request.color() != null && !request.color().isBlank()) {
            existing.setColor(request.color().trim());
        }
        if (request.icon() != null) {
            existing.setIcon(request.icon().trim());
        }
        existing.setUpdatedBy(authenticatedUid);

        Category updated = categoryRepository.updateCategory(workspaceId, existing);
        log.info("Category updated: id='{}' in workspace='{}' by user='{}'", categoryId, workspaceId, authenticatedUid);

        return CategoryResponse.from(updated);
    }

    /**
     * Soft-deletes a category using idempotent archival.
     *
     * @param authenticatedUid caller's verified Firebase UID
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     */
    public void archiveCategory(String authenticatedUid, String workspaceId, String categoryId) {
        workspaceService.verifyWorkspaceWriteAccess(authenticatedUid, workspaceId);

        Category existing = categoryRepository.findById(workspaceId, categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found with id: " + categoryId + " in workspace: " + workspaceId
                ));

        if (existing.isDefault()) {
            throw new IllegalArgumentException("Default categories cannot be archived: " + categoryId);
        }

        categoryRepository.archiveCategory(workspaceId, categoryId, authenticatedUid);
        log.info("Category archived: id='{}' in workspace='{}' by user='{}'", categoryId, workspaceId, authenticatedUid);
    }

    /**
     * Seeds default categories in the workspace idempotently.
     *
     * @param workspaceId target workspace ID
     * @param creatorUid creator Firebase UID
     */
    public void seedDefaultCategories(String workspaceId, String creatorUid) {
        List<Category> defaults = DefaultCategories.createDefaultCategories(workspaceId, creatorUid);
        categoryRepository.seedDefaultCategories(workspaceId, defaults);
    }
}

