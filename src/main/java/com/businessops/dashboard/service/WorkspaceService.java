package com.businessops.dashboard.service;

import com.businessops.dashboard.dto.WorkspaceCreateRequest;
import com.businessops.dashboard.dto.WorkspaceResponse;
import com.businessops.dashboard.exception.ResourceNotFoundException;
import com.businessops.dashboard.exception.WorkspaceAccessDeniedException;
import com.businessops.dashboard.model.Workspace;
import com.businessops.dashboard.model.WorkspaceMember;
import com.businessops.dashboard.model.WorkspaceRole;
import com.businessops.dashboard.repository.WorkspaceRepository;
import com.businessops.dashboard.validation.SupportedCurrencies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service orchestrating workspace management, validation, and server-side authorization.
 *
 * <p><strong>Authorization invariants:</strong>
 * <ul>
 *   <li>The creator always receives the {@link WorkspaceRole#OWNER} role.</li>
 *   <li>Workspace reads require either explicit membership in {@code members/{uid}} or matching {@code ownerUid}.</li>
 *   <li>Cross-workspace data access is strictly rejected with {@link WorkspaceAccessDeniedException} (403).</li>
 * </ul>
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Creates a new business workspace and automatically provisions the owner's membership.
     *
     * @param authenticatedUid verified Firebase UID of the creator
     * @param request creation request with validated name and optional currency
     * @return the created workspace response
     */
    public WorkspaceResponse createWorkspace(String authenticatedUid, WorkspaceCreateRequest request) {
        String normalizedCurrency = SupportedCurrencies.normalizeOrDefault(request.defaultCurrency());

        Workspace workspace = new Workspace();
        workspace.setName(request.name().trim());
        workspace.setOwnerUid(authenticatedUid); // Enforce ownerUid strictly from authenticated principal
        workspace.setDefaultCurrency(normalizedCurrency);

        WorkspaceMember ownerMember = new WorkspaceMember();
        ownerMember.setUid(authenticatedUid);
        ownerMember.setRole(WorkspaceRole.OWNER); // Automatically set OWNER role

        Workspace created = workspaceRepository.createWorkspace(workspace, ownerMember);
        log.info("Workspace created: id='{}', name='{}', owner='{}'", created.getId(), created.getName(), authenticatedUid);

        return WorkspaceResponse.from(created, WorkspaceRole.OWNER);
    }

    /**
     * Retrieves workspace details by ID after validating user access.
     *
     * @param authenticatedUid verified Firebase UID of the requesting user
     * @param workspaceId target workspace ID
     * @return the workspace response if authorized
     * @throws ResourceNotFoundException if the workspace does not exist (404)
     * @throws WorkspaceAccessDeniedException if the user is not a member or owner (403)
     */
    public WorkspaceResponse getWorkspaceById(String authenticatedUid, String workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with id: " + workspaceId));

        // Enforce server-side authorization: user must be an owner or active member
        Optional<WorkspaceMember> memberOpt = workspaceRepository.findMember(workspaceId, authenticatedUid);
        WorkspaceRole role;

        if (memberOpt.isPresent()) {
            role = memberOpt.get().getRole();
        } else if (authenticatedUid.equals(workspace.getOwnerUid())) {
            role = WorkspaceRole.OWNER;
        } else {
            log.warn("Access denied: user {} attempted to access workspace {} without membership", authenticatedUid, workspaceId);
            throw new WorkspaceAccessDeniedException("You do not have permission to access workspace: " + workspaceId);
        }

        return WorkspaceResponse.from(workspace, role);
    }

    /**
     * Lists all workspaces accessible to the user (owned or member).
     *
     * @param authenticatedUid verified Firebase UID of the requesting user
     * @return list of accessible workspaces
     */
    public List<WorkspaceResponse> listWorkspacesForUser(String authenticatedUid) {
        List<Workspace> workspaces = workspaceRepository.findWorkspacesByMemberUid(authenticatedUid);
        List<WorkspaceResponse> responses = new ArrayList<>();

        for (Workspace ws : workspaces) {
            WorkspaceRole role = WorkspaceRole.MEMBER;
            if (authenticatedUid.equals(ws.getOwnerUid())) {
                role = WorkspaceRole.OWNER;
            } else {
                Optional<WorkspaceMember> memberOpt = workspaceRepository.findMember(ws.getId(), authenticatedUid);
                if (memberOpt.isPresent()) {
                    role = memberOpt.get().getRole();
                }
            }
            responses.add(WorkspaceResponse.from(ws, role));
        }

        return responses;
    }
}
