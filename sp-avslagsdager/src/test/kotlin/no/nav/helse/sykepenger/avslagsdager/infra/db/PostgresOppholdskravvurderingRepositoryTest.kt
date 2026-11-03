package no.nav.helse.sykepenger.avslagsdager.infra.db

import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingId
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PostgresOppholdskravvurderingRepositoryTest : DatabaseTest() {
    @Test
    fun `en lagret vurdering kan hentes igjen med alle felter`() {
        // Given
        val vurdering =
            vurdering(
                SvarPåOppholdskrav(
                    eøsBorger = false,
                    oppholdIGodkjentOmråde = false,
                    unntakAndreLedd = UnntakAndreLedd.INGEN,
                    godkjentOppholdInntilFireUker = false,
                ),
                journalpostId = listOf("123", "456"),
            )

        // When
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }

        // Then
        assertEquals(vurdering, transaksjon { it.oppholdskravvurderinger.finn(vurdering.id) })
    }

    @Test
    fun `svar som ikke ble brukt, lagres som null`() {
        // Given
        val vurdering =
            vurdering(
                SvarPåOppholdskrav(
                    eøsBorger = true,
                    oppholdIGodkjentOmråde = true,
                    unntakAndreLedd = null,
                    godkjentOppholdInntilFireUker = null,
                ),
                journalpostId = emptyList(),
            )

        // When
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }

        // Then
        assertEquals(vurdering, transaksjon { it.oppholdskravvurderinger.finn(vurdering.id) })
    }

    @Test
    fun `en vurdering kan ikke lagres to ganger`() {
        // Given
        val vurdering = vurdering()
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }

        // When / Then
        assertThrows<IllegalStateException> { transaksjon { it.oppholdskravvurderinger.lagre(vurdering) } }
    }

    @Test
    fun `ukjent id gir null`() {
        assertNull(transaksjon { it.oppholdskravvurderinger.finn(OppholdskravvurderingId.ny()) })
    }

    private fun vurdering(
        svar: SvarPåOppholdskrav =
            SvarPåOppholdskrav(
                eøsBorger = true,
                oppholdIGodkjentOmråde = false,
                unntakAndreLedd = UnntakAndreLedd.INNLAGT_I_HELSEINSTITUSJON,
                godkjentOppholdInntilFireUker = null,
            ),
        journalpostId: List<String> = listOf("123"),
    ): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = "12345678910",
                behandlingId = UUID.randomUUID(),
                dager = listOf(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)),
                svar = svar,
                fritekstbegrunnelse = "Begrunnelse",
                journalpostId = journalpostId,
                saksbehandlerIdent = "A123456",
                // Postgres lagrer mikrosekunder
                vurdertTidspunkt = Instant.now().truncatedTo(ChronoUnit.MICROS),
            )
        return (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
    }
}
