package no.nav.helse.sykepenger.avslagsdager.leaderelection

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NaisLeaderElectionTest {
    private val elector = WireMockServer(wireMockConfig().dynamicPort()).also(WireMockServer::start)
    private lateinit var leaderElection: NaisLeaderElection

    @BeforeEach
    fun beforeEach() {
        elector.resetAll()
        leaderElection =
            NaisLeaderElection(
                electorSseUrl = "${elector.baseUrl()}/sse",
                hostname = "sp-avslagsdager-abc123",
                ventetidFørNyTilkobling = 1.seconds,
            )
    }

    @AfterAll
    fun afterAll() {
        elector.stop()
    }

    @Test
    fun `er ikke leader før vi har hørt fra elector`() {
        // Then
        assertFalse(leaderElection.isLeader())
    }

    @Test
    fun `er leader når elector sender vårt hostname`() {
        // Given
        stubSseStrøm("""{"name":"sp-avslagsdager-abc123","last_update":"2026-09-29T14:00:00Z"}""")

        // When
        leaderElection.lesStrømmenTilDenLukkes()

        // Then
        assertTrue(leaderElection.isLeader())
    }

    @Test
    fun `siste hendelse fra elector gjelder`() {
        // Given
        stubSseStrøm(
            """{"name":"sp-avslagsdager-abc123","last_update":"2026-09-29T14:00:00Z"}""",
            """{"name":"sp-avslagsdager-def456","last_update":"2026-09-29T14:05:00Z"}""",
        )

        // When
        leaderElection.lesStrømmenTilDenLukkes()

        // Then
        assertFalse(leaderElection.isLeader())
    }

    @Test
    fun `er ikke leader når elector ikke har valgt noen ennå`() {
        // Given
        stubSseStrøm("{}")

        // When
        leaderElection.lesStrømmenTilDenLukkes()

        // Then
        assertFalse(leaderElection.isLeader())
    }

    @Test
    fun `beholder kjent leader når elector svarer med feil`() {
        // Given
        stubSseStrøm("""{"name":"sp-avslagsdager-abc123","last_update":"2026-09-29T14:00:00Z"}""")
        leaderElection.lesStrømmenTilDenLukkes()
        elector.stubFor(get(urlPathEqualTo("/sse")).willReturn(aResponse().withStatus(503)))

        // When
        assertThrows<IllegalStateException> { leaderElection.lesStrømmenTilDenLukkes() }

        // Then
        assertTrue(leaderElection.isLeader())
    }

    private fun stubSseStrøm(vararg hendelser: String) {
        elector.stubFor(
            get(urlPathEqualTo("/sse")).willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(hendelser.joinToString(separator = "") { "data: $it\n\n" }),
            ),
        )
    }
}
