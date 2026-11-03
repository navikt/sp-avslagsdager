CREATE TABLE oppholdskravvurdering
(
    id                                UUID        PRIMARY KEY,
    fødselsnummer                     VARCHAR(11) NOT NULL,
    behandling_id                     UUID        NOT NULL,
    eos_borger                        BOOLEAN     NOT NULL,
    opphold_i_godkjent_omrade         BOOLEAN     NOT NULL,
    unntak_andre_ledd                 TEXT,
    godkjent_opphold_inntil_fire_uker BOOLEAN,
    utfall                            TEXT        NOT NULL,
    fritekstbegrunnelse               TEXT        NOT NULL,
    journalpost_id                    TEXT[]      NOT NULL,
    saksbehandler_ident               TEXT        NOT NULL,
    vurdert_tidspunkt                 TIMESTAMPTZ NOT NULL,
    opprettet                         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_oppholdskravvurdering_fodselsnummer ON oppholdskravvurdering (fødselsnummer);

CREATE TABLE oppholdskravvurdering_dag
(
    vurdering_id UUID NOT NULL REFERENCES oppholdskravvurdering (id),
    dato         DATE NOT NULL,
    PRIMARY KEY (vurdering_id, dato)
);

CREATE TABLE oppholdskravvurdering_vilkarsvurdering
(
    vurdering_id UUID NOT NULL REFERENCES oppholdskravvurdering (id),
    vilkårskode  TEXT NOT NULL,
    utfall       TEXT NOT NULL,
    lov          TEXT NOT NULL,
    paragraf     TEXT NOT NULL,
    avsnitt      INT,
    setning      INT,
    bokstav      TEXT,
    i_kraft_fra  DATE NOT NULL,
    PRIMARY KEY (vurdering_id, vilkårskode)
);
