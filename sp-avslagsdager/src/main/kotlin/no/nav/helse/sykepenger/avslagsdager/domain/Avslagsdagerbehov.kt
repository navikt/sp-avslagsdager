package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

/** Navnet på dagtypen i sykdomstidslinja til Spleis (`JsonDagType`). */
@JvmInline
internal value class Dagtype(
    val navn: String,
) {
    // Arbeidsgiverdager er sykepenger arbeidsgiveren betaler, og foreldede sykedager gir rett selv om de ikke utbetales
    val girRettTilSykepenger get() = navn in dagtyperMedRettTilSykepenger

    private companion object {
        val dagtyperMedRettTilSykepenger = setOf("SYKEDAG", "ARBEIDSGIVERDAG", "FORELDET_SYKEDAG")
    }
}

internal data class Sykdomsdag(
    val dato: LocalDate,
    val dagtype: Dagtype,
)

internal data class YrkesaktivitetIBehov(
    val yrkesaktivitet: Yrkesaktivitet,
    val sykdomstidslinje: List<Sykdomsdag>,
    val søknadMeldingsreferanseIder: List<MeldingsreferanseId>,
) {
    fun harRettTilSykepenger(dato: LocalDate) = sykdomstidslinje.any { it.dato == dato && it.dagtype.girRettTilSykepenger }
}

/** Behovet `Avslagsdager` fra Spleis, for beregningsperioden. */
internal data class Avslagsdagerbehov(
    val fødselsnummer: String,
    val periode: Periode,
    val yrkesaktiviteter: List<YrkesaktivitetIBehov>,
) {
    fun harDagMedRettTilSykepenger(dato: LocalDate) = yrkesaktiviteter.any { it.harRettTilSykepenger(dato) }
}
