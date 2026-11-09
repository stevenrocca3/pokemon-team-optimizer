-- Cups come from PvPoke's open data, pulled nightly (CupSync). Every pull is logged; a good one stores each live
-- format, rules and meta as JSON (CupFormat). The Team Builder uses the newest good pull, so a bad night keeps
-- yesterday's Cups. Only the last few good pulls keep their formats.
CREATE SEQUENCE cup_sync_run_seq;
CREATE SEQUENCE cup_format_seq INCREMENT BY 50;

CREATE TABLE cup_sync_run (
    id               BIGINT PRIMARY KEY,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    succeeded        BOOLEAN                  NOT NULL,
    source_timestamp VARCHAR(64),
    notes            TEXT                     NOT NULL
);

CREATE TABLE cup_format (
    id         BIGINT PRIMARY KEY,
    run_id     BIGINT       NOT NULL REFERENCES cup_sync_run (id),
    format_key VARCHAR(128) NOT NULL,
    title      VARCHAR(255) NOT NULL,
    cp_cap     INTEGER      NOT NULL,
    payload    TEXT         NOT NULL,
    UNIQUE (run_id, format_key)
);
