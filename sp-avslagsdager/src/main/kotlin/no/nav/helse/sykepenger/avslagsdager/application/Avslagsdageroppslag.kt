package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.VurdertDag
import no.nav.helse.sykepenger.avslagsdager.domain.gjeldendePerDag

/** [vurderteDager] er utfallet for hver dag i perioden som har en gjeldende vurdering, sortert på dato. */
internal data class Avslagsdagerløsning(
    val vurderteDager: List<VurdertDag>,
)

/** Spleis spør med beregningsperioden og lagrer vurderingene per dag på endringen (ADR 0005). */
internal fun OppholdskravvurderingRepository.avslagsdager(
    fødselsnummer: String,
    periode: Periode,
): Avslagsdagerløsning {
    val gjeldende = finnForPeriode(fødselsnummer, periode).gjeldendePerDag(periode)
    return Avslagsdagerløsning(
        vurderteDager = gjeldende.map { (dag, vurdering) -> vurdering.vurdertDag(dag) },
    )
}
