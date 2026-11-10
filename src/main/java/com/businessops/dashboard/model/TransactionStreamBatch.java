package com.businessops.dashboard.model;

import com.google.cloud.firestore.QueryDocumentSnapshot;
import java.util.List;

/**
 * Represents a single cursor-paginated page of selectively projected transaction items.
 *
 * @param items batch of projected transaction stream items
 * @param lastDocument last document snapshot in the batch for cursor continuation (null if items is empty)
 * @param hasMore whether additional documents exist beyond this batch in the query window
 */
public record TransactionStreamBatch(
    List<TransactionStreamItem> items,
    QueryDocumentSnapshot lastDocument,
    boolean hasMore
) {}

