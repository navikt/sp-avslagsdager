package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.MeldingsreferanseId
import no.nav.helse.sykepenger.avslagsdager.domain.OppgittUtlandsopphold
import no.nav.helse.sykepenger.avslagsdager.domain.Yrkesaktivitet

/** Henter oppholdene en søknad oppgir. Kaster [SøknadenKunneIkkeHentes] når søknaden ikke kan hentes. */
internal interface Søknadsoppslag {
    fun hent(
        meldingsreferanseId: MeldingsreferanseId,
        yrkesaktivitet: Yrkesaktivitet,
        fødselsnummer: String,
    ): OppgittUtlandsopphold
}

internal class SøknadenKunneIkkeHentes(
    val meldingsreferanseId: MeldingsreferanseId,
    cause: Throwable,
) : RuntimeException("Søknaden $meldingsreferanseId kunne ikke hentes", cause)
