package com.streamvault.backend.library;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.library.dto.AddSeriesRequest;
import com.streamvault.backend.library.dto.LibrarySeriesResponse;

import jakarta.validation.Valid;

/**
 * The second write path into a per-user library, and the first that persists a tree rather than a
 * single row. One endpoint, {@code POST /api/library/series}. The owning user id is always
 * {@code principal.userId()} resolved from the JWT - it is never read from the request body
 * ({@code AddSeriesRequest} has no such component), so a client that sends a {@code userId} is
 * ignored (AC-7). The route is not added to {@code SecurityConfig}'s {@code permitAll()} set, so it
 * falls under {@code anyRequest().authenticated()} and reuses the existing 401 entry point (AC-1).
 */
@RestController
@RequestMapping("/api/library/series")
public class LibrarySeriesController {

    private final LibrarySeriesService librarySeriesService;

    public LibrarySeriesController(LibrarySeriesService librarySeriesService) {
        this.librarySeriesService = librarySeriesService;
    }

    @PostMapping
    public ResponseEntity<LibrarySeriesResponse> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddSeriesRequest request) {
        LibrarySeriesResponse body = librarySeriesService.addSeries(principal.userId(), request.tmdbId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
