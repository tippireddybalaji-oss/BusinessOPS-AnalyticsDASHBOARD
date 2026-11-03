package com.businessops.dashboard.controller;

import com.businessops.dashboard.dto.WorkspaceCreateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.security.SecurityUtils;
import com.businessops.dashboard.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for workspace management under the {@code /api/v1/workspaces} path.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/workspaces} - Creates a workspace; creator automatically becomes OWNER.</li>
 *   <li>{@code GET /api/v1/workspaces} - Lists all workspaces accessible to the authenticated user.</li>
 *   <li>{@code GET /api/v1/workspaces/{id}} - Retrieves workspace details after verifying authorization.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SecurityUtils securityUtils;

    public WorkspaceController(WorkspaceService workspaceService, SecurityUtils securityUtils) {
        this.workspaceService = workspaceService;
        this.securityUtils = securityUtils;
    }

    /**
     * Creates a new business workspace.
     *
     * <p>Requires a valid Firebase Bearer token. The authenticated user is set
     * as the owner and granted the {@code OWNER} role.
     *
     * @param request creation payload containing workspace name and optional currency
     * @return 201 Created with the created workspace response
     */
    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @Valid @RequestBody WorkspaceCreateRequest request) {
        String uid = securityUtils.getAuthenticatedUid();
        WorkspaceResponse response = workspaceService.createWorkspace(uid, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lists all workspaces accessible to the caller.
     *
     * @return 200 OK with list of workspaces (or empty list if none exist)
     */
    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> listWorkspaces() {
        String uid = securityUtils.getAuthenticatedUid();
        List<WorkspaceResponse> workspaces = workspaceService.listWorkspacesForUser(uid);
        return ResponseEntity.ok(workspaces);
    }

    /**
     * Retrieves workspace details by ID.
     *
     * @param id workspace ID
     * @return 200 OK with workspace details if authorized
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponse> getWorkspace(@PathVariable("id") String id) {
        String uid = securityUtils.getAuthenticatedUid();
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(uid, id);
        return ResponseEntity.ok(workspace);
    }
}
