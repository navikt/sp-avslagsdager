package no.nav.helse.sykepenger.avslagsdager.infra.spleis

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal class SpleisClient(
    private val httpClient: HttpClient,
    private val tokenProvider: AzureTokenProvider,
    private val baseUrl: String,
    private val scope: String,
) {
    // Spleis-api tar fødselsnummer i body, ikke i URL-en, så kallene er POST.
    fun post(
        sti: String,
        body: String,
    ): String {
        val token = tokenProvider.bearerToken(scope).getOrThrow().token
        val request =
            HttpRequest
                .newBuilder(URI("$baseUrl$sti"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .header("callId", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        val response =
            try {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (ex: IOException) {
                throw SpleisClientException("Klarte ikke kontakte spleis-api", ex)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SpleisClientException("Kall til spleis-api ble avbrutt", ex)
            }
        if (response.statusCode() != 200) throw SpleisClientException("Uventet svar fra spleis-api: HTTP ${response.statusCode()}")
        return response.body()
    }

    companion object {
        fun fraMiljø(
            env: Map<String, String>,
            httpClient: HttpClient,
            tokenProvider: AzureTokenProvider,
        ): SpleisClient {
            val cluster = if (env["NAIS_CLUSTER_NAME"]?.startsWith("prod") == true) "prod-gcp" else "dev-gcp"
            return SpleisClient(
                httpClient = httpClient,
                tokenProvider = tokenProvider,
                baseUrl = "http://spleis-api",
                scope = "api://$cluster.tbd.spleis-api/.default",
            )
        }
    }
}

internal class SpleisClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
