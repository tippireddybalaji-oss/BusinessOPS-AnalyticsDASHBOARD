package com.businessops.dashboard.dto;

import com.businessops.dashboard.model.Category;

import java.time.Instant;

/**
 * Standardized response contract for category data.
 */
public record CategoryResponse(
        String id,
        String workspaceId,
        String name,
        String type,
        boolean isDefault,
        String color,
        String icon,
        boolean isArchived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
) {
    public static CategoryResponse from(Category category) {
        if (category == null) {
            return null;
        }

        Instant archivedInstant = category.getArchivedAt() != null
                ? category.getArchivedAt().toDate().toInstant()
                : null;
        Instant createdInstant = category.getCreatedAt() != null
                ? category.getCreatedAt().toDate().toInstant()
                : Instant.now();
        Instant updatedInstant = category.getUpdatedAt() != null
                ? category.getUpdatedAt().toDate().toInstant()
                : Instant.now();

        return new CategoryResponse(
                category.getId(),
                category.getWorkspaceId(),
                category.getName(),
                category.getType(),
                category.isDefault(),
                category.getColor(),
                category.getIcon(),
                category.isArchived(),
                archivedInstant,
                createdInstant,
                updatedInstant,
                category.getCreatedBy(),
                category.getUpdatedBy()
        );
    }
}

