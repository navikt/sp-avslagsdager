package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.Instant
import java.util.UUID

@JvmInline
internal value class MeldingsreferanseId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

/** ID-en søknaden fikk hos flex. En korrigerende søknad peker på søknaden den korrigerer med denne. */
@JvmInline
internal value class SøknadId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

internal data class Yrkesaktivitet(
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
)

/**
 * Periodene med opphold utenfor EØS som personen har oppgitt i én søknad, med søknaden som kilde (ADR 0021). Søknaden
 * lagres ikke. [søknadId] er ID-en søknaden fikk hos flex, og [korrigerer] er [søknadId] til søknaden den korrigerer.
 * En korrigert søknad har en ny meldingsreferanse-ID, så [utlandsoppholdsperioder] i en lagret søknad endres aldri.
 */
internal data class OppgittUtlandsopphold(
    val meldingsreferanseId: MeldingsreferanseId,
    val søknadId: SøknadId,
    val korrigerer: SøknadId?,
    val fødselsnummer: String,
    val yrkesaktivitet: Yrkesaktivitet,
    val utlandsoppholdsperioder: List<Periode>,
    val hentetTidspunkt: Instant,
)
