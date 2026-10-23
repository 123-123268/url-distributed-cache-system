CREATE TABLE short_urls
(
    id           BIGSERIAL PRIMARY KEY,
    short_key    VARCHAR(10) NOT NULL UNIQUE,
    original_url TEXT        NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);