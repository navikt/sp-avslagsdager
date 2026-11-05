package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.infra.db.Database
import no.nav.helse.sykepenger.avslagsdager.infra.db.DatabaseTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

internal class AvslagsdagerRiverTest : DatabaseTest() {
    private val rapid = TestRapid().apply { AvslagsdagerRiver(this, Database.transaksjonProvider) }

    @Test
    fun `en person uten vurderinger i perioden får tomme lister`() {
        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan), FØDSELSNUMMER)

        // Then
        assertEquals(1, rapid.inspektør.size)
        assertEquals(FØDSELSNUMMER, rapid.inspektør.key(0))
        assertEquals(Løsning(emptyList(), emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `svarer med hver dag i perioden i en vurdering som ikke er oppfylt`() {
        // Given
        val vurdering = lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderingIder = listOf(vurdering.id.value),
                avslagsdager =
                    listOf(
                        Oppføring(vurdering.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT),
                        Oppføring(vurdering.id.value, "2026-01-06", VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `dager utenfor perioden er ikke med`() {
        // Given
        val vurdering = lagre(ikkeOppfylt, dager = listOf(4.jan, 5.jan, 6.jan, 7.jan))
        lagre(ikkeOppfylt, dager = listOf(1.feb))

        // When
        rapid.sendTestMessage(behov(fom = 5.jan, tom = 6.jan))

        // Then
        assertEquals(
            Løsning(
                vurderingIder = listOf(vurdering.id.value),
                avslagsdager =
                    listOf(
                        Oppføring(vurdering.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT),
                        Oppføring(vurdering.id.value, "2026-01-06", VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `flere vurderinger gir dagene sortert på dato og ID-ene i rekkefølgen de først gjelder`() {
        // Given
        val senere = lagre(ikkeOppfylt, dager = listOf(10.jan))
        val tidligere = lagre(ikkeOppfyltIkkeEøsBorger, dager = listOf(2.jan, 1.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderingIder = listOf(tidligere.id.value, senere.id.value),
                avslagsdager =
                    listOf(
                        Oppføring(tidligere.id.value, "2026-01-01", VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                        Oppføring(tidligere.id.value, "2026-01-02", VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                        Oppføring(senere.id.value, "2026-01-10", VILKÅRSKODER_IKKE_OPPFYLT),
                    ),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `en oppfylt vurdering gir ID-en, men ingen avslagsdager`() {
        // Given
        val vurdering = lagre(oppfylt, dager = listOf(5.jan))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(Løsning(listOf(vurdering.id.value), emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `en oppfylt omgjøring fjerner avslaget for dagene den omfatter`() {
        // Given
        val avslag = lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan), vurdertTidspunkt = kl(12))
        val omgjøring = lagre(oppfylt, dager = listOf(6.jan), vurdertTidspunkt = kl(13))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderingIder = listOf(avslag.id.value, omgjøring.id.value),
                avslagsdager = listOf(Oppføring(avslag.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT)),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `en vurdering som er omgjort for alle dagene sine, gjelder ikke lenger`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(12))
        val omgjøring = lagre(ikkeOppfyltIkkeEøsBorger, dager = listOf(5.jan), vurdertTidspunkt = kl(13))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(
            Løsning(
                vurderingIder = listOf(omgjøring.id.value),
                avslagsdager = listOf(Oppføring(omgjøring.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER)),
            ),
            rapid.inspektør.message(0).løsning(),
        )
    }

    @Test
    fun `den nyeste vurderingen gjelder uansett rekkefølgen de er lagret i`() {
        // Given
        val nyest = lagre(oppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(13))
        lagre(ikkeOppfylt, dager = listOf(5.jan), vurdertTidspunkt = kl(12))

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(Løsning(listOf(nyest.id.value), emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `vurderinger for en annen person er ikke med`() {
        // Given
        lagre(ikkeOppfylt, dager = listOf(5.jan), fødselsnummer = "01010112345")

        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan))

        // Then
        assertEquals(Løsning(emptyList(), emptyList()), rapid.inspektør.message(0).løsning())
    }

    @Test
    fun `behov som allerede har en løsning, ignoreres`() {
        // Given
        val medLøsning =
            behov(fom = 1.jan, tom = 31.jan).replace(
                "\"@behov\"",
                "\"@løsning\": { \"Avslagsdager\": { \"vurderingIder\": [], \"avslagsdager\": [] } }, \"@behov\"",
            )

        // When
        rapid.sendTestMessage(medLøsning)

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `andre behov ignoreres`() {
        // When
        rapid.sendTestMessage(behov(fom = 1.jan, tom = 31.jan).replace("[\"Avslagsdager\"]", "[\"EtHeltAnnetBehov\"]"))

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `behov uten gyldig periode ignoreres`() {
        listOf(
            """{ "tom": "2026-01-31" }""",
            """{ "fom": "2026-01-01" }""",
            """{ "fom": "ikke en dato", "tom": "2026-01-31" }""",
            """{ "fom": "2026-01-31", "tom": "2026-01-01" }""",
            """{ "vurderingIder": ["${UUID.randomUUID()}"] }""",
        ).forEach { periode ->
            // When
            rapid.sendTestMessage(behov(periode))

            // Then
            assertEquals(0, rapid.inspektør.size, periode)
        }
    }

    private fun lagre(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate>,
        fødselsnummer: String = FØDSELSNUMMER,
        vurdertTidspunkt: Instant = kl(12),
    ): Oppholdskravvurdering {
        val resultat =
            Oppholdskravvurdering.vurder(
                fødselsnummer = fødselsnummer,
                behandlingId = UUID.randomUUID(),
                dager = dager,
                svar = svar,
                fritekstbegrunnelse = "Begrunnelse",
                journalpostId = emptyList(),
                saksbehandlerIdent = "A123456",
                vurdertTidspunkt = vurdertTidspunkt,
            )
        val vurdering = (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }
        return vurdering
    }

    private data class Løsning(
        val vurderingIder: List<UUID>,
        val avslagsdager: List<Oppføring>,
    )

    private data class Oppføring(
        val vurderingId: UUID,
        val dato: String,
        val vilkårskoder: List<String>,
    )

    private fun JsonNode.løsning(): Løsning {
        val løsning = path("@løsning").path("Avslagsdager")
        return Løsning(
            vurderingIder = løsning.path("vurderingIder").values().map { UUID.fromString(it.asString()) },
            avslagsdager =
                løsning.path("avslagsdager").values().map { oppføring ->
                    Oppføring(
                        vurderingId = UUID.fromString(oppføring.path("vurderingId").asString()),
                        dato = oppføring.path("dato").asString(),
                        vilkårskoder = oppføring.path("vilkårskoder").values().map { it.asString() },
                    )
                },
        )
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"

        val ikkeOppfylt =
            SvarPåOppholdskrav(
                eøsBorger = true,
                oppholdIGodkjentOmråde = false,
                unntakAndreLedd = UnntakAndreLedd.INGEN,
                godkjentOppholdInntilFireUker = false,
            )

        val oppfylt =
            SvarPåOppholdskrav(
                eøsBorger = true,
                oppholdIGodkjentOmråde = true,
                unntakAndreLedd = null,
                godkjentOppholdInntilFireUker = null,
            )

        val ikkeOppfyltIkkeEøsBorger = ikkeOppfylt.copy(eøsBorger = false)

        val VILKÅRSKODER_IKKE_OPPFYLT =
            listOf(
                "OPPHOLD_I_NORGE_EØS_ELLER_AVTALELAND",
                "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8",
                "UNNTAK_INNLAGT_HELSEINSTITUSJON",
                "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER",
            )

        val VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER =
            listOf(
                "OPPHOLD_I_NORGE_IKKE_EØS_BORGER",
                "UNNTAK_MEDLEM_ETTER_2_5_2_6_2_8",
                "UNNTAK_INNLAGT_HELSEINSTITUSJON",
                "UNNTAK_GODKJENT_OPPHOLD_INNTIL_FIRE_UKER",
            )

        val Int.jan: LocalDate get() = LocalDate.of(2026, 1, this)
        val Int.feb: LocalDate get() = LocalDate.of(2026, 2, this)

        fun kl(time: Int): Instant = Instant.parse("2026-01-10T%02d:00:00Z".format(time))

        fun behov(
            fom: LocalDate,
            tom: LocalDate,
        ) = behov("""{ "fom": "$fom", "tom": "$tom" }""")

        fun behov(behovsinnhold: String): String {
            @Language("JSON")
            val behov = """
            {
              "@event_name": "behov",
              "@id": "${UUID.randomUUID()}",
              "@behov": ["Avslagsdager"],
              "fødselsnummer": "$FØDSELSNUMMER",
              "Avslagsdager": $behovsinnhold
            }
            """
            return behov
        }
    }
}
