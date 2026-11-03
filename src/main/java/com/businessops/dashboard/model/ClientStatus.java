package com.businessops.dashboard.model;

/**
 * Lifecycle status of a client within a workspace.
 */
public enum ClientStatus {
    ACTIVE,
    INACTIVE,
    LEAD;

    public static ClientStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return ACTIVE;
        }
        try {
            return ClientStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid client status: '" + status + "'. Allowed values: ACTIVE, INACTIVE, LEAD"
            );
        }
    }
}
