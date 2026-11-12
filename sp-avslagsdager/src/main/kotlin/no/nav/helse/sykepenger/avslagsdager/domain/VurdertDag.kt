package no.nav.helse.sykepenger.avslagsdager.domain

import java.time.LocalDate

internal enum class LøpendeVilkår {
    OPPHOLDSKRAV,
}

/** Utfallet av den gjeldende vurderingen for én dag og ett løpende vilkår (ADR 0016). */
internal data class VurdertDag(
    val vurderingId: OppholdskravvurderingId,
    val vilkår: LøpendeVilkår,
    val dato: LocalDate,
    val utfall: Utfall,
    val vilkårskoder: List<Vilkårskode>,
)
