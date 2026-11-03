package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.Objects;

/**
 * Domain entity representing a business client.
 *
 * <p>Stored at path: {@code workspaces/{workspaceId}/clients/{clientId}}
 */
public class Client {

    private String id;
    private String workspaceId;
    private String name;
    private String company;
    private String email;
    private String phone;
    private String status;
    private String notes;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String updatedBy;

    public Client() {
    }

    public Client(String id, String workspaceId, String name, String company, String email,
                  String phone, String status, String notes, Timestamp createdAt,
                  Timestamp updatedAt, String createdBy, String updatedBy) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.notes = notes;
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

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        if (!(o instanceof Client client)) return false;
        return Objects.equals(id, client.id) && Objects.equals(workspaceId, client.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, workspaceId);
    }

    @Override
    public String toString() {
        return "Client{" +
                "id='" + id + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", name='" + name + '\'' +
                ", company='" + company + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
