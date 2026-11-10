package com.businessops.dashboard.controller;

import com.businessops.dashboard.config.SecurityConfig;
import com.businessops.dashboard.dto.CategoryCreateRequest;
import com.businessops.dashboard.dto.CategoryResponse;
import com.businessops.dashboard.dto.CategoryUpdateRequest;
import com.businessops.dashboard.exception.GlobalExceptionHandler;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.security.FirebaseAuthFilter;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link CategoryController}.
 */
@WebMvcTest(controllers = CategoryController.class)
@Import({SecurityConfig.class, FirebaseAuthFilter.class, GlobalExceptionHandler.class})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    private static final String VALID_TOKEN = "valid.firebase.token";
    private static final String TEST_UID = "user-test-789";
    private static final String WS_ID = "ws-abc-123";

    @BeforeEach
    void setupAuth() throws Exception {
        FirebaseToken mockToken = mock(FirebaseToken.class);
        when(mockToken.getUid()).thenReturn(TEST_UID);
        when(mockToken.getEmail()).thenReturn("tester@businessops.io");

        when(firebaseAuth.verifyIdToken(VALID_TOKEN)).thenReturn(mockToken);
        when(securityUtils.getAuthenticatedUid()).thenReturn(TEST_UID);
    }

    // ── CREATE CATEGORY (POST) ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/categories with valid payload → 201 Created")
    void createCategory_valid_returns201() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "Custom Consulting", "INCOME", "#10B981", "briefcase"
        );
        CategoryResponse response = new CategoryResponse(
                "cat-1", WS_ID, "Custom Consulting", "INCOME", false,
                "#10B981", "briefcase", false, null, Instant.now(), Instant.now(), TEST_UID, TEST_UID
        );

        when(categoryService.createCategory(eq(TEST_UID), eq(WS_ID), any(CategoryCreateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("cat-1"))
                .andExpect(jsonPath("$.name").value("Custom Consulting"))
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.color").value("#10B981"));
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/categories with invalid color hex → 400 Bad Request")
    void createCategory_invalidColor_returns400() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "Custom Consulting", "INCOME", "invalid-color", "briefcase"
        );

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.color").exists());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/categories without auth header → 401 Unauthorized")
    void createCategory_unauthenticated_returns401() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "Custom Consulting", "INCOME", "#10B981", "briefcase"
        );

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/workspaces/{id}/categories when MEMBER role → 403 Forbidden")
    void createCategory_memberRole_returns403() throws Exception {
        CategoryCreateRequest request = new CategoryCreateRequest(
                "Custom Consulting", "INCOME", "#10B981", "briefcase"
        );

        when(categoryService.createCategory(eq(TEST_UID), eq(WS_ID), any(CategoryCreateRequest.class)))
                .thenThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"));

        mockMvc.perform(post("/api/v1/workspaces/" + WS_ID + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Insufficient permissions: Member role cannot perform write operations"));
    }

    // ── LIST CATEGORIES (GET) ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/categories → 200 OK list")
    void listCategories_returns200() throws Exception {
        CategoryResponse cat1 = new CategoryResponse(
                "def_inc_sales", WS_ID, "Client Invoicing / Sales", "INCOME", true,
                "#10B981", "briefcase", false, null, Instant.now(), Instant.now(), "sys", "sys"
        );
        when(categoryService.listCategories(eq(TEST_UID), eq(WS_ID), isNull(), eq(false)))
                .thenReturn(List.of(cat1));

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("def_inc_sales"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/categories with type filter → 200 OK")
    void listCategories_withTypeFilter_returns200() throws Exception {
        when(categoryService.listCategories(eq(TEST_UID), eq(WS_ID), eq("EXPENSE"), eq(false)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/categories?type=EXPENSE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ── GET CATEGORY BY ID (GET) ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/categories/{catId} → 200 OK")
    void getCategory_exists_returns200() throws Exception {
        CategoryResponse cat = new CategoryResponse(
                "cat-100", WS_ID, "Custom Travel", "EXPENSE", false,
                "#6366F1", "map-pin", false, null, Instant.now(), Instant.now(), TEST_UID, TEST_UID
        );
        when(categoryService.getCategoryById(eq(TEST_UID), eq(WS_ID), eq("cat-100")))
                .thenReturn(cat);

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/categories/cat-100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("cat-100"))
                .andExpect(jsonPath("$.name").value("Custom Travel"));
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{id}/categories/{catId} not found → 404 Not Found")
    void getCategory_notFound_returns404() throws Exception {
        when(categoryService.getCategoryById(eq(TEST_UID), eq(WS_ID), eq("missing")))
                .thenThrow(new ResourceNotFoundException("Category not found with id: missing in workspace: " + WS_ID));

        mockMvc.perform(get("/api/v1/workspaces/" + WS_ID + "/categories/missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ── UPDATE CATEGORY (PUT) ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/categories/{catId} valid payload → 200 OK")
    void updateCategory_valid_returns200() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest("Renamed Travel", "#6366F1", "map-pin");
        CategoryResponse response = new CategoryResponse(
                "cat-100", WS_ID, "Renamed Travel", "EXPENSE", false,
                "#6366F1", "map-pin", false, null, Instant.now(), Instant.now(), TEST_UID, TEST_UID
        );

        when(categoryService.updateCategory(eq(TEST_UID), eq(WS_ID), eq("cat-100"), any(CategoryUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/workspaces/" + WS_ID + "/categories/cat-100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Travel"));
    }

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/categories/{catId} on default category → 400 Bad Request")
    void updateCategory_defaultCategory_returns400() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest("Renamed Rent", null, null);

        when(categoryService.updateCategory(eq(TEST_UID), eq(WS_ID), eq("def_exp_rent"), any(CategoryUpdateRequest.class)))
                .thenThrow(new IllegalArgumentException("Default categories cannot be renamed or modified: def_exp_rent"));

        mockMvc.perform(put("/api/v1/workspaces/" + WS_ID + "/categories/def_exp_rent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Argument"))
                .andExpect(jsonPath("$.message").value("Default categories cannot be renamed or modified: def_exp_rent"));
    }

    @Test
    @DisplayName("PUT /api/v1/workspaces/{id}/categories/{catId} when MEMBER role → 403 Forbidden")
    void updateCategory_memberRole_returns403() throws Exception {
        CategoryUpdateRequest request = new CategoryUpdateRequest("Renamed Travel", null, null);

        when(categoryService.updateCategory(eq(TEST_UID), eq(WS_ID), eq("cat-100"), any(CategoryUpdateRequest.class)))
                .thenThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"));

        mockMvc.perform(put("/api/v1/workspaces/" + WS_ID + "/categories/cat-100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ── ARCHIVE CATEGORY (DELETE) ────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/categories/{catId} → 204 No Content")
    void archiveCategory_valid_returns204() throws Exception {
        doNothing().when(categoryService).archiveCategory(eq(TEST_UID), eq(WS_ID), eq("cat-100"));

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/categories/cat-100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/categories/{catId} on default category → 400 Bad Request")
    void archiveCategory_defaultCategory_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Default categories cannot be archived: def_exp_rent"))
                .when(categoryService).archiveCategory(eq(TEST_UID), eq(WS_ID), eq("def_exp_rent"));

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/categories/def_exp_rent")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Argument"))
                .andExpect(jsonPath("$.message").value("Default categories cannot be archived: def_exp_rent"));
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/categories/{catId} when category missing → 404 Not Found")
    void archiveCategory_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Category not found with id: missing in workspace: " + WS_ID))
                .when(categoryService).archiveCategory(eq(TEST_UID), eq(WS_ID), eq("missing"));

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/categories/missing")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/workspaces/{id}/categories/{catId} when MEMBER role → 403 Forbidden")
    void archiveCategory_memberRole_returns403() throws Exception {
        doThrow(new WorkspaceAccessDeniedException("Insufficient permissions: Member role cannot perform write operations"))
                .when(categoryService).archiveCategory(eq(TEST_UID), eq(WS_ID), eq("cat-100"));

        mockMvc.perform(delete("/api/v1/workspaces/" + WS_ID + "/categories/cat-100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }
}

