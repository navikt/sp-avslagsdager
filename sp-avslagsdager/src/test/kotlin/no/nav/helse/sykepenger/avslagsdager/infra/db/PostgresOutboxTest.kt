package no.nav.helse.sykepenger.avslagsdager.infra.db

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.sykepenger.libs.leaderelection.LeaderElection
import no.nav.sykepenger.libs.outbox.OutboxPubliseringsjobb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.util.UUID

internal class PostgresOutboxTest : DatabaseTest() {
    private val fødselsnummer = "12029240045"
    private val rapid = TestRapid()

    @Test
    fun `meldingen publiseres uendret med fødselsnummeret som nøkkel`() {
        // Given
        val melding = melding("avslagsdager_endret")
        transaksjon { it.outbox.leggTil(fødselsnummer, melding) }

        // When
        publiserUpubliserte()

        // Then
        assertEquals(1, rapid.inspektør.size)
        assertEquals(jacksonObjectMapper().readTree(melding), rapid.inspektør.message(0))
        assertEquals(fødselsnummer, rapid.inspektør.key(0))
    }

    @Test
    fun `meldinger i samme transaksjon publiseres i innsettingsrekkefølge`() {
        // Given
        transaksjon { kontekst ->
            kontekst.outbox.leggTil(fødselsnummer, melding("saksbehandlerhandling"))
            kontekst.outbox.leggTil(fødselsnummer, melding("avslagsdager_endret"))
        }

        // When
        publiserUpubliserte()

        // Then
        assertEquals(
            listOf("saksbehandlerhandling", "avslagsdager_endret"),
            (0 until rapid.inspektør.size).map {
                rapid.inspektør
                    .message(it)
                    .path("@event_name")
                    .asString()
            },
        )
    }

    @Test
    fun `feiler lagringen av vurderingen, blir meldingene i samme transaksjon ikke lagret`() {
        // Given
        val vurdering = vurdering()
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }

        // When
        assertThrows<IllegalStateException> {
            transaksjon { kontekst ->
                kontekst.outbox.leggTil(fødselsnummer, melding("avslagsdager_endret"))
                kontekst.oppholdskravvurderinger.lagre(vurdering)
            }
        }

        // Then
        assertEquals(0, Database.antallRader("outbox"))
    }

    private fun publiserUpubliserte() = OutboxPubliseringsjobb(Database.dataSource, rapid, LeaderElection { true }).publiserUpubliserte()

    private fun melding(eventName: String) = JsonMessage.newMessage(eventName, mapOf("fødselsnummer" to fødselsnummer, "id" to UUID.randomUUID())).toJson()

    private fun vurdering(): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = fødselsnummer,
                behandlingId = UUID.randomUUID(),
                dager = listOf(LocalDate.of(2026, 1, 1)),
                svar = SvarPåOppholdskrav(eøsBorger = true, oppholdIGodkjentOmråde = true, unntakAndreLedd = null, godkjentOppholdInntilFireUker = null),
                fritekstbegrunnelse = "Begrunnelse",
                journalpostId = emptyList(),
                saksbehandlerIdent = "Z999999",
            )
        return (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
    }
}
