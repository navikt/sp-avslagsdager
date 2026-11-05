package no.nav.helse.sykepenger.avslagsdager.application

import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode

internal interface OppholdskravvurderingRepository {
    fun lagre(vurdering: Oppholdskravvurdering)

    fun finn(id: OppholdskravvurderingId): Oppholdskravvurdering?

    /** Vurderingene for personen som omfatter minst én dag i [periode]. */
    fun finnForPeriode(
        fødselsnummer: String,
        periode: Periode,
    ): List<Oppholdskravvurdering>
}
