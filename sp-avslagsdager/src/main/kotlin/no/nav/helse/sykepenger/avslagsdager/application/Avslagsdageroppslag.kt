package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.VurdertDag
import no.nav.helse.sykepenger.avslagsdager.domain.gjeldendePerDag

/**
 * [vurderingIder] er vurderingene som gjelder for minst én dag i perioden, også oppfylte, i rekkefølgen de først
 * gjelder. [vurderteDager] er utfallet for hver dag i perioden som har en gjeldende vurdering, sortert på dato.
 * [avslagsdager] er dagene der den gjeldende vurderingen ikke er oppfylt. Den fjernes når Spleis bruker [vurderteDager].
 */
internal data class Avslagsdagerløsning(
    val vurderingIder: List<OppholdskravvurderingId>,
    val avslagsdager: List<Avslagsdag>,
    val vurderteDager: List<VurdertDag>,
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
        vurderteDager = gjeldende.map { (dag, vurdering) -> vurdering.vurdertDag(dag) },
    )
}
