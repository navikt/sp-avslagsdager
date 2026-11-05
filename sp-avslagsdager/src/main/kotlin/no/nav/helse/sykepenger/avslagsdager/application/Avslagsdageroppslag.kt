package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.gjeldendePerDag

/**
 * [vurderingIder] er vurderingene som gjelder for minst én dag i perioden, også oppfylte, i rekkefølgen de først
 * gjelder. [avslagsdager] er dagene der den gjeldende vurderingen ikke er oppfylt, sortert på dato.
 */
internal data class Avslagsdagerløsning(
    val vurderingIder: List<OppholdskravvurderingId>,
    val avslagsdager: List<Avslagsdag>,
)

/** Spleis spør med beregningsperioden og lagrer ID-ene fra løsningen på endringen (ADR 0005). */
internal fun OppholdskravvurderingRepository.avslagsdager(
    fødselsnummer: String,
    periode: Periode,
): Avslagsdagerløsning {
    val gjeldende = finnForPeriode(fødselsnummer, periode).gjeldendePerDag(periode)
    return Avslagsdagerløsning(
        vurderingIder = gjeldende.values.map { it.id }.distinct(),
        avslagsdager = gjeldende.mapNotNull { (dag, vurdering) -> vurdering.avslagsdag(dag) },
    )
}
