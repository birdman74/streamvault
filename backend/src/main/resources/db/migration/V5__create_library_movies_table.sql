CREATE TABLE library_movies (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id),
    tmdb_id      BIGINT       NOT NULL,
    title        VARCHAR(500) NOT NULL,
    release_year INTEGER,
    poster_url   VARCHAR(500),
    status       VARCHAR(30)  NOT NULL,
    added_at     TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_movies_user_tmdb UNIQUE (user_id, tmdb_id)
);
