package com.businessops.dashboard.model;

import com.google.cloud.Timestamp;

import java.util.Objects;

/**
 * Domain entity representing an income or expense transaction.
 *
 * <p>Stored at path: {@code workspaces/{workspaceId}/transactions/{transactionId}}
 * <p>All financial arithmetic uses {@code amountMinor} as a 64-bit signed integer.
 */
public class Transaction {

    private String id;
    private String workspaceId;
    private String type; // INCOME or EXPENSE
    private Long amountMinor; // in minor currency units (paise, cents, yen)
    private String currency; // ISO-4217, e.g. INR, USD, JPY
    private String categoryId;
    private String categoryName;
    private String categoryColor;
    private String clientId;
    private String clientName;
    private String description;
    private Timestamp transactionDate;
    private String paymentMethod;
    private String status;
    private String createdByUid;
    private String updatedByUid;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Transaction() {
    }

    public Transaction(String id, String workspaceId, String type, Long amountMinor, String currency,
                       String categoryId, String categoryName, String categoryColor,
                       String clientId, String clientName, String description,
                       Timestamp transactionDate, String paymentMethod, String status,
                       String createdByUid, String updatedByUid, Timestamp createdAt, Timestamp updatedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.type = type;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryColor = categoryColor;
        this.clientId = clientId;
        this.clientName = clientName;
        this.description = description;
        this.transactionDate = transactionDate;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdByUid = createdByUid;
        this.updatedByUid = updatedByUid;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(Long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryColor() {
        return categoryColor;
    }

    public void setCategoryColor(String categoryColor) {
        this.categoryColor = categoryColor;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Timestamp transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedByUid() {
        return createdByUid;
    }

    public void setCreatedByUid(String createdByUid) {
        this.createdByUid = createdByUid;
    }

    public String getUpdatedByUid() {
        return updatedByUid;
    }

    public void setUpdatedByUid(String updatedByUid) {
        this.updatedByUid = updatedByUid;
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
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(id, that.id) && Objects.equals(workspaceId, that.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, workspaceId);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id='" + id + '\'' +
                ", workspaceId='" + workspaceId + '\'' +
                ", type='" + type + '\'' +
                ", amountMinor=" + amountMinor +
                ", currency='" + currency + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

