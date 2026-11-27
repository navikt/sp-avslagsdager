package no.nav.helse.sykepenger.avslagsdager.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/avslagsdager/{grunnlagForLøpendeVilkårId}")
internal class ApiGrunnlagForLøpendeVilkårResource(
    val personId: String,
    val grunnlagForLøpendeVilkårId: String,
)
