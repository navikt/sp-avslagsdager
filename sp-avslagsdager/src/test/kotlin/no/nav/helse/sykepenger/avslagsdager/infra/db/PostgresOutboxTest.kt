package no.nav.helse.sykepenger.avslagsdager.infra.db

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import no.nav.helse.sykepenger.avslagsdager.application.HandlingType
import no.nav.helse.sykepenger.avslagsdager.application.OutboxKonvolutt
import no.nav.helse.sykepenger.avslagsdager.application.OutboxMelding
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.Periode
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PostgresOutboxTest : DatabaseTest() {
    private val identitetsnummer = Identitetsnummer("12029240045")

    @Test
    fun `saksbehandlerhandling lagres med typediskriminator og leses tilbake`() {
        // Given
        val konvolutt = OutboxKonvolutt.ny(saksbehandlerhandling(), identitetsnummer)

        // When
        transaksjon { it.outbox.leggTil(konvolutt) }

        // Then
        assertEquals(listOf("SAKSBEHANDLERHANDLING"), Database.meldingstyperIOutbox())
        assertEquals(listOf(konvolutt), transaksjon { it.outbox.hentUpubliserte() })
    }

    @Test
    fun `avslagsdager_endret lagres med typediskriminator og leses tilbake`() {
        // Given
        val konvolutt = OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer)

        // When
        transaksjon { it.outbox.leggTil(konvolutt) }

        // Then
        assertEquals(listOf("AVSLAGSDAGER_ENDRET"), Database.meldingstyperIOutbox())
        assertEquals(listOf(konvolutt), transaksjon { it.outbox.hentUpubliserte() })
    }

    @Test
    fun `meldinger i samme transaksjon hentes i innsettingsrekkefølge`() {
        // Given
        val første = OutboxKonvolutt.ny(saksbehandlerhandling(), identitetsnummer)
        val andre = OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer)

        // When
        transaksjon { kontekst ->
            kontekst.outbox.leggTil(første)
            kontekst.outbox.leggTil(andre)
        }

        // Then
        assertEquals(listOf(første, andre), transaksjon { it.outbox.hentUpubliserte() })
    }

    @Test
    fun `henter bare de eldste meldingene når maksAntall er satt`() {
        // Given
        val meldinger = (1..3).map { OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer) }
        transaksjon { kontekst -> meldinger.forEach(kontekst.outbox::leggTil) }

        // When
        val upubliserte = transaksjon { it.outbox.hentUpubliserte(maksAntall = 2) }

        // Then
        assertEquals(meldinger.take(2), upubliserte)
    }

    @Test
    fun `publiserte meldinger hentes ikke igjen`() {
        // Given
        val konvolutt = OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer)
        transaksjon { it.outbox.leggTil(konvolutt) }

        // When
        transaksjon { it.outbox.markerSomSendt(konvolutt.id) }

        // Then
        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }

    @Test
    fun `feiler lagringen av vurderingen, blir meldingene i samme transaksjon ikke lagret`() {
        // Given
        val vurdering = vurdering()
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }

        // When
        assertThrows<IllegalStateException> {
            transaksjon { kontekst ->
                kontekst.outbox.leggTil(OutboxKonvolutt.ny(avslagsdagerEndret(), identitetsnummer))
                kontekst.oppholdskravvurderinger.lagre(vurdering)
            }
        }

        // Then
        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }

    private fun saksbehandlerhandling() =
        OutboxMelding.Saksbehandlerhandling(
            saksbehandlerIdent = "Z999999",
            handlingId = UUID.randomUUID(),
            handlingType = HandlingType.AVSLAGSDAGER_ENDRET,
            kreverTotrinnsvurdering = true,
            tidspunkt = Instant.parse("2026-01-10T12:00:00Z"),
            behandlingId = UUID.randomUUID(),
        )

    private fun avslagsdagerEndret() =
        OutboxMelding.AvslagsdagerEndret(
            vurderingId = UUID.randomUUID(),
            perioder =
                listOf(
                    Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)),
                    Periode(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)),
                ),
        )

    private fun vurdering(): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = identitetsnummer.value,
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
