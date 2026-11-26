CREATE TABLE oppgitt_utlandsopphold
(
    meldingsreferanse_id      UUID        PRIMARY KEY,
    soknad_id                 UUID        NOT NULL,
    korrigerer                UUID,
    fødselsnummer             VARCHAR(11) NOT NULL,
    yrkesaktivitetstype       TEXT        NOT NULL,
    organisasjonsnummer       TEXT,
    utlandsoppholdsperioder   JSONB       NOT NULL,
    hentet_tidspunkt          TIMESTAMPTZ NOT NULL,
    opprettet                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_oppgitt_utlandsopphold_fodselsnummer ON oppgitt_utlandsopphold (fødselsnummer);

CREATE TABLE grunnlag_for_lopende_vilkar
(
    id            UUID        PRIMARY KEY,
    fødselsnummer VARCHAR(11) NOT NULL,
    fom           DATE        NOT NULL,
    tom           DATE        NOT NULL,
    grunnlag      JSONB       NOT NULL,
    opprettet     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_grunnlag_for_lopende_vilkar_periode ON grunnlag_for_lopende_vilkar (fødselsnummer, fom, tom, opprettet);
