package com.businessops.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for {@code POST /api/v1/workspaces}.
 *
 * <p><strong>Security invariant:</strong> Neither {@code ownerUid} nor {@code role}
 * is accepted in this request. The authenticated user's verified UID is derived
 * exclusively from the Firebase ID token, and the creator is automatically assigned
 * the {@code OWNER} role.
 */
public record WorkspaceCreateRequest(
        @NotBlank(message = "Workspace name cannot be blank")
        @Size(min = 2, max = 100, message = "Workspace name must be between 2 and 100 characters")
        String name,

        String defaultCurrency
) {
}
