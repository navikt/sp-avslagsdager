package no.nav.helse.sykepenger.avslagsdager.infra.db

import kotliquery.Session
import kotliquery.sessionOf
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.TransaksjonProvider
import javax.sql.DataSource

internal class PostgresTransaksjonProvider(
    private val dataSource: DataSource,
) : TransaksjonProvider<Transaksjonskontekst> {
    override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T =
        sessionOf(dataSource).use { session ->
            session.transaction { transaksjon -> block(PostgresTransaksjonskontekst(transaksjon)) }
        }
}

@Suppress("UNUSED_PARAMETER")
private class PostgresTransaksjonskontekst(
    session: Session,
) : Transaksjonskontekst
