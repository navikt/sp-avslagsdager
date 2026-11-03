package no.nav.helse.sykepenger.avslagsdager.bootstrap

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.application.Transaksjonskontekst
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.avslagsdager.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.avslagsdager.rammeverk.rest.configureRestRuting
import no.nav.helse.sykepenger.avslagsdager.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.sykepenger.libs.ktor.auth.AzureAdConfig
import no.nav.sykepenger.libs.ktor.auth.TilgangsgrupperTilTilganger
import no.nav.sykepenger.libs.ktor.auth.configureJwtAuthentication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class EndepunkterTest {
    @Test
    fun `vurdering av oppholdskravet er bare aktivert i dev-gcp`() {
        assertTrue(oppholdskravvurderingAktivert(mapOf("NAIS_CLUSTER_NAME" to "dev-gcp")))
        assertFalse(oppholdskravvurderingAktivert(mapOf("NAIS_CLUSTER_NAME" to "prod-gcp")))
        assertFalse(oppholdskravvurderingAktivert(emptyMap()))
    }

    @Test
    fun `endepunktet finnes ikke når vurdering av oppholdskravet er av`() =
        testApplication {
            // Given
            application { settOpp(oppholdskravvurderingAktivert = false) }

            // When
            val response = client.post(url())

            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `endepunktet finnes og krever token når vurdering av oppholdskravet er på`() =
        testApplication {
            // Given
            application { settOpp(oppholdskravvurderingAktivert = true) }

            // When
            val response = client.post(url())

            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    private fun Application.settOpp(oppholdskravvurderingAktivert: Boolean) {
        configureContentNegotiation()
        configureResources()
        configureJwtAuthentication(
            azureAdConfig =
                AzureAdConfig(
                    clientId = "sp-avslagsdager",
                    issuerUrl = "http://localhost/azure",
                    jwkProviderUri = "http://localhost/azure/jwks",
                ),
            tilgangsgrupperTilTilganger = TilgangsgrupperTilTilganger(tilgangLesGruppeIder = setOf("les"), tilgangSkrivGruppeIder = setOf("skriv")),
        )
        configureRestRuting(
            RestAdapter<Transaksjonskontekst>(
                personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
                populasjonstilgangskontrollProvider = ÅpenTilgangskontroll,
                transaksjonProvider = InMemoryTransaksjonProvider(),
            ),
            endepunkter(oppholdskravvurderingAktivert),
        )
    }

    private fun url() = "/api/personer/${UUID.randomUUID()}/vurderinger/oppholdskrav"

    private object ÅpenTilgangskontroll : PopulasjonstilgangskontrollProvider {
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
    }
}
