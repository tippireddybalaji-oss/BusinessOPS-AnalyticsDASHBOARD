package com.businessops.dashboard.dto;

import java.util.List;

/**
 * Standardized envelope for paginated collections.
 *
 * @param <T> element type
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {
    public static <T> PagedResponse<T> of(List<T> allItems, int page, int size) {
        int total = allItems.size();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int fromIndex = safePage * safeSize;

        if (fromIndex >= total) {
            int totalPages = (int) Math.ceil((double) total / safeSize);
            return new PagedResponse<>(List.of(), safePage, safeSize, total, totalPages, false);
        }

        int toIndex = Math.min(fromIndex + safeSize, total);
        List<T> pageContent = allItems.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil((double) total / safeSize);
        boolean hasMore = toIndex < total;

        return new PagedResponse<>(pageContent, safePage, safeSize, total, totalPages, hasMore);
    }
}
