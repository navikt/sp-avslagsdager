package no.nav.helse.sykepenger.avslagsdager.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/saksbehandlerhandlinger/{handlingId}/visning")
internal class ApiSaksbehandlerhandlingVisningResource(
    val personId: String,
    val handlingId: String,
)
