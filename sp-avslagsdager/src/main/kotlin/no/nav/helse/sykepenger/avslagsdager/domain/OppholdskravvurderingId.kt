package no.nav.helse.sykepenger.avslagsdager.domain

import java.util.UUID

@JvmInline
internal value class OppholdskravvurderingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = OppholdskravvurderingId(UUID.randomUUID())
    }
}
