package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.Objects;

/**
 * Domain entity representing a business workspace.
 *
 * <p>Stored at path: {@code workspaces/{workspaceId}}
 *
 * <h2>Data Fields</h2>
 * <ul>
 *   <li>{@code id}: Unique Firestore document ID</li>
 *   <li>{@code name}: Workspace business name</li>
 *   <li>{@code ownerUid}: Firebase UID of the creator and owner</li>
 *   <li>{@code defaultCurrency}: Currency ISO code (defaults to "INR")</li>
 *   <li>{@code createdAt}: Server timestamp of creation</li>
 *   <li>{@code updatedAt}: Server timestamp of latest update</li>
 * </ul>
 */
public class Workspace {

    private String id;
    private String name;
    private String ownerUid;
    private String defaultCurrency;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Workspace() {
    }

    public Workspace(String id, String name, String ownerUid, String defaultCurrency, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.name = name;
        this.ownerUid = ownerUid;
        this.defaultCurrency = defaultCurrency;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(String ownerUid) {
        this.ownerUid = ownerUid;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace workspace)) return false;
        return Objects.equals(id, workspace.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Workspace{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", ownerUid='" + ownerUid + '\'' +
                ", defaultCurrency='" + defaultCurrency + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
