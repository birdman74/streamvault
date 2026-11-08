package com.streamvault.backend.library;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.library.dto.LibraryListRequest;
import com.streamvault.backend.library.dto.LibraryListResponse;

import jakarta.validation.Valid;

/**
 * The first read path over the library built by STORY-007/STORY-008. One endpoint,
 * {@code GET /api/library}. The owning user id is always {@code principal.userId()} resolved from
 * the JWT - {@code LibraryListRequest} has no {@code userId} component, so there is nothing in
 * the request for a client to override (AC-7). The route is not added to {@code SecurityConfig}'s
 * {@code permitAll()} set, so it falls under {@code anyRequest().authenticated()} and reuses the
 * existing 401 entry point.
 */
@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    @GetMapping
    public ResponseEntity<LibraryListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid LibraryListRequest request) {
        return ResponseEntity.ok(libraryService.list(
                principal.userId(), request.type(), request.status(),
                request.page(), request.pageSize()));
    }
}
