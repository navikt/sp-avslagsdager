package no.nav.helse.sykepenger.avslagsdager.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vurderinger/oppholdskrav")
internal class ApiOppholdskravvurderingResource(
    val personId: String,
)
