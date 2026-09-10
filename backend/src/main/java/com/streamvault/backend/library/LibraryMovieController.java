package com.streamvault.backend.library;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.library.dto.AddMovieRequest;
import com.streamvault.backend.library.dto.LibraryMovieResponse;

import jakarta.validation.Valid;

/**
 * The first write path into a per-user library. One endpoint, {@code POST /api/library/movies}. The
 * owning user id is always {@code principal.userId()} resolved from the JWT - it is never read from
 * the request body ({@code AddMovieRequest} has no such component), so a client that sends a
 * {@code userId} is ignored (AC-6). The route is not added to {@code SecurityConfig}'s
 * {@code permitAll()} set, so it falls under {@code anyRequest().authenticated()} and reuses the
 * existing 401 entry point (AC-1).
 */
@RestController
@RequestMapping("/api/library/movies")
public class LibraryMovieController {

    private final LibraryMovieService libraryMovieService;

    public LibraryMovieController(LibraryMovieService libraryMovieService) {
        this.libraryMovieService = libraryMovieService;
    }

    @PostMapping
    public ResponseEntity<LibraryMovieResponse> add(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody AddMovieRequest request) {
        LibraryMovieResponse body =
                libraryMovieService.addMovie(principal.userId(), request.tmdbId(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
