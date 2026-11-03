@file:UseContextualSerialization(LocalDate::class, UUID::class)

package no.nav.helse.sykepenger.avslagsdager.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate
import java.util.UUID

@Serializable
internal data class ApiOppholdskravvurderingRequest(
    val behandlingId: UUID,
    val dager: List<LocalDate>,
    val eøsBorger: Boolean,
    val oppholdIGodkjentOmråde: Boolean,
    val unntakAndreLedd: ApiUnntakAndreLedd?,
    val godkjentOppholdInntilFireUker: Boolean?,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
)

@Serializable
internal enum class ApiUnntakAndreLedd {
    MEDLEM_ETTER_2_5_2_6_2_8,
    INNLAGT_I_HELSEINSTITUSJON,
    INGEN,
}
