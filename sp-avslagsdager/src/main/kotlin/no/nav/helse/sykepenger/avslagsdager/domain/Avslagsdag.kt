package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

internal data class Avslagsdag(
    val vurderingId: OppholdskravvurderingId,
    val dato: LocalDate,
    val vilkårskoder: List<Vilkårskode>,
)
