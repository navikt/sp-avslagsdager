package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.domain.tilPerioder
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Detaljene i en saksbehandlerhandling av typen `MANUELT_VURDERT_OPPHOLDSKRAV`. Handlings-ID-en er vurderings-ID-en.
 */
internal class GetSaksbehandlerhandlingVisningBehandler(
    private val aktivert: Boolean,
) : GetBehandler<
        ApiSaksbehandlerhandlingVisningResource,
        ApiSaksbehandlerhandlingVisningResponse,
        ApiSaksbehandlerhandlingVisningFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "saksbehandlerhandlinger"

    override fun behandle(
        resource: ApiSaksbehandlerhandlingVisningResource,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiSaksbehandlerhandlingVisningResponse, ApiSaksbehandlerhandlingVisningFeil> {
        check(aktivert) { "Vurdering av oppholdskravet er ikke skrudd på" }

        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiSaksbehandlerhandlingVisningFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiSaksbehandlerhandlingVisningFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiSaksbehandlerhandlingVisningFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val ikkeFunnet = RestResponse.feil(ApiSaksbehandlerhandlingVisningFeil.VisningIkkeFunnet)

            val vurderingId =
                runCatching { UUID.fromString(resource.handlingId) }.getOrNull()
                    ?: return@medPerson ikkeFunnet

            // Tilgangen er sjekket mot personen i URL-en, så en vurdering for en annen person skal se ut som at den ikke finnes
            val vurdering =
                kallKontekst.transaksjon.oppholdskravvurderinger
                    .finn(OppholdskravvurderingId(vurderingId))
                    ?.takeIf { it.fødselsnummer == identitetsnummer.value }
                    ?: return@medPerson ikkeFunnet

            RestResponse.ok(ApiSaksbehandlerhandlingVisningResponse(felter = vurdering.tilVisningsfelter()))
        }
    }
}

// Feltene kommer i samme rekkefølge som spørsmålene i skjemaet. Svar på spørsmål som ikke ble stilt, er fjernet ved lagring.
private fun Oppholdskravvurdering.tilVisningsfelter(): List<ApiVisningsfelt> =
    buildList {
        add(ApiVisningsfelt.Tekst(tittel = "Oppholdskrav", tekst = utfall.visningstekst()))
        add(ApiVisningsfelt.Tekst(tittel = "EØS-borger", tekst = svar.eøsBorger.jaNei()))
        val oppholdstittel = if (svar.eøsBorger) "Opphold i Norge, EØS eller avtaleland" else "Opphold i Norge"
        add(ApiVisningsfelt.Tekst(tittel = oppholdstittel, tekst = svar.oppholdIGodkjentOmråde.jaNei()))
        svar.unntakAndreLedd?.let { add(ApiVisningsfelt.Tekst(tittel = "Unntak i andre ledd", tekst = it.visningstekst())) }
        svar.godkjentOppholdInntilFireUker?.let {
            add(ApiVisningsfelt.Tekst(tittel = "Godkjent opphold utenfor Norge i inntil fire uker", tekst = it.jaNei()))
        }
        add(ApiVisningsfelt.Liste(tittel = "Dager", elementer = dager.tilPerioder().map { it.visningstekst() }))
        if (journalpostId.isNotEmpty()) {
            add(ApiVisningsfelt.Liste(tittel = "Journalposter", elementer = journalpostId))
        }
        add(ApiVisningsfelt.Tekst(tittel = "Begrunnelse", tekst = fritekstbegrunnelse))
    }

private val datoformat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Periode.visningstekst() = if (fom == tom) fom.format(datoformat) else "${fom.format(datoformat)}–${tom.format(datoformat)}"

private fun Utfall.visningstekst() =
    when (this) {
        Utfall.Oppfylt -> "Oppfylt"
        Utfall.IkkeOppfylt -> "Ikke oppfylt"
    }

private fun UnntakAndreLedd.visningstekst() =
    when (this) {
        UnntakAndreLedd.MEDLEM_ETTER_2_5_2_6_2_8 -> "Medlem etter §§ 2-5, 2-6 eller 2-8"
        UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON -> "Innlagt i helseinstitusjon"
        UnntakAndreLedd.INGEN -> "Nei"
    }

private fun Boolean.jaNei() = if (this) "Ja" else "Nei"
