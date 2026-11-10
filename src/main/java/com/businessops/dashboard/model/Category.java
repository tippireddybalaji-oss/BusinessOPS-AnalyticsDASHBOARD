package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.Objects;

/**
 * Domain entity representing an income or expense category within a workspace.
 *
 * <p>Stored at path: {@code workspaces/{workspaceId}/categories/{categoryId}}
 */
public class Category {

    private String id;
    private String workspaceId;
    private String name;
    private String type; // INCOME or EXPENSE
    private boolean isDefault;
    private String color;
    private String icon;
    private boolean isArchived;
    private Timestamp archivedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;

    public Category() {
    }

    public Category(String id, String workspaceId, String name, String type, boolean isDefault,
                    String color, String icon, boolean isArchived, Timestamp archivedAt,
                    Timestamp createdAt, Timestamp updatedAt, String createdBy, String updatedBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.type = type;
        this.isDefault = isDefault;
        this.color = color;
        this.icon = icon;
        this.isArchived = isArchived;
        this.archivedAt = archivedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean isArchived) {
        this.isArchived = isArchived;
    }

    public Timestamp getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Timestamp archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category category = (Category) o;
        return Objects.equals(id, category.id) && Objects.equals(workspaceId, category.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, workspaceId);
    }

    @Override
    public String toString() {
        return "Category{" +
                "id='" + id + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", isDefault=" + isDefault +
                ", isArchived=" + isArchived +
                '}';
    }
}

