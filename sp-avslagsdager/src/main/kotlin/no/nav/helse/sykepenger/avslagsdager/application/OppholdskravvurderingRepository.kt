package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId

internal interface OppholdskravvurderingRepository {
    fun lagre(vurdering: Oppholdskravvurdering)

    fun finn(id: OppholdskravvurderingId): Oppholdskravvurdering?
}
