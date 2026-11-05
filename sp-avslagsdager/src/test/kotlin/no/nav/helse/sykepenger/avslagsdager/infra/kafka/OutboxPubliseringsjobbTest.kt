package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import no.nav.helse.sykepenger.avslagsdager.application.HandlingType
import no.nav.helse.sykepenger.avslagsdager.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.avslagsdager.application.OutboxKonvolutt
import no.nav.helse.sykepenger.avslagsdager.application.OutboxMelding
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.leaderelection.LeaderElection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class OutboxPubliseringsjobbTest {
    private val identitetsnummer = Identitetsnummer("12029240045")
    private val transaksjon = InMemoryTransaksjonProvider()

    @Test
    fun `saksbehandlerhandling publiseres med feltene spesialist trenger`() {
        // Given
        val rapid = TestRapid()
        val handlingId = UUID.randomUUID()
        val behandlingId = UUID.randomUUID()
        transaksjon.outbox.leggTil(OutboxKonvolutt.ny(saksbehandlerhandling(handlingId, behandlingId), identitetsnummer))

        // When
        nyJobb(rapid).kjørEnRunde()

        // Then
        val melding = rapid.inspektør.message(0)
        assertEquals("saksbehandlerhandling", melding.path("@event_name").asString())
        assertEquals(identitetsnummer.value, melding.path("fødselsnummer").asString())
        assertEquals("Z999999", melding.path("saksbehandlerIdent").asString())
        assertEquals(handlingId.toString(), melding.path("handlingId").asString())
        assertEquals("MANUELT_VURDERT_OPPHOLDSKRAV", melding.path("handlingType").asString())
        assertTrue(melding.path("kreverTotrinnsvurdering").asBoolean())
        assertEquals("2026-01-10T12:00:00Z", melding.path("tidspunkt").asString())
        assertEquals(behandlingId.toString(), melding.path("behandlingId").asString())
        assertFalse(melding.has("skjæringstidspunkt"))
        assertEquals(identitetsnummer.value, rapid.inspektør.key(0))
    }

    @Test
    fun `avslagsdager_endret publiseres med perioder og manuellVurdering, uten utfall`() {
        // Given
        val rapid = TestRapid()
        val vurderingId = UUID.randomUUID()
        transaksjon.outbox.leggTil(
            OutboxKonvolutt.ny(
                OutboxMelding.AvslagsdagerEndret(
                    vurderingId = vurderingId,
                    perioder = listOf(Periode(1.jan, 3.jan), Periode(10.jan, 10.jan)),
                    manuellVurdering = true,
                ),
                identitetsnummer,
            ),
        )

        // When
        nyJobb(rapid).kjørEnRunde()

        // Then
        val melding = rapid.inspektør.message(0)
        assertEquals("avslagsdager_endret", melding.path("@event_name").asString())
        assertEquals(identitetsnummer.value, melding.path("fødselsnummer").asString())
        assertEquals(vurderingId.toString(), melding.path("vurderingId").asString())
        assertEquals(
            listOf("2026-01-01" to "2026-01-03", "2026-01-10" to "2026-01-10"),
            melding.path("perioder").values().map { it.path("fom").asString() to it.path("tom").asString() },
        )
        assertTrue(melding.path("manuellVurdering").asBoolean())
        assertFalse(melding.has("utfall"))
        assertEquals(identitetsnummer.value, rapid.inspektør.key(0))
    }

    @Test
    fun `meldingene publiseres i rekkefølgen de ble lagt til og markeres som publisert`() {
        // Given
        val rapid = TestRapid()
        transaksjon.outbox.leggTil(OutboxKonvolutt.ny(saksbehandlerhandling(), identitetsnummer))
        transaksjon.outbox.leggTil(OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer))

        // When
        nyJobb(rapid).kjørEnRunde()

        // Then
        assertEquals(listOf("saksbehandlerhandling", "avslagsdager_endret"), rapid.eventNames())
        assertTrue(transaksjon.outbox.hentUpubliserte().isEmpty())
    }

    @Test
    fun `en publisert melding sendes ikke på nytt`() {
        // Given
        val rapid = TestRapid()
        val jobb = nyJobb(rapid)
        transaksjon.outbox.leggTil(OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer))
        jobb.kjørEnRunde()

        // When
        jobb.kjørEnRunde()

        // Then
        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `publiserer ikke når poden ikke er leader`() {
        // Given
        val rapid = TestRapid()
        val konvolutt = OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer)
        transaksjon.outbox.leggTil(konvolutt)

        // When
        nyJobb(rapid, erLeader = false).kjørEnRundeHvisLeader()

        // Then
        assertEquals(0, rapid.inspektør.size)
        assertEquals(listOf(konvolutt), transaksjon.outbox.hentUpubliserte())
    }

    @Test
    fun `publiserer når poden er leader`() {
        // Given
        val rapid = TestRapid()
        transaksjon.outbox.leggTil(OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer))

        // When
        nyJobb(rapid, erLeader = true).kjørEnRundeHvisLeader()

        // Then
        assertEquals(1, rapid.inspektør.size)
    }

    @Test
    fun `feil under publisering lar meldingen og senere meldinger være upubliserte`() {
        // Given
        val meldinger = (1..3).map { OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer) }
        meldinger.forEach(transaksjon.outbox::leggTil)

        // When
        nyJobb(SvikterPåPubliseringNr(2)).kjørEnRunde()

        // Then
        assertEquals(meldinger.drop(1), transaksjon.outbox.hentUpubliserte())
    }

    private fun nyJobb(
        rapid: RapidsConnection,
        erLeader: Boolean = true,
    ) = OutboxPubliseringsjobb(rapid, transaksjon, LeaderElection { erLeader })

    private fun saksbehandlerhandling(
        handlingId: UUID = UUID.randomUUID(),
        behandlingId: UUID = UUID.randomUUID(),
    ) = OutboxMelding.Saksbehandlerhandling(
        saksbehandlerIdent = "Z999999",
        handlingId = handlingId,
        handlingType = HandlingType.MANUELT_VURDERT_OPPHOLDSKRAV,
        kreverTotrinnsvurdering = true,
        tidspunkt = Instant.parse("2026-01-10T12:00:00Z"),
        behandlingId = behandlingId,
    )

    private fun avslagsdagerEndret() = OutboxMelding.AvslagsdagerEndret(vurderingId = UUID.randomUUID(), perioder = listOf(Periode(1.jan, 1.jan)), manuellVurdering = true)

    private class SvikterPåPubliseringNr(
        private val feilerPå: Int,
    ) : RapidsConnection() {
        private var publiseringer = 0

        override fun publish(message: String) {
            publish(listOf(OutgoingMessage(message)))
        }

        override fun publish(
            key: String,
            message: String,
        ) {
            publish(listOf(OutgoingMessage(message, key)))
        }

        override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
            publiseringer++
            val feilede =
                if (publiseringer == feilerPå) {
                    messages.mapIndexed { index, message -> FailedMessage(index, message, RuntimeException("Kafka er nede")) }
                } else {
                    emptyList()
                }
            return emptyList<SentMessage>() to feilede
        }

        override fun rapidName() = "svikter-rapid"

        override fun start() = Unit

        override fun stop() = Unit
    }
}

private fun TestRapid.eventNames() = (0 until inspektør.size).map { inspektør.message(it).path("@event_name").asString() }

private val Int.jan get() = LocalDate.of(2026, 1, this)
