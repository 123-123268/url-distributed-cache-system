-- V2 removes the BIGSERIAL default. This migration also removes its now-unused sequence
-- for databases that were initialized before Snowflake ID generation was introduced.
DROP SEQUENCE IF EXISTS short_urls_id_seq;
