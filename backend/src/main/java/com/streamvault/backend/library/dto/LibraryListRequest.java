package com.streamvault.backend.library.dto;

import java.util.List;

import com.streamvault.backend.library.LibraryItemType;
import com.streamvault.backend.library.WatchStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Query-string-bound request for {@code GET /api/library}. {@code type} / {@code status} carry
 * no Bean Validation constraint of their own - an unrecognized value in either is a binding
 * failure at the HTTP layer (repeated query param bound to a {@code List<Enum>}), not a Bean
 * Validation concern at this layer, mirroring {@code TmdbBrowseRequest.list}'s precedent.
 * {@code null} or an empty list for either means "no filter" (AC-3, AC-4).
 */
public record LibraryListRequest(
        List<LibraryItemType> type,
        List<WatchStatus> status,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {
}
