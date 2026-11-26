package no.nav.helse.sykepenger.avslagsdager.infra.spedisjon

import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.spedisjon.SpedisjonClient
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.infra.spleis.FakeHttpClient
import no.nav.helse.sykepenger.avslagsdager.infra.spleis.FakeTokenProvider
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/** Spedisjon med sendte søknader. Teller oppslagene, så testene kan se at en søknad bare slås opp én gang. */
internal class FakeSpedisjon {
    private val meldinger = mutableMapOf<UUID, Map<String, Any>>()
    private var feiler = false

    val oppslag = mutableListOf<UUID>()

    val søknadsoppslag =
        SpedisjonSøknadsoppslag(
            SpedisjonClient(
                httpClient = FakeHttpClient(::hentMelding),
                objectMapper = objectMapper,
                tokenProvider = FakeTokenProvider,
                baseUrl = "http://spedisjon",
                scope = "api://test.tbd.spedisjon/.default",
            ),
        )

    fun feiler() {
        feiler = true
    }

    fun sendtSøknad(
        fødselsnummer: String,
        utlandsopphold: List<Periode> = emptyList(),
        ferie: List<Periode> = emptyList(),
        korrigerer: SendtSøknad? = null,
    ): SendtSøknad {
        val meldingsreferanseId = UUID.randomUUID()
        val søknadId = UUID.randomUUID()
        val fravar =
            utlandsopphold.map { mapOf("type" to "UTLANDSOPPHOLD", "fom" to it.fom.toString(), "tom" to it.tom.toString()) } +
                ferie.map { mapOf("type" to "FERIE", "fom" to it.fom.toString(), "tom" to it.tom.toString()) }
        val søknad =
            mapOf(
                "id" to søknadId.toString(),
                "fnr" to fødselsnummer,
                "status" to "SENDT",
                "fravar" to fravar,
            ) + listOfNotNull(korrigerer?.let { "korrigerer" to it.søknadId.toString() })
        meldinger[meldingsreferanseId] =
            mapOf(
                "type" to "sendt_søknad_nav",
                "fnr" to fødselsnummer,
                "internDokumentId" to meldingsreferanseId.toString(),
                "eksternDokumentId" to søknadId.toString(),
                "duplikatkontroll" to UUID.randomUUID().toString(),
                "jsonBody" to objectMapper.writeValueAsString(søknad),
            )
        return SendtSøknad(meldingsreferanseId, søknadId)
    }

    private fun hentMelding(request: HttpRequest): HttpResponse<String> {
        val path = request.uri().path
        check(request.method() == "GET" && path.startsWith("/api/melding/")) { "Uventet kall til Spedisjon: ${request.method()} ${request.uri()}" }
        if (feiler) return MockHttpResponse(feilrespons("Spedisjon er nede"), 503)
        val internDokumentId = UUID.fromString(path.removePrefix("/api/melding/"))
        oppslag.add(internDokumentId)
        val melding = meldinger[internDokumentId] ?: return MockHttpResponse(feilrespons("Fant ikke meldingen"), 404)
        return MockHttpResponse(objectMapper.writeValueAsString(melding), 200)
    }

    private fun feilrespons(detail: String) =
        objectMapper.writeValueAsString(
            mapOf("type" to "about:blank", "title" to "Feil", "status" to 500, "detail" to detail, "instance" to "/api/melding"),
        )

    internal data class SendtSøknad(
        val meldingsreferanseId: UUID,
        val søknadId: UUID,
    )

    private companion object {
        val objectMapper = jacksonObjectMapper()
    }
}
