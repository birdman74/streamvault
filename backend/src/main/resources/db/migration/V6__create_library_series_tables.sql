CREATE TABLE library_series (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    tmdb_id        BIGINT       NOT NULL,
    title          VARCHAR(500) NOT NULL,
    first_air_year INTEGER,
    poster_url     VARCHAR(500),
    added_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_series_user_tmdb UNIQUE (user_id, tmdb_id)
);

CREATE TABLE library_seasons (
    id                 BIGSERIAL PRIMARY KEY,
    library_series_id  BIGINT  NOT NULL REFERENCES library_series (id) ON DELETE CASCADE,
    season_number      INTEGER NOT NULL,
    CONSTRAINT uq_library_seasons_series_number UNIQUE (library_series_id, season_number)
);

CREATE TABLE library_episodes (
    id                 BIGSERIAL PRIMARY KEY,
    library_season_id  BIGINT       NOT NULL REFERENCES library_seasons (id) ON DELETE CASCADE,
    episode_number     INTEGER      NOT NULL,
    title              VARCHAR(500),
    status             VARCHAR(30)  NOT NULL,
    CONSTRAINT uq_library_episodes_season_number UNIQUE (library_season_id, episode_number)
);
