package no.nav.helse.sykepenger.avslagsdager.infra.rest

import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.ApiErrorCode

internal enum class ApiOppholdskravvurderingFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    DagerMangler(400, "Dager må oppgis"),
    FritekstbegrunnelseMangler(400, "Fritekstbegrunnelse må oppgis"),
    UnntakAndreLeddMangler(400, "Må svare på om et av unntakene i andre ledd gjelder"),
    GodkjentOppholdInntilFireUkerMangler(400, "Må svare på om oppholdet er godkjent for inntil fire uker"),
}
