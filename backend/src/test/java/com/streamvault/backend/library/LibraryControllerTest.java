package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.JwtService;
import com.streamvault.backend.config.SecurityConfig;
import com.streamvault.backend.library.dto.LibraryItemResponse;
import com.streamvault.backend.library.dto.LibraryListResponse;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. {@code LibraryController},
 * {@code LibraryService}, {@code LibraryListResponse}, and {@code LibraryItemResponse} do not exist
 * yet; this test is expected to fail to compile until Dev implements them.
 *
 * <p>Mirrors {@code LibraryMovieControllerTest} exactly, per ADR-001: {@code addFilters = false},
 * {@code @Import(SecurityConfig.class)} for the {@code @AuthenticationPrincipal} argument resolver,
 * {@link WithMockAuthenticatedUser} to seed the principal. Covers the 200 body shape (AC-1, AC-2),
 * repeated {@code type=}/{@code status=} query-param binding into the service call (AC-3, AC-4,
 * AC-5), and the 400 mapping for an unrecognized enum value or an out-of-bounds {@code page} /
 * {@code pageSize} (shared {@code MethodArgumentNotValidException} envelope, no new exception
 * type). It cannot assert 401 because filters are disabled -- that is
 * {@code LibraryListEndpointsSecurityTest}'s job (AC-7, Layer 2).
 */
@WebMvcTest(LibraryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockAuthenticatedUser(userId = 7L, email = "viewer@example.com")
class LibraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryService libraryService;

    @MockitoBean
    private JwtService jwtService;

    private LibraryItemResponse seriesItem() {
        return new LibraryItemResponse(3L, "SERIES", 1399L, "Game of Thrones", 2011,
                "https://image.tmdb.org/t/p/w500/got.jpg", "CURRENTLY_WATCHING",
                Instant.parse("2026-11-05T09:00:00Z"));
    }

    private LibraryItemResponse movieItem() {
        return new LibraryItemResponse(1L, "MOVIE", 27205L, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg", "WATCHED",
                Instant.parse("2026-11-04T12:00:00Z"));
    }

    @Test
    void should_return200WithTheMergedListBody_when_libraryIsListed() throws Exception {
        when(libraryService.list(eq(7L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 2, 1, List.of(seriesItem(), movieItem())));

        mockMvc.perform(get("/api/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(3))
                .andExpect(jsonPath("$.items[0].type").value("SERIES"))
                .andExpect(jsonPath("$.items[0].tmdbId").value(1399))
                .andExpect(jsonPath("$.items[0].title").value("Game of Thrones"))
                .andExpect(jsonPath("$.items[0].year").value(2011))
                .andExpect(jsonPath("$.items[0].posterUrl").value("https://image.tmdb.org/t/p/w500/got.jpg"))
                .andExpect(jsonPath("$.items[0].status").value("CURRENTLY_WATCHING"))
                .andExpect(jsonPath("$.items[0].addedAt").value("2026-11-05T09:00:00Z"))
                .andExpect(jsonPath("$.items[1].type").value("MOVIE"));
    }

    @Test
    void should_returnAnEmptyItemsArray_when_theServiceReturnsNoMatches() throws Exception {
        when(libraryService.list(eq(7L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void should_threadAuthenticatedUserIdIntoTheService_when_listing() throws Exception {
        when(libraryService.list(eq(7L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library")).andExpect(status().isOk());

        verify(libraryService).list(eq(7L), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    void should_bindRepeatedTypeQueryParamsIntoAList_when_typeIsSuppliedMultipleTimes() throws Exception {
        when(libraryService.list(eq(7L), eq(List.of(LibraryItemType.MOVIE, LibraryItemType.SERIES)),
                eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library?type=MOVIE&type=SERIES")).andExpect(status().isOk());

        verify(libraryService).list(eq(7L), eq(List.of(LibraryItemType.MOVIE, LibraryItemType.SERIES)),
                eq(null), eq(null), eq(null));
    }

    @Test
    void should_bindRepeatedStatusQueryParamsIntoAList_when_statusIsSuppliedMultipleTimes() throws Exception {
        when(libraryService.list(eq(7L), eq(null),
                eq(List.of(WatchStatus.PLANNED, WatchStatus.WATCHED)), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library?status=PLANNED&status=WATCHED")).andExpect(status().isOk());

        verify(libraryService).list(eq(7L), eq(null),
                eq(List.of(WatchStatus.PLANNED, WatchStatus.WATCHED)), eq(null), eq(null));
    }

    @Test
    void should_bindPageAndPageSizeQueryParams_when_bothAreSupplied() throws Exception {
        when(libraryService.list(eq(7L), eq(null), eq(null), eq(2), eq(10)))
                .thenReturn(new LibraryListResponse(2, 10, 0, 0, List.of()));

        mockMvc.perform(get("/api/library?page=2&pageSize=10")).andExpect(status().isOk());

        verify(libraryService).list(eq(7L), eq(null), eq(null), eq(2), eq(10));
    }

    @Test
    void should_return400_when_typeIsNotARecognizedConstant() throws Exception {
        mockMvc.perform(get("/api/library?type=NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.type").exists());
    }

    @Test
    void should_return400_when_statusIsNotARecognizedConstant() throws Exception {
        mockMvc.perform(get("/api/library?status=SOON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.status").exists());
    }

    @Test
    void should_return400_when_pageIsLessThanOne() throws Exception {
        mockMvc.perform(get("/api/library?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.page").exists());
    }

    @Test
    void should_return400_when_pageSizeExceedsOneHundred() throws Exception {
        mockMvc.perform(get("/api/library?pageSize=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.pageSize").exists());
    }
}
