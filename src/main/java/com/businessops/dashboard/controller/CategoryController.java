package com.businessops.dashboard.controller;

import com.businessops.dashboard.dto.CategoryCreateRequest;
import com.businessops.dashboard.dto.CategoryResponse;
import com.businessops.dashboard.dto.CategoryUpdateRequest;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Category Management within a specific workspace.
 *
 * <p>Base path: {@code /api/v1/workspaces/{workspaceId}/categories}
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final SecurityUtils securityUtils;

    public CategoryController(CategoryService categoryService, SecurityUtils securityUtils) {
        this.categoryService = categoryService;
        this.securityUtils = securityUtils;
    }

    /**
     * Creates a custom category in the specified workspace.
     *
     * @param workspaceId target workspace ID
     * @param request validated creation payload
     * @return 201 Created with created category
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @PathVariable("workspaceId") String workspaceId,
            @Valid @RequestBody CategoryCreateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        CategoryResponse response = categoryService.createCategory(uid, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all categories in the workspace with optional type filtering and archival inclusion.
     *
     * @param workspaceId target workspace ID
     * @param type optional category type filter (INCOME or EXPENSE)
     * @param includeArchived whether to include soft-deleted categories (default: false)
     * @return 200 OK with list of category responses
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listCategories(
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "includeArchived", defaultValue = "false") boolean includeArchived) {
        String uid = securityUtils.getAuthenticatedUid();
        List<CategoryResponse> response = categoryService.listCategories(uid, workspaceId, type, includeArchived);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves single category details by ID.
     *
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @return 200 OK with category details
     */
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategory(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("categoryId") String categoryId) {
        String uid = securityUtils.getAuthenticatedUid();
        CategoryResponse response = categoryService.getCategoryById(uid, workspaceId, categoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing custom category.
     *
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @param request validated update payload
     * @return 200 OK with updated category details
     */
    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("categoryId") String categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        CategoryResponse response = categoryService.updateCategory(uid, workspaceId, categoryId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Soft-deletes (archives) a custom category.
     *
     * @param workspaceId target workspace ID
     * @param categoryId target category ID
     * @return 204 No Content
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> archiveCategory(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("categoryId") String categoryId) {
        String uid = securityUtils.getAuthenticatedUid();
        categoryService.archiveCategory(uid, workspaceId, categoryId);
        return ResponseEntity.noContent().build();
    }
}

