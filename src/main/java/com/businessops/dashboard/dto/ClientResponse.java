package com.businessops.dashboard.dto;

import com.businessops.dashboard.model.Client;

import java.time.Instant;

/**
 * Standardized response contract for client data.
 */
public record ClientResponse(
        String id,
        String workspaceId,
        String name,
        String company,
        String email,
        String phone,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
    public static ClientResponse from(Client client) {
        Instant createdInstant = client.getCreatedAt() != null
                ? client.getCreatedAt().toDate().toInstant()
                : Instant.now();
        Instant updatedInstant = client.getUpdatedAt() != null
                ? client.getUpdatedAt().toDate().toInstant()
                : Instant.now();

        return new ClientResponse(
                client.getId(),
                client.getWorkspaceId(),
                client.getName(),
                client.getCompany(),
                client.getEmail(),
                client.getPhone(),
                client.getStatus(),
                client.getNotes(),
                createdInstant,
                updatedInstant,
                client.getCreatedBy(),
                client.getUpdatedBy()
        );
    }
}
