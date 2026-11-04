package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Avslagsdag
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId

internal sealed interface AvslagsdagerResultat {
    data class Funnet(
        val avslagsdager: List<Avslagsdag>,
    ) : AvslagsdagerResultat

    /** Vurderinger som ikke finnes, eller som gjelder en annen person. De to tilfellene skilles ikke. */
    data class UkjenteVurderinger(
        val vurderingIder: List<OppholdskravvurderingId>,
    ) : AvslagsdagerResultat
}

/**
 * Avslagsdagene i vurderingene, i rekkefølgen ID-ene er gitt. Spleis avgjør selv hvilken vurdering som gjelder for
 * hver dag (ADR 0005). Er én av vurderingene ukjent for personen, gis ingen avslagsdager, så Spleis aldri bygger på et
 * delvis svar.
 */
internal fun OppholdskravvurderingRepository.avslagsdager(
    fødselsnummer: String,
    vurderingIder: List<OppholdskravvurderingId>,
): AvslagsdagerResultat {
    val vurderinger = vurderingIder.distinct().associateWith { id -> finn(id)?.takeIf { it.fødselsnummer == fødselsnummer } }
    val ukjente = vurderinger.filterValues { it == null }.keys
    if (ukjente.isNotEmpty()) return AvslagsdagerResultat.UkjenteVurderinger(ukjente.toList())
    return AvslagsdagerResultat.Funnet(vurderinger.values.filterNotNull().flatMap { it.avslagsdager() })
}
