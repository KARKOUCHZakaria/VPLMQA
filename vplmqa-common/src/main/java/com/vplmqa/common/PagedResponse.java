package com.vplmqa.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Wrapper around Spring {@link Page} with pagination metadata.
 *
 * @param <T>           the item type
 * @param items         the page content
 * @param pageNumber    the current page number (0-based)
 * @param pageSize      the page size
 * @param totalElements the total number of elements
 * @param totalPages    the total number of pages
 */
public record PagedResponse<T>(
        List<T> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages) {
    /**
     * Converts a {@link Page} into a {@link PagedResponse}.
     *
     * @param page the Spring page
     * @param <T>  the item type
     * @return a PagedResponse instance
     */
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
