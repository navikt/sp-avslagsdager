package no.nav.helse.sykepenger.avslagsdager.infra.rest

import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.ApiErrorCode

internal enum class ApiGrunnlagForLøpendeVilkårFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    GrunnlagIkkeFunnet(404, "Fant ikke grunnlaget for løpende vilkår"),
}
