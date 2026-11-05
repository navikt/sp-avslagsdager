package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureStatusPages
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.get
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.sykepenger.libs.ktor.auth.AccessToken
import no.nav.sykepenger.libs.ktor.auth.NavIdent
import no.nav.sykepenger.libs.ktor.auth.Saksbehandler
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerOid
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerPrincipal
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.UUID

class GetSaksbehandlerhandlingVisningBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")
    private val annenPerson = Identitetsnummer("10987654321")
    private val transaksjonProvider = InMemoryTransaksjonProvider()

    @Test
    fun `avslag vises med utfall, svarene, perioder, journalposter og begrunnelse`() =
        testApplication {
            // Given
            val vurdering =
                lagreVurdering(
                    dager = listOf(3.jan, 1.jan, 2.jan, 10.jan),
                    svar = SvarPåOppholdskrav(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = UnntakAndreLedd.INGEN, godkjentOppholdInntilFireUker = false),
                    journalpostId = listOf("123", "456"),
                )
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertJson(
                """
                {
                  "felter": [
                    { "type": "TEKST", "tittel": "Oppholdskrav", "tekst": "Ikke oppfylt" },
                    { "type": "TEKST", "tittel": "EØS-borger", "tekst": "Ja" },
                    { "type": "TEKST", "tittel": "Opphold i Norge, EØS eller avtaleland", "tekst": "Nei" },
                    { "type": "TEKST", "tittel": "Unntak i andre ledd", "tekst": "Nei" },
                    { "type": "TEKST", "tittel": "Godkjent opphold utenfor Norge i inntil fire uker", "tekst": "Nei" },
                    { "type": "LISTE", "tittel": "Dager", "elementer": ["01.01.2026–03.01.2026", "10.01.2026"] },
                    { "type": "LISTE", "tittel": "Journalposter", "elementer": ["123", "456"] },
                    { "type": "TEKST", "tittel": "Begrunnelse", "tekst": "Oppholdt seg i Thailand" }
                  ]
                }
                """,
                response.bodyAsText(),
            )
        }

    @Test
    fun `omgjøring til oppfylt vises uten journalposter når det ikke er noen`() =
        testApplication {
            // Given
            val vurdering =
                lagreVurdering(
                    dager = listOf(1.jan),
                    svar = SvarPåOppholdskrav(eøsBorger = false, oppholdIGodkjentOmråde = false, unntakAndreLedd = UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON, godkjentOppholdInntilFireUker = null),
                    journalpostId = emptyList(),
                )
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertJson(
                """
                {
                  "felter": [
                    { "type": "TEKST", "tittel": "Oppholdskrav", "tekst": "Oppfylt" },
                    { "type": "TEKST", "tittel": "EØS-borger", "tekst": "Nei" },
                    { "type": "TEKST", "tittel": "Opphold i Norge", "tekst": "Nei" },
                    { "type": "TEKST", "tittel": "Unntak i andre ledd", "tekst": "Innlagt i helseinstitusjon" },
                    { "type": "LISTE", "tittel": "Dager", "elementer": ["01.01.2026"] },
                    { "type": "TEKST", "tittel": "Begrunnelse", "tekst": "Oppholdt seg i Thailand" }
                  ]
                }
                """,
                response.bodyAsText(),
            )
        }

    @Test
    fun `oppfylt første ledd vises uten spørsmålene om unntak`() =
        testApplication {
            // Given
            val vurdering =
                lagreVurdering(
                    svar = SvarPåOppholdskrav(eøsBorger = true, oppholdIGodkjentOmråde = true, unntakAndreLedd = UnntakAndreLedd.INGEN, godkjentOppholdInntilFireUker = false),
                    journalpostId = emptyList(),
                )
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            assertJson(
                """
                {
                  "felter": [
                    { "type": "TEKST", "tittel": "Oppholdskrav", "tekst": "Oppfylt" },
                    { "type": "TEKST", "tittel": "EØS-borger", "tekst": "Ja" },
                    { "type": "TEKST", "tittel": "Opphold i Norge, EØS eller avtaleland", "tekst": "Ja" },
                    { "type": "LISTE", "tittel": "Dager", "elementer": ["01.01.2026"] },
                    { "type": "TEKST", "tittel": "Begrunnelse", "tekst": "Oppholdt seg i Thailand" }
                  ]
                }
                """,
                response.bodyAsText(),
            )
        }

    @Test
    fun `kall når vurdering av oppholdskravet ikke er skrudd på gir 500`() =
        testApplication {
            // Given
            val vurdering = lagreVurdering()
            val pseudoId = settOpp(aktivert = false)

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `ukjent handlingId gir 404`() =
        testApplication {
            // Given
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, UUID.randomUUID().toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `handlingId som ikke er en UUID gir 404`() =
        testApplication {
            // Given
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, "ikke-en-uuid"))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `vurdering som gjelder en annen person gir 404`() =
        testApplication {
            // Given
            val vurdering = lagreVurdering(fødselsnummer = annenPerson)
            val pseudoId = settOpp()

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `manglende tilgang til personen gir 403`() =
        testApplication {
            // Given
            val vurdering = lagreVurdering()
            val pseudoId =
                settOpp(tilgangskontroll = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)))

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `kall uten Les-tilgang gir 403`() =
        testApplication {
            // Given
            val vurdering = lagreVurdering()
            val pseudoId = settOpp(tilganger = emptySet())

            // When
            val response = client.get(url(pseudoId, vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `ukjent person gir 404`() =
        testApplication {
            // Given
            val vurdering = lagreVurdering()
            settOpp()

            // When
            val response = client.get(url(UUID.randomUUID().toString(), vurdering.id.toString()))

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    private fun lagreVurdering(
        fødselsnummer: Identitetsnummer = identitetsnummer,
        dager: List<LocalDate> = listOf(1.jan),
        svar: SvarPåOppholdskrav = SvarPåOppholdskrav(eøsBorger = true, oppholdIGodkjentOmråde = false, unntakAndreLedd = UnntakAndreLedd.INGEN, godkjentOppholdInntilFireUker = false),
        journalpostId: List<String> = emptyList(),
    ): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = fødselsnummer.value,
                behandlingId = UUID.randomUUID(),
                dager = dager,
                svar = svar,
                fritekstbegrunnelse = "Oppholdt seg i Thailand",
                journalpostId = journalpostId,
                saksbehandlerIdent = "Z999999",
            )
        val vurdering = (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
        transaksjonProvider.oppholdskravvurderinger.lagre(vurdering)
        return vurdering
    }

    private fun ApplicationTestBuilder.settOpp(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        aktivert: Boolean = true,
    ): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
        pseudoIdProvider.nyPersonPseudoId(annenPerson)
        application {
            configureContentNegotiation()
            configureResources()
            configureStatusPages()
            val principal = SaksbehandlerPrincipal(saksbehandler, tilganger, AccessToken("token"))
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
            val restAdapter =
                RestAdapter(
                    personPseudoIdProvider = pseudoIdProvider,
                    populasjonstilgangskontrollProvider = tilgangskontroll,
                    transaksjonProvider = transaksjonProvider,
                )
            routing {
                get(GetSaksbehandlerhandlingVisningBehandler(aktivert), restAdapter)
            }
        }
        return pseudoId.toString()
    }

    private class FakeTilgangskontroll(
        private val resultat: TilgangskontrollResultat = TilgangskontrollResultat.Ok,
    ) : PopulasjonstilgangskontrollProvider {
        override fun kontrollerKomplettTilgang(
            accessToken: String,
            fødselsnummer: String,
        ) = resultat

        override fun kontrollerKjerneTilgang(
            accessToken: String,
            fødselsnummer: String,
        ) = resultat

        override fun kontrollerKjerneTilgangForAnsatt(
            ansattId: String,
            fødselsnummer: String,
        ) = resultat
    }
}

private fun url(
    personId: String,
    handlingId: String,
) = "/api/personer/$personId/saksbehandlerhandlinger/$handlingId/visning"

private fun assertJson(
    @Language("JSON") forventet: String,
    faktisk: String,
) {
    val mapper = jacksonObjectMapper()
    assertEquals(mapper.readTree(forventet), mapper.readTree(faktisk))
}

private val Int.jan get() = LocalDate.of(2026, 1, this)
