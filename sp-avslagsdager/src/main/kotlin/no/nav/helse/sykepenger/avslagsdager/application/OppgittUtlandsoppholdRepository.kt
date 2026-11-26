package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold

internal interface OppgittUtlandsoppholdRepository {
    /** Lagrer ikke en søknad som allerede er lagret. Oppholdene i en søknad endres aldri. */
    fun lagre(oppgittUtlandsopphold: OppgittUtlandsopphold)

    fun finnForPerson(fødselsnummer: String): List<OppgittUtlandsopphold>

    /** Hvilke av [meldingsreferanseIder] som allerede er lagret. */
    fun finnLagrede(meldingsreferanseIder: List<MeldingsreferanseId>): Set<MeldingsreferanseId>
}
