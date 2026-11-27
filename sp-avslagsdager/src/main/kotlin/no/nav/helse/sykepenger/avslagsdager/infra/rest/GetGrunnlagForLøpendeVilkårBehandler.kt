package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.domain.DagMedKilder
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.GrunnlagForLøpendeVilkårId
import no.nav.helse.sykepenger.avslagsdager.domain.LøpendeVilkår
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import java.util.UUID

/**
 * Grunnlaget for løpende vilkår som en beregning i Spleis bygde på (ADR 0023). Spesialist bruker det til å sperre
 * godkjenning, og Speil viser det i dagoversikten.
 */
internal class GetGrunnlagForLøpendeVilkårBehandler(
    private val aktivert: Boolean,
) : GetBehandler<
        ApiGrunnlagForLøpendeVilkårResource,
        ApiGrunnlagForLøpendeVilkårResponse,
        ApiGrunnlagForLøpendeVilkårFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "avslagsdager"

    override fun behandle(
        resource: ApiGrunnlagForLøpendeVilkårResource,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiGrunnlagForLøpendeVilkårResponse, ApiGrunnlagForLøpendeVilkårFeil> {
        check(aktivert) { "Vurdering av oppholdskravet er ikke skrudd på" }

        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiGrunnlagForLøpendeVilkårFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiGrunnlagForLøpendeVilkårFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiGrunnlagForLøpendeVilkårFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val ikkeFunnet = RestResponse.feil(ApiGrunnlagForLøpendeVilkårFeil.GrunnlagIkkeFunnet)

            val id =
                runCatching { UUID.fromString(resource.grunnlagForLøpendeVilkårId) }.getOrNull()
                    ?: return@medPerson ikkeFunnet

            // Tilgangen er sjekket mot personen i URL-en, så et grunnlag for en annen person skal se ut som at det ikke finnes
            val grunnlag =
                kallKontekst.transaksjon.grunnlagForLøpendeVilkår
                    .finnOrNull(GrunnlagForLøpendeVilkårId(id))
                    ?.takeIf { it.innhold.behov.fødselsnummer == identitetsnummer.value }
                    ?: return@medPerson ikkeFunnet

            RestResponse.ok(grunnlag.tilResponse())
        }
    }
}

private fun GrunnlagForLøpendeVilkår.tilResponse() =
    ApiGrunnlagForLøpendeVilkårResponse(
        grunnlagForLøpendeVilkårId = id.value,
        periode = ApiPeriode(fom = innhold.behov.periode.fom, tom = innhold.behov.periode.tom),
        dagerMedOppgittUtlandsopphold = innhold.dagerMedOppgittUtlandsopphold.map { it.tilApi() },
        dagerTilVurdering = innhold.dagerTilVurdering.map { it.tilApi() },
        vurderteDager =
            innhold.vurderteDager.map {
                ApiVurdertDag(dato = it.dato, vilkår = it.vilkår.tilApi(), vurderingId = it.vurderingId.value, utfall = it.utfall.tilApi())
            },
    )

private fun DagMedKilder.tilApi() =
    ApiDagMedKilder(
        dato = dato,
        kilder =
            kilder.map {
                ApiKilde(
                    søknadMeldingsreferanseId = it.søknadMeldingsreferanseId.value,
                    yrkesaktivitetstype = it.yrkesaktivitet.yrkesaktivitetstype,
                    organisasjonsnummer = it.yrkesaktivitet.organisasjonsnummer,
                )
            },
    )

private fun LøpendeVilkår.tilApi() =
    when (this) {
        LøpendeVilkår.OPPHOLDSKRAV -> ApiLøpendeVilkår.OPPHOLDSKRAV
    }

private fun Utfall.tilApi() =
    when (this) {
        Utfall.Oppfylt -> ApiUtfall.Oppfylt
        Utfall.IkkeOppfylt -> ApiUtfall.IkkeOppfylt
    }
