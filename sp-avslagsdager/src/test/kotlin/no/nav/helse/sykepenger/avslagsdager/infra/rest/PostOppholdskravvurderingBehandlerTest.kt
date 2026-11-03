package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.domain.Utfall
import no.nav.helse.sykepenger.avslagsdager.domain.Vilkårskode
import no.nav.helse.sykepenger.avslagsdager.infra.kafka.OutboxPubliseringsjobb
import no.nav.helse.sykepenger.avslagsdager.leaderelection.LeaderElection
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureStatusPages
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.post
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.sykepenger.libs.ktor.auth.AccessToken
import no.nav.sykepenger.libs.ktor.auth.NavIdent
import no.nav.sykepenger.libs.ktor.auth.Saksbehandler
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerOid
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerPrincipal
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.UUID

class PostOppholdskravvurderingBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")
    private val behandlingId = UUID.randomUUID()
    private val transaksjonProvider = InMemoryTransaksjonProvider()

    @Test
    fun `avslag lagrer vurderingen med dagene, vilkårsvurderingene og saksbehandleren`() =
        testApplication {
            // Given
            val pseudoId = settOpp()

            // When
            val response =
                client.post(url(pseudoId)) {
                    json(request(oppholdIGodkjentOmråde = false, unntakAndreLedd = "INGEN", godkjentOppholdInntilFireUker = false))
                }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val vurderingId = jacksonObjectMapper().readTree(response.bodyAsText())["vurderingId"].asString()
            val lagret = transaksjonProvider.oppholdskravvurderinger.alle.single()
            assertEquals(vurderingId, lagret.id.toString())
            assertEquals(identitetsnummer.value, lagret.fødselsnummer)
            assertEquals(behandlingId, lagret.behandlingId)
            assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), lagret.dager)
            assertEquals(Utfall.IkkeOppfylt, lagret.utfall)
            assertEquals(
                listOf(
                    Vilkårskode.OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND,
                    Vilkårskode.UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8,
                    Vilkårskode.UNNTAK_INNLAGT_HELSEINSTITUSJON,
                    Vilkårskode.UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER,
                ),
                lagret.vilkårsvurderinger.map { it.vilkårskode },
            )
            assertEquals("Z999999", lagret.saksbehandlerIdent)
            assertEquals("Begrunnelse", lagret.fritekstbegrunnelse)
            assertEquals(listOf("123"), lagret.journalpostId)
        }

    @Test
    fun `vurderingen publiseres som saksbehandlerhandling og avslagsdager_endret`() =
        testApplication {
            // Given
            val pseudoId = settOpp()
            val rapid = TestRapid()

            // When
            val response =
                client.post(url(pseudoId)) {
                    json(
                        request(
                            dager = """["2026-01-10", "2026-01-02", "2026-01-01"]""",
                            oppholdIGodkjentOmråde = false,
                            unntakAndreLedd = "INGEN",
                            godkjentOppholdInntilFireUker = false,
                        ),
                    )
                }
            OutboxPubliseringsjobb(rapid, transaksjonProvider, LeaderElection { true }).kjørEnRunde()

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val vurderingId = jacksonObjectMapper().readTree(response.bodyAsText())["vurderingId"].asString()
            assertEquals(2, rapid.inspektør.size)

            val handling = rapid.inspektør.message(0)
            assertEquals("saksbehandlerhandling", handling["@event_name"].asString())
            assertEquals(identitetsnummer.value, handling["fødselsnummer"].asString())
            assertEquals("Z999999", handling["saksbehandlerIdent"].asString())
            assertEquals(vurderingId, handling["handlingId"].asString())
            assertEquals("AVSLAGSDAGER_ENDRET", handling["handlingType"].asString())
            assertTrue(handling["kreverTotrinnsvurdering"].asBoolean())
            assertTrue(handling.hasNonNull("tidspunkt"))
            assertEquals(behandlingId.toString(), handling["behandlingId"].asString())
            assertFalse(handling.has("skjæringstidspunkt"))

            val endret = rapid.inspektør.message(1)
            assertEquals("avslagsdager_endret", endret["@event_name"].asString())
            assertEquals(identitetsnummer.value, endret["fødselsnummer"].asString())
            assertEquals(vurderingId, endret["vurderingId"].asString())
            assertEquals(
                listOf("2026-01-01" to "2026-01-02", "2026-01-10" to "2026-01-10"),
                endret["perioder"].values().map { it["fom"].asString() to it["tom"].asString() },
            )
            assertFalse(endret.has("utfall"))

            assertEquals(listOf(identitetsnummer.value, identitetsnummer.value), listOf(rapid.inspektør.key(0), rapid.inspektør.key(1)))
        }

    @Test
    fun `omgjøring til oppfylt lagres som en ny vurdering`() =
        testApplication {
            // Given
            val pseudoId = settOpp()
            client.post(url(pseudoId)) {
                json(request(oppholdIGodkjentOmråde = false, unntakAndreLedd = "INGEN", godkjentOppholdInntilFireUker = false))
            }

            // When
            val response =
                client.post(url(pseudoId)) {
                    json(request(oppholdIGodkjentOmråde = false, unntakAndreLedd = "INNLAGT_I_HELSEINSTITUSJON"))
                }

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val (første, andre) = transaksjonProvider.oppholdskravvurderinger.alle
            assertEquals(Utfall.IkkeOppfylt, første.utfall)
            assertEquals(Utfall.Oppfylt, andre.utfall)
            assertEquals(UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON, andre.svar.unntakAndreLedd)
        }

    @Test
    fun `kall når vurdering av oppholdskravet ikke er skrudd på gir 500 og lagrer ingenting`() =
        testApplication {
            // Given
            val pseudoId = settOpp(aktivert = false)

            // When
            val response = client.post(url(pseudoId)) { json(request()) }

            // Then
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals(emptyList<Any>(), transaksjonProvider.oppholdskravvurderinger.alle)
            assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
        }

    @Test
    fun `tomme dager gir 400`() = assertUgyldig(request(dager = "[]"), "Dager må oppgis")

    @Test
    fun `tom fritekstbegrunnelse gir 400`() = assertUgyldig(request(fritekstbegrunnelse = " "), "Fritekstbegrunnelse må oppgis")

    @Test
    fun `manglende svar på unntak i andre ledd gir 400`() = assertUgyldig(request(oppholdIGodkjentOmråde = false), "Må svare på om et av unntakene i andre ledd gjelder")

    @Test
    fun `manglende svar på godkjent opphold inntil fire uker gir 400`() =
        assertUgyldig(
            request(oppholdIGodkjentOmråde = false, unntakAndreLedd = "INGEN"),
            "Må svare på om oppholdet er godkjent for inntil fire uker",
        )

    @Test
    fun `ukjent unntak i andre ledd gir 400`() = assertUgyldig(request(oppholdIGodkjentOmråde = false, unntakAndreLedd = "UKJENT"))

    @Test
    fun `manglende tilgang til personen gir 403`() =
        testApplication {
            // Given
            val pseudoId =
                settOpp(tilgangskontroll = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)))

            // When
            val response = client.post(url(pseudoId)) { json(request()) }

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(emptyList<Any>(), transaksjonProvider.oppholdskravvurderinger.alle)
            assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
        }

    @Test
    fun `kall uten Skriv-tilgang gir 403`() =
        testApplication {
            // Given
            val pseudoId = settOpp(tilganger = setOf(Tilgang.Les))

            // When
            val response = client.post(url(pseudoId)) { json(request()) }

            // Then
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(emptyList<Any>(), transaksjonProvider.oppholdskravvurderinger.alle)
            assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
        }

    @Test
    fun `ukjent person gir 404`() =
        testApplication {
            // Given
            settOpp()

            // When
            val response = client.post(url(UUID.randomUUID().toString())) { json(request()) }

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `ugyldig person-id gir 404`() =
        testApplication {
            // Given
            settOpp()

            // When
            val response = client.post(url("ikke-en-uuid")) { json(request()) }

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    private fun assertUgyldig(
        body: String,
        forventetTittel: String? = null,
    ) = testApplication {
        // Given
        val pseudoId = settOpp()

        // When
        val response = client.post(url(pseudoId)) { json(body) }

        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        if (forventetTittel != null) {
            assertEquals(forventetTittel, jacksonObjectMapper().readTree(response.bodyAsText())["title"].asString())
        }
        assertEquals(emptyList<Any>(), transaksjonProvider.oppholdskravvurderinger.alle)
        assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
    }

    private fun ApplicationTestBuilder.settOpp(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les, Tilgang.Skriv),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        aktivert: Boolean = true,
    ): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
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
                post(PostOppholdskravvurderingBehandler(aktivert), restAdapter)
            }
        }
        return pseudoId.toString()
    }

    @Language("JSON")
    private fun request(
        dager: String = """["2026-01-02", "2026-01-01"]""",
        oppholdIGodkjentOmråde: Boolean = true,
        unntakAndreLedd: String? = null,
        godkjentOppholdInntilFireUker: Boolean? = null,
        fritekstbegrunnelse: String = "Begrunnelse",
    ) = """
        {
          "behandlingId": "$behandlingId",
          "dager": $dager,
          "eøsBorger": true,
          "oppholdIGodkjentOmråde": $oppholdIGodkjentOmråde,
          "unntakAndreLedd": ${unntakAndreLedd?.let { "\"$it\"" }},
          "godkjentOppholdInntilFireUker": $godkjentOppholdInntilFireUker,
          "fritekstbegrunnelse": "$fritekstbegrunnelse",
          "journalpostId": ["123"]
        }
        """

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

private fun HttpRequestBuilder.json(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

private fun url(pseudoId: String) = "/api/personer/$pseudoId/vurderinger/oppholdskrav"
