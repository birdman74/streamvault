package com.streamvault.backend.tmdb.exception;

/**
 * Raised by the TMDB gateway when the upstream call cannot be completed: any
 * {@code RestClientException} (transport failure, connection reset, timeout) or any non-2xx
 * response (401 for a bad key, 429 rate limit, 5xx). Mapped by {@code GlobalExceptionHandler} to a
 * 502 with a single fixed user-facing message (AC-6).
 *
 * <p>Per the project "no swallowed exceptions" rule the originating cause is attached via the
 * {@link #TmdbUnavailableException(Throwable)} constructor and logged by the handler; it is never
 * placed in the response body. The {@link #TmdbUnavailableException(String)} constructor exists for
 * callers that have a description but no throwable cause.
 */
public class TmdbUnavailableException extends RuntimeException {

    public TmdbUnavailableException(Throwable cause) {
        super("TMDB request failed", cause);
    }

    public TmdbUnavailableException(String detail) {
        super(detail);
    }
}
