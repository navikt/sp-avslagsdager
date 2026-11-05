package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.avslagsdager.application.HandlingType
import no.nav.helse.sykepenger.avslagsdager.application.OutboxKonvolutt
import no.nav.helse.sykepenger.avslagsdager.application.OutboxMelding
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UgyldigOppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.tilPerioder
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang

/**
 * Saksbehandlerens vurdering av oppholdskravet i § 8-9 for dager i en behandling.
 *
 * Både avslag og omgjøring til oppfylt lagres som en ny vurdering. Tidligere vurderinger endres ikke.
 * Vurderingen og meldingene til spesialist og Spleis lagres i samme transaksjon og publiseres via outboxen.
 */
internal class PostOppholdskravvurderingBehandler(
    private val aktivert: Boolean,
) : PostBehandler<
        ApiOppholdskravvurderingResource,
        ApiOppholdskravvurderingRequest,
        ApiOppholdskravvurderingResponse,
        ApiOppholdskravvurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vurderinger"

    override fun behandle(
        resource: ApiOppholdskravvurderingResource,
        request: ApiOppholdskravvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiOppholdskravvurderingResponse, ApiOppholdskravvurderingFeil> {
        check(aktivert) { "Vurdering av oppholdskravet er ikke skrudd på" }

        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiOppholdskravvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiOppholdskravvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiOppholdskravvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val resultat =
                Oppholdskravvurdering.vurder(
                    fødselsnummer = identitetsnummer.value,
                    behandlingId = request.behandlingId,
                    dager = request.dager,
                    svar = request.tilSvar(),
                    fritekstbegrunnelse = request.fritekstbegrunnelse,
                    journalpostId = request.journalpostId,
                    saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value,
                )
            val vurdering =
                when (resultat) {
                    is OppholdskravvurderingResultat.Ugyldig -> return@medPerson RestResponse.feil(resultat.ugyldighet.tilApi())
                    is OppholdskravvurderingResultat.Vurdert -> resultat.vurdering
                }

            kallKontekst.transaksjon.oppholdskravvurderinger.lagre(vurdering)
            kallKontekst.transaksjon.outbox.leggTil(OutboxKonvolutt.ny(vurdering.tilSaksbehandlerhandling(), identitetsnummer))
            kallKontekst.transaksjon.outbox.leggTil(OutboxKonvolutt.ny(vurdering.tilAvslagsdagerEndret(), identitetsnummer))

            RestResponse.ok(ApiOppholdskravvurderingResponse(vurderingId = vurdering.id.value))
        }
    }
}

private fun Oppholdskravvurdering.tilSaksbehandlerhandling() =
    OutboxMelding.Saksbehandlerhandling(
        saksbehandlerIdent = saksbehandlerIdent,
        handlingId = id.value,
        handlingType = HandlingType.AVSLAGSDAGER_ENDRET,
        kreverTotrinnsvurdering = true,
        tidspunkt = vurdertTidspunkt,
        behandlingId = behandlingId,
    )

private fun Oppholdskravvurdering.tilAvslagsdagerEndret() =
    OutboxMelding.AvslagsdagerEndret(
        vurderingId = id.value,
        perioder = dager.tilPerioder(),
        manuellVurdering = true,
    )

private fun ApiOppholdskravvurderingRequest.tilSvar() =
    SvarPåOppholdskrav(
        eøsBorger = eøsBorger,
        oppholdIGodkjentOmråde = oppholdIGodkjentOmråde,
        unntakAndreLedd = unntakAndreLedd?.fraApi(),
        godkjentOppholdInntilFireUker = godkjentOppholdInntilFireUker,
    )

private fun ApiUnntakAndreLedd.fraApi() =
    when (this) {
        ApiUnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8 -> UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8
        ApiUnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON -> UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON
        ApiUnntakAndreLedd.INGEN -> UnntakAndreLedd.INGEN
    }

private fun UgyldigOppholdskravvurdering.tilApi() =
    when (this) {
        UgyldigOppholdskravvurdering.DAGER_MANGLER -> ApiOppholdskravvurderingFeil.DagerMangler
        UgyldigOppholdskravvurdering.FRITEKSTBEGRUNNELSE_MANGLER -> ApiOppholdskravvurderingFeil.FritekstbegrunnelseMangler
        UgyldigOppholdskravvurdering.UNNTAK_ANDRE_LEDD_MANGLER -> ApiOppholdskravvurderingFeil.UnntakAndreLeddMangler
        UgyldigOppholdskravvurdering.GODKJENT_OPPHOLD_INNTIL_FIRE_UKER_MANGLER ->
            ApiOppholdskravvurderingFeil.GodkjentOppholdInntilFireUkerMangler
    }
