package com.streamvault.backend.tmdb.dto;

import java.util.List;

/**
 * A single bounded page of catalog results (AC-4). {@code page} / {@code totalPages} /
 * {@code totalResults} are passed through from TMDB's envelope so every response is explicitly
 * navigable. {@code results} is never {@code null} - a query that matches nothing is
 * {@code List.of()} (AC-5), not an error.
 */
public record TmdbResultPage(
        int page,
        int totalPages,
        int totalResults,
        List<TmdbResult> results) {
}
