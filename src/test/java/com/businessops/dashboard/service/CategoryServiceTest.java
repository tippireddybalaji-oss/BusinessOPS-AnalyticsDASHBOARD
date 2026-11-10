package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.CategoryCreateRequest;
import com.businessops.dashboard.dto.CategoryResponse;
import com.businessops.dashboard.dto.CategoryUpdateRequest;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.Category;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.repository.CategoryRepository;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CategoryService}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private static final String AUTH_UID = "user-auth-123";
    private static final String WS_ID = "ws-target-456";

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(workspaceService, categoryRepository);
    }

    // ── CREATE CATEGORY ──────────────────────────────────────────────────────

    @Test
    @DisplayName("createCategory: OWNER or ADMIN successfully creates custom category")
    void createCategory_withOwnerRole_createsSuccessfully() {
        CategoryCreateRequest request = new CategoryCreateRequest("Custom Consulting", "INCOME", "#10B981", "briefcase");

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(categoryRepository.findByNameAndType(WS_ID, "Custom Consulting", "INCOME")).thenReturn(Optional.empty());

        Category saved = new Category(
                "cat-1", WS_ID, "Custom Consulting", "INCOME", false,
                "#10B981", "briefcase", false, null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID
        );
        when(categoryRepository.createCategory(eq(WS_ID), any(Category.class))).thenReturn(saved);

        CategoryResponse response = categoryService.createCategory(AUTH_UID, WS_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("cat-1");
        assertThat(response.name()).isEqualTo("Custom Consulting");
        assertThat(response.type()).isEqualTo("INCOME");
        assertThat(response.isDefault()).isFalse();

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).createCategory(eq(WS_ID), captor.capture());
        Category captured = captor.getValue();
        assertThat(captured.getCreatedBy()).isEqualTo(AUTH_UID);
        assertThat(captured.getUpdatedBy()).isEqualTo(AUTH_UID);
    }

    @Test
    @DisplayName("createCategory: MEMBER role is rejected with 403 Forbidden")
    void createCategory_whenMemberRole_throwsAccessDenied() {
        CategoryCreateRequest request = new CategoryCreateRequest("New Category", "EXPENSE", null, null);

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID))
                .thenThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"));

        assertThatThrownBy(() -> categoryService.createCategory(AUTH_UID, WS_ID, request))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("Insufficient permissions");

        verify(categoryRepository, never()).createCategory(anyString(), any(Category.class));
    }

    @Test
    @DisplayName("createCategory: duplicate category name for same type is rejected")
    void createCategory_duplicateName_throwsIllegalArgument() {
        CategoryCreateRequest request = new CategoryCreateRequest("Consulting", "INCOME", "#10B981", "briefcase");

        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Category existing = new Category("cat-old", WS_ID, "Consulting", "INCOME", false, "#10B981", "tag", false, null, null, null, "u1", "u1");
        when(categoryRepository.findByNameAndType(WS_ID, "Consulting", "INCOME")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.createCategory(AUTH_UID, WS_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists for type INCOME");

        verify(categoryRepository, never()).createCategory(anyString(), any(Category.class));
    }

    // ── GET CATEGORY BY ID ───────────────────────────────────────────────────

    @Test
    @DisplayName("getCategoryById: returns category response when authorized")
    void getCategoryById_returnsCategory() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.MEMBER);

        Category cat = new Category("cat-1", WS_ID, "Rent", "EXPENSE", true, "#EF4444", "home", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "cat-1")).thenReturn(Optional.of(cat));

        CategoryResponse response = categoryService.getCategoryById(AUTH_UID, WS_ID, "cat-1");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("cat-1");
        assertThat(response.name()).isEqualTo("Rent");
    }

    @Test
    @DisplayName("getCategoryById: non-existent category throws ResourceNotFoundException")
    void getCategoryById_missing_throwsNotFound() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(categoryRepository.findById(WS_ID, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(AUTH_UID, WS_ID, "missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found with id: missing");
    }

    // ── LIST CATEGORIES & SELF-HEALING ───────────────────────────────────────

    @Test
    @DisplayName("listCategories: performs self-healing default category seeding when workspace has no categories")
    void listCategories_emptyWorkspace_triggersSelfHealing() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(categoryRepository.hasAnyCategories(WS_ID)).thenReturn(false);

        Category seededCat = new Category("def_inc_sales", WS_ID, "Client Invoicing / Sales", "INCOME", true, "#10B981", "briefcase", false, null, Timestamp.now(), Timestamp.now(), AUTH_UID, AUTH_UID);
        when(categoryRepository.listCategories(WS_ID, null, false)).thenReturn(List.of(seededCat));

        List<CategoryResponse> results = categoryService.listCategories(AUTH_UID, WS_ID, null, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("def_inc_sales");

        // Verify that default seeding was triggered
        verify(categoryRepository).seedDefaultCategories(eq(WS_ID), anyList());
    }

    @Test
    @DisplayName("listCategories: does not trigger seeding if categories already exist")
    void listCategories_existingWorkspace_doesNotSeed() {
        when(workspaceService.verifyWorkspaceAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.MEMBER);
        when(categoryRepository.hasAnyCategories(WS_ID)).thenReturn(true);

        Category existing = new Category("cat-1", WS_ID, "Software", "EXPENSE", true, "#F59E0B", "cpu", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.listCategories(WS_ID, "EXPENSE", false)).thenReturn(List.of(existing));

        List<CategoryResponse> results = categoryService.listCategories(AUTH_UID, WS_ID, "EXPENSE", false);

        assertThat(results).hasSize(1);
        verify(categoryRepository, never()).seedDefaultCategories(anyString(), anyList());
    }

    // ── UPDATE CATEGORY ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateCategory: OWNER updates custom category successfully")
    void updateCategory_customCategory_updatesSuccessfully() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Category existing = new Category("cat-custom", WS_ID, "Old Name", "EXPENSE", false, "#123456", "tag", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "cat-custom")).thenReturn(Optional.of(existing));
        when(categoryRepository.findByNameAndType(WS_ID, "New Name", "EXPENSE")).thenReturn(Optional.empty());

        Category updated = new Category("cat-custom", WS_ID, "New Name", "EXPENSE", false, "#4F46E5", "briefcase", false, null, Timestamp.now(), Timestamp.now(), "u1", AUTH_UID);
        when(categoryRepository.updateCategory(eq(WS_ID), any(Category.class))).thenReturn(updated);

        CategoryUpdateRequest request = new CategoryUpdateRequest("New Name", "#4F46E5", "briefcase");
        CategoryResponse response = categoryService.updateCategory(AUTH_UID, WS_ID, "cat-custom", request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.color()).isEqualTo("#4F46E5");
    }

    @Test
    @DisplayName("updateCategory: default category cannot be modified")
    void updateCategory_defaultCategory_throwsIllegalArgument() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Category defaultCat = new Category("def_inc_sales", WS_ID, "Client Invoicing / Sales", "INCOME", true, "#10B981", "briefcase", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "def_inc_sales")).thenReturn(Optional.of(defaultCat));

        CategoryUpdateRequest request = new CategoryUpdateRequest("Renamed Sales", null, null);

        assertThatThrownBy(() -> categoryService.updateCategory(AUTH_UID, WS_ID, "def_inc_sales", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default categories cannot be renamed or modified");

        verify(categoryRepository, never()).updateCategory(anyString(), any(Category.class));
    }

    @Test
    @DisplayName("updateCategory: archived category cannot be updated")
    void updateCategory_archivedCategory_throwsIllegalArgument() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.ADMIN);

        Category archived = new Category("cat-1", WS_ID, "Archived", "EXPENSE", false, "#123456", "tag", true, Timestamp.now(), Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "cat-1")).thenReturn(Optional.of(archived));

        CategoryUpdateRequest request = new CategoryUpdateRequest("Attempted Edit", null, null);

        assertThatThrownBy(() -> categoryService.updateCategory(AUTH_UID, WS_ID, "cat-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Archived category cannot be updated");
    }

    // ── ARCHIVE CATEGORY ─────────────────────────────────────────────────────

    @Test
    @DisplayName("archiveCategory: OWNER or ADMIN archives custom category successfully")
    void archiveCategory_customCategory_archivesSuccessfully() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Category customCat = new Category("cat-custom", WS_ID, "Custom Travel", "EXPENSE", false, "#6366F1", "map-pin", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "cat-custom")).thenReturn(Optional.of(customCat));

        categoryService.archiveCategory(AUTH_UID, WS_ID, "cat-custom");

        verify(categoryRepository).archiveCategory(WS_ID, "cat-custom", AUTH_UID);
    }

    @Test
    @DisplayName("archiveCategory: default category cannot be archived")
    void archiveCategory_defaultCategory_throwsIllegalArgument() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);

        Category defaultCat = new Category("def_exp_rent", WS_ID, "Rent & Utilities", "EXPENSE", true, "#EF4444", "home", false, null, Timestamp.now(), Timestamp.now(), "u1", "u1");
        when(categoryRepository.findById(WS_ID, "def_exp_rent")).thenReturn(Optional.of(defaultCat));

        assertThatThrownBy(() -> categoryService.archiveCategory(AUTH_UID, WS_ID, "def_exp_rent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default categories cannot be archived");

        verify(categoryRepository, never()).archiveCategory(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("archiveCategory: non-existent category throws ResourceNotFoundException")
    void archiveCategory_missing_throwsNotFound() {
        when(workspaceService.verifyWorkspaceWriteAccess(AUTH_UID, WS_ID)).thenReturn(WorkspaceRole.OWNER);
        when(categoryRepository.findById(WS_ID, "missing-cat")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.archiveCategory(AUTH_UID, WS_ID, "missing-cat"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found with id: missing-cat");

        verify(categoryRepository, never()).archiveCategory(anyString(), anyString(), anyString());
    }
}

