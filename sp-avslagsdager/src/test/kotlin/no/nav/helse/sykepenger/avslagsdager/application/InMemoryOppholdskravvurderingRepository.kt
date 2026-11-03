package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId

internal class InMemoryOppholdskravvurderingRepository : OppholdskravvurderingRepository {
    private val vurderinger = mutableListOf<Oppholdskravvurdering>()

    val alle: List<Oppholdskravvurdering> get() = vurderinger.toList()

    override fun lagre(vurdering: Oppholdskravvurdering) {
        check(vurderinger.none { it.id == vurdering.id }) { "Oppholdskravvurdering ${vurdering.id} er allerede lagret" }
        vurderinger.add(vurdering)
    }

    override fun finn(id: OppholdskravvurderingId) = vurderinger.find { it.id == id }
}
