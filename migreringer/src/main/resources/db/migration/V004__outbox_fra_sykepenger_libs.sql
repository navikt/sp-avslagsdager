-- Outboxen fra sykepenger-libs publiserer melding-kolonnen uendret. Upubliserte rader i det gamle formatet ville gått
-- ut på rapiden som de er, så vi stopper migreringen i stedet.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM outbox WHERE publisert_tidspunkt IS NULL) THEN
            RAISE EXCEPTION 'Outboxen har upubliserte meldinger i det gamle formatet';
        END IF;
    END
$$;

ALTER TABLE outbox ADD CONSTRAINT outbox_sekvensnummer_key UNIQUE (sekvensnummer);

ALTER INDEX outbox_upublisert RENAME TO outbox_upublisert_idx;
