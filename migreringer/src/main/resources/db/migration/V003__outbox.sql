CREATE TABLE outbox
(
    id                  UUID PRIMARY KEY,
    sekvensnummer       BIGINT GENERATED ALWAYS AS IDENTITY,
    melding             JSONB       NOT NULL,
    fodselsnummer       TEXT        NOT NULL,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now(),
    publisert_tidspunkt TIMESTAMPTZ NULL
);

CREATE INDEX outbox_upublisert ON outbox (sekvensnummer) WHERE publisert_tidspunkt IS NULL;
