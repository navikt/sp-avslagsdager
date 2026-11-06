package no.nav.helse.sykepenger.avslagsdager.infra.db

import kotliquery.Session
import no.nav.helse.sykepenger.avslagsdager.application.Outbox
import no.nav.sykepenger.libs.outbox.Outbox as SykepengerLibsOutbox

internal class PostgresOutbox(
    private val session: Session,
) : Outbox {
    override fun leggTil(
        fødselsnummer: String,
        melding: String,
    ) {
        SykepengerLibsOutbox.leggTil(
            connection = session.connection.underlying,
            fødselsnummer = fødselsnummer,
            melding = melding,
        )
    }
}
