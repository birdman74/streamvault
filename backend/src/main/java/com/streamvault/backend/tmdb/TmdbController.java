package com.streamvault.backend.tmdb;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.tmdb.dto.TmdbBrowseRequest;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.dto.TmdbSearchRequest;

import jakarta.validation.Valid;

/**
 * Read-only catalog resource that proxies TMDB. Both endpoints return the same
 * {@link TmdbResultPage} envelope so one UI component can render either (AC-3).
 *
 * <p>{@code principal} is resolved only so the endpoint sits behind authentication - {@code userId()}
 * is never read, nothing is user-scoped (AC-7). The routes are not added to {@code SecurityConfig}'s
 * {@code permitAll()} set, so they fall under {@code anyRequest().authenticated()} and reuse the
 * existing 401 entry point (AC-8). The request records bind from the query string as implicit model
 * attributes; a {@code @Valid} failure routes through the existing
 * {@code MethodArgumentNotValidException} handler (400 with the shared {@code fields} envelope).
 */
@RestController
@RequestMapping("/api/tmdb")
public class TmdbController {

    private final TmdbCatalogService tmdbCatalogService;

    public TmdbController(TmdbCatalogService tmdbCatalogService) {
        this.tmdbCatalogService = tmdbCatalogService;
    }

    @GetMapping("/search")
    public ResponseEntity<TmdbResultPage> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid TmdbSearchRequest request) {
        return ResponseEntity.ok(tmdbCatalogService.search(request.query(), request.page()));
    }

    @GetMapping("/browse")
    public ResponseEntity<TmdbResultPage> browse(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid TmdbBrowseRequest request) {
        return ResponseEntity.ok(tmdbCatalogService.browse(request.list(), request.page()));
    }
}
