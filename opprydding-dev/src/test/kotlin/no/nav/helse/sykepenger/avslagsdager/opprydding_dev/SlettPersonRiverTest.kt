package no.nav.helse.sykepenger.avslagsdager.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import kotliquery.queryOf
import kotliquery.sessionOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

internal class SlettPersonRiverTest {
    private val rapid =
        TestRapid().apply {
            SlettPersonRiver(this, Database.dataSource)
        }

    @BeforeEach
    fun beforeEach() {
        Database.reset()
        rapid.reset()
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun shutdown() {
            Database.shutdown()
        }
    }

    @Test
    fun `publiserer person_slettet etter sletting`() {
        val fødselsnummer = "01020312345"

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(1, rapid.inspektør.size)
        val melding = rapid.inspektør.message(0)
        assertEquals("person_slettet", melding["@event_name"].asString())
        assertEquals(fødselsnummer, melding["fødselsnummer"].asString())
        assertEquals(fødselsnummer, rapid.inspektør.key(0))
    }

    @Test
    fun `sletter personens oppholdskravvurderinger og lar andre personer være`() {
        // Given
        val fødselsnummer = "01020312345"
        val annenPerson = "10203012345"
        lagreOppholdskravvurdering(fødselsnummer)
        lagreOppholdskravvurdering(annenPerson)

        // When
        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        // Then
        assertEquals(1, antallRader("oppholdskravvurdering"))
        assertEquals(1, antallRader("oppholdskravvurdering_dag"))
        assertEquals(1, antallRader("oppholdskravvurdering_vilkarsvurdering"))
        assertEquals(1, antallRader("oppholdskravvurdering", annenPerson))
    }

    private fun lagreOppholdskravvurdering(fødselsnummer: String) {
        val id = UUID.randomUUID()
        sessionOf(Database.dataSource).use { session ->
            session.run(
                queryOf(
                    """
                    INSERT INTO oppholdskravvurdering (
                        id, fødselsnummer, behandling_id, eos_borger, opphold_i_godkjent_omrade, utfall,
                        fritekstbegrunnelse, journalpost_id, saksbehandler_ident, vurdert_tidspunkt
                    )
                    VALUES (?, ?, ?, true, true, 'OPPFYLT', 'Begrunnelse', '{}', 'Z999999', now())
                    """.trimIndent(),
                    id,
                    fødselsnummer,
                    UUID.randomUUID(),
                ).asUpdate,
            )
            session.run(queryOf("INSERT INTO oppholdskravvurdering_dag (vurdering_id, dato) VALUES (?, ?)", id, LocalDate.of(2026, 1, 1)).asUpdate)
            session.run(
                queryOf(
                    """
                    INSERT INTO oppholdskravvurdering_vilkarsvurdering (vurdering_id, vilkårskode, utfall, lov, paragraf, avsnitt, setning, i_kraft_fra)
                    VALUES (?, 'OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND', 'OPPFYLT', 'folketrygdloven', '8-9', 1, 1, '2021-06-01')
                    """.trimIndent(),
                    id,
                ).asUpdate,
            )
        }
    }

    private fun antallRader(
        tabell: String,
        fødselsnummer: String? = null,
    ): Int =
        sessionOf(Database.dataSource).use { session ->
            val sql = if (fødselsnummer == null) "SELECT COUNT(*) FROM $tabell" else "SELECT COUNT(*) FROM $tabell WHERE fødselsnummer = ?"
            session.run(queryOf(sql, *listOfNotNull(fødselsnummer).toTypedArray()).map { it.int(1) }.asSingle)!!
        }

    private fun slettPersonMelding(fødselsnummer: String) =
        """
        {
            "@event_name": "slett_person",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$fødselsnummer"
        }
        """.trimIndent()
}
