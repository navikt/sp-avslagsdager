@file:UseContextualSerialization(LocalDate::class, UUID::class)

package no.nav.helse.sykepenger.avslagsdager.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate
import java.util.UUID

@Serializable
internal data class ApiGrunnlagForLøpendeVilkårResponse(
    val grunnlagForLøpendeVilkårId: UUID,
    val periode: ApiPeriode,
    val dagerMedOppgittUtlandsopphold: List<ApiDagMedKilder>,
    val dagerTilVurdering: List<ApiDagMedKilder>,
    val vurderteDager: List<ApiVurdertDag>,
)

@Serializable
internal data class ApiPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

@Serializable
internal data class ApiDagMedKilder(
    val dato: LocalDate,
    val kilder: List<ApiKilde>,
)

@Serializable
internal data class ApiKilde(
    val søknadMeldingsreferanseId: UUID,
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
)

@Serializable
internal data class ApiVurdertDag(
    val dato: LocalDate,
    val vilkår: ApiLøpendeVilkår,
    val vurderingId: UUID,
    val utfall: ApiUtfall,
)

@Serializable
internal enum class ApiLøpendeVilkår {
    OPPHOLDSKRAV,
}

@Serializable
internal enum class ApiUtfall {
    Oppfylt,
    IkkeOppfylt,
}
