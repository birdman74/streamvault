package com.streamvault.backend.tmdb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Query-string-bound request for {@code GET /api/tmdb/search}. Bound as an implicit model attribute
 * so a {@code @NotBlank} / {@code @Min} / {@code @Max} violation routes through the existing
 * {@code MethodArgumentNotValidException} handler and produces the same error envelope as the rest
 * of the API. {@code page} may be {@code null} (meaning "page 1"); the bounds only fire on a
 * non-null value. The {@code 500} ceiling is TMDB's own hard page limit.
 */
public record TmdbSearchRequest(
        @NotBlank(message = "Search query is required") String query,
        @Min(1) @Max(500) Integer page) {
}
