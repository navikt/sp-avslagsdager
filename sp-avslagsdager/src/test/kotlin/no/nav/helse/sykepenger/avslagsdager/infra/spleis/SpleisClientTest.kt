package no.nav.helse.sykepenger.avslagsdager.infra.spleis

import com.github.navikt.tbd_libs.mock.MockHttpResponse
import com.github.navikt.tbd_libs.mock.bodyAsString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.net.http.HttpRequest
import kotlin.test.assertEquals

internal class SpleisClientTest {
    @Test
    fun `bruker prod-scope i prod`() {
        val kall = mutableListOf<HttpRequest>()
        val client = SpleisClient.fraMiljø(mapOf("NAIS_CLUSTER_NAME" to "prod-gcp"), svar(kall), FakeTokenProvider)

        client.post("/api/noe", "{}")

        assertEquals(listOf("api://prod-gcp.tbd.spleis-api/.default"), FakeTokenProvider.scopes.takeLast(1))
        assertEquals("http://spleis-api/api/noe", kall.single().uri().toString())
    }

    @Test
    fun `bruker dev-scope utenfor prod`() {
        val client = SpleisClient.fraMiljø(mapOf("NAIS_CLUSTER_NAME" to "dev-gcp"), svar(mutableListOf()), FakeTokenProvider)

        client.post("/api/noe", "{}")

        assertEquals(listOf("api://dev-gcp.tbd.spleis-api/.default"), FakeTokenProvider.scopes.takeLast(1))
    }

    @Test
    fun `sender body og token, og returnerer svaret`() {
        val kall = mutableListOf<HttpRequest>()
        val client = SpleisClient.fraMiljø(emptyMap(), svar(kall, body = """{"svar":true}"""), FakeTokenProvider)

        val svar = client.post("/api/noe", """{"fødselsnummer":"12345678910"}""")

        assertEquals("""{"svar":true}""", svar)
        val request = kall.single()
        assertEquals("POST", request.method())
        assertEquals("""{"fødselsnummer":"12345678910"}""", request.bodyAsString())
        assertEquals("Bearer token", request.headers().firstValue("Authorization").orElse(null))
    }

    @Test
    fun `feiler når spleis-api svarer med noe annet enn 200`() {
        val client = SpleisClient.fraMiljø(emptyMap(), svar(mutableListOf(), status = 503), FakeTokenProvider)

        assertThrows<SpleisClientException> { client.post("/api/noe", "{}") }
    }

    @Test
    fun `feiler når spleis-api ikke kan kontaktes`() {
        val client = SpleisClient.fraMiljø(emptyMap(), FakeHttpClient { throw IOException("nede") }, FakeTokenProvider)

        assertThrows<SpleisClientException> { client.post("/api/noe", "{}") }
    }

    private fun svar(
        kall: MutableList<HttpRequest>,
        body: String = "{}",
        status: Int = 200,
    ) = FakeHttpClient { request ->
        kall.add(request)
        MockHttpResponse(body, status)
    }
}
