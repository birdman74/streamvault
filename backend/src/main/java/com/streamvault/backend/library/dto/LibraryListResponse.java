package com.streamvault.backend.library.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/library}: one bounded, paginated page over the authenticated
 * user's merged movie/series library (AC-1, AC-6). {@code items} is never {@code null}; {@code []}
 * when nothing matches the active filters or {@code page} is beyond the last page (AC-8).
 */
public record LibraryListResponse(
        int page,
        int pageSize,
        int totalItems,
        int totalPages,
        List<LibraryItemResponse> items) {
}
