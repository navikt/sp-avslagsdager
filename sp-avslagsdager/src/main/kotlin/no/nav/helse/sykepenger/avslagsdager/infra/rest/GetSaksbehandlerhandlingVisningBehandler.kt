package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.domain.Lovreferanse
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.tilPerioder
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Detaljene i en saksbehandlerhandling av typen `AVSLAGSDAGER_ENDRET`. Handlings-ID-en er vurderings-ID-en.
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

private fun Oppholdskravvurdering.tilVisningsfelter(): List<ApiVisningsfelt> =
    buildList {
        add(ApiVisningsfelt.Liste(tittel = "Dager", elementer = dager.tilPerioder().map { it.visningstekst() }))
        add(ApiVisningsfelt.Tekst(tittel = "Utfall", tekst = utfall.visningstekst()))
        add(ApiVisningsfelt.Liste(tittel = "Vilkår", elementer = vilkårsvurderinger.map { it.visningstekst() }))
        add(ApiVisningsfelt.Tekst(tittel = "Begrunnelse", tekst = fritekstbegrunnelse))
        if (journalpostId.isNotEmpty()) {
            add(ApiVisningsfelt.Liste(tittel = "Journalposter", elementer = journalpostId))
        }
    }

private val datoformat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Periode.visningstekst() = if (fom == tom) fom.format(datoformat) else "${fom.format(datoformat)}–${tom.format(datoformat)}"

private fun Utfall.visningstekst() =
    when (this) {
        Utfall.Oppfylt -> "Oppfylt"
        Utfall.IkkeOppfylt -> "Ikke oppfylt"
    }

// Bruker lovreferansen som ble lagret med vurderingen, så visningen viser hjemmelen slik den var da vurderingen ble gjort
private fun Vilkårsvurdering.visningstekst() = "${lovreferanse.visningstekst()}: ${utfall.visningstekst()}"

private fun Lovreferanse.visningstekst() =
    listOfNotNull(
        "§ $paragraf",
        avsnitt?.let { "${it.ordenstall()} ledd" },
        setning?.let { "${it.ordenstall()} setning" },
        bokstav?.let { "bokstav $it" },
    ).joinToString(" ")

private fun Int.ordenstall() =
    when (this) {
        1 -> "første"
        2 -> "andre"
        3 -> "tredje"
        4 -> "fjerde"
        5 -> "femte"
        else -> "$this."
    }
