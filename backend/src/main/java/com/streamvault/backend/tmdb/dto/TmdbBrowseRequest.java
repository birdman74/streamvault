package com.streamvault.backend.tmdb.dto;

import com.streamvault.backend.tmdb.TmdbBrowseList;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Query-string-bound request for {@code GET /api/tmdb/browse}. {@code list} may be {@code null}
 * (meaning {@code POPULAR}); Spring's default {@code String} to enum binding is case-sensitive by
 * constant name, so an unrecognized value becomes a binding error surfaced as a 400 with a
 * {@code fields.list} entry. {@code page} follows the same {@code @Min(1)} / {@code @Max(500)} rule
 * as search.
 */
public record TmdbBrowseRequest(
        TmdbBrowseList list,
        @Min(1) @Max(500) Integer page) {
}
