package no.nav.helse.sykepenger.avslagsdager.infra.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.openapi.OpenApiConfig
import no.nav.helse.sykepenger.avslagsdager.rammeverk.openapi.configureOpenApiPlugin
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.get
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.post
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class OppholdskravvurderingOpenApiTest {
    @Test
    fun `openapi-specen dokumenterer endepunktene, requesten og responsene`() =
        testApplication {
            // Given
            application { settOppTestapp() }
            startApplication()

            // When
            val response = client.get("/api/openapi.json")

            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            val spec = jacksonObjectMapper().readTree(response.bodyAsText())
            assertTrue(spec["paths"].has("/api/personer/{personId}/vurderinger/oppholdskrav")) { "Forventet at ruten var dokumentert: $spec" }

            val request = spec["components"]["schemas"]["ApiOppholdskravvurderingRequest"]["properties"]
            listOf(
                "behandlingId",
                "dager",
                "eøsBorger",
                "oppholdIGodkjentOmråde",
                "unntakAndreLedd",
                "godkjentOppholdInntilFireUker",
                "fritekstbegrunnelse",
                "journalpostId",
            ).forEach { felt -> assertTrue(request.has(felt)) { "Forventet feltet $felt i requesten: $request" } }

            val unntak = spec["components"]["schemas"]["ApiUnntakAndreLedd"]["enum"].values().map { it.asString() }
            assertEquals(listOf("MEDLEM_ETTER_2_5_2_6_2_8", "INNLAGT_I_HELSEINSTITUSJON", "INGEN"), unntak)

            val responsen = spec["components"]["schemas"]["ApiOppholdskravvurderingResponse"]["properties"]
            assertTrue(responsen.has("vurderingId")) { "Forventet vurderingId i responsen: $responsen" }

            assertTrue(spec["paths"].has("/api/personer/{personId}/saksbehandlerhandlinger/{handlingId}/visning")) {
                "Forventet at visningen var dokumentert: $spec"
            }
            val visning = spec["components"]["schemas"]["ApiSaksbehandlerhandlingVisningResponse"]["properties"]
            assertTrue(visning.has("felter")) { "Forventet felter i visningen: $visning" }
            listOf("ApiVisningsfelt.Tekst", "ApiVisningsfelt.Liste").forEach { skjema ->
                assertTrue(spec["components"]["schemas"][skjema]?.get("properties")?.has("type") == true) {
                    "Forventet diskriminatoren type i skjemaet $skjema: ${spec["components"]["schemas"][skjema]}"
                }
            }
        }

    private fun Application.settOppTestapp() {
        configureContentNegotiation()
        configureResources()
        val restAdapter =
            RestAdapter<Transaksjonskontekst>(
                personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
                populasjonstilgangskontrollProvider =
                    object : PopulasjonstilgangskontrollProvider {
                        override fun kontrollerKomplettTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgangForAnsatt(
                            ansattId: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok
                    },
                transaksjonProvider = InMemoryTransaksjonProvider(),
            )
        configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = true, tittel = "sp-avslagsdager"))
        routing {
            post(PostOppholdskravvurderingBehandler(aktivert = true), restAdapter)
            get(GetSaksbehandlerhandlingVisningBehandler(aktivert = true), restAdapter)
        }
    }
}
