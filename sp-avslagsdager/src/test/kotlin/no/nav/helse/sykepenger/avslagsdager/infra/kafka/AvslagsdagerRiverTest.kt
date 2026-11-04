package no.nav.helse.sykepenger.avslagsdager.infra.kafka

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.avslagsdager.domain.Oppholdskravvurdering
import no.nav.helse.sykepenger.avslagsdager.domain.OppholdskravvurderingResultat
import no.nav.helse.sykepenger.avslagsdager.domain.SvarPåOppholdskrav
import no.nav.helse.sykepenger.avslagsdager.domain.UnntakAndreLedd
import no.nav.helse.sykepenger.avslagsdager.infra.db.Database
import no.nav.helse.sykepenger.avslagsdager.infra.db.DatabaseTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AvslagsdagerRiverTest : DatabaseTest() {
    private val rapid = TestRapid().apply { AvslagsdagerRiver(this, Database.transaksjonProvider) }

    @Test
    fun `svarer med hver dag i en vurdering som ikke er oppfylt`() {
        // Given
        val vurdering = lagre(ikkeOppfylt, dager = listOf(5.jan, 6.jan))

        // When
        rapid.sendTestMessage(behov(vurdering.id.value), FØDSELSNUMMER)

        // Then
        assertEquals(1, rapid.inspektør.size)
        assertEquals(FØDSELSNUMMER, rapid.inspektør.key(0))
        assertEquals(
            listOf(
                Oppføring(vurdering.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT),
                Oppføring(vurdering.id.value, "2026-01-06", VILKÅRSKODER_IKKE_OPPFYLT),
            ),
            rapid.inspektør.message(0).avslagsdager(),
        )
    }

    @Test
    fun `svarer med avslagsdagene fra alle vurderingene i rekkefølgen Spleis spør om, og hopper over oppfylte vurderinger`() {
        // Given
        val første = lagre(ikkeOppfylt, dager = listOf(5.jan))
        val oppfylt = lagre(oppfylt, dager = listOf(6.jan))
        val andre = lagre(ikkeOppfyltIkkeEøsBorger, dager = listOf(2.jan, 1.jan))

        // When
        rapid.sendTestMessage(behov(andre.id.value, oppfylt.id.value, første.id.value))

        // Then
        assertEquals(
            listOf(
                Oppføring(andre.id.value, "2026-01-01", VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                Oppføring(andre.id.value, "2026-01-02", VILKÅRSKODER_IKKE_OPPFYLT_IKKE_EØS_BORGER),
                Oppføring(første.id.value, "2026-01-05", VILKÅRSKODER_IKKE_OPPFYLT),
            ),
            rapid.inspektør.message(0).avslagsdager(),
        )
    }

    @Test
    fun `svarer med en tom liste når ingen av vurderingene har avslåtte dager`() {
        // Given
        val vurdering = lagre(oppfylt)

        // When
        rapid.sendTestMessage(behov(vurdering.id.value))

        // Then
        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0).avslagsdagerNode()
        assertTrue(løsning.isArray)
        assertEquals(0, løsning.size())
    }

    @Test
    fun `en ukjent vurderings-ID gir ingen løsning, og feilen logges`() {
        // Given
        val kjent = lagre(ikkeOppfylt)

        // When
        val feil = loggfeil { rapid.sendTestMessage(behov(kjent.id.value, UUID.randomUUID())) }

        // Then
        assertEquals(0, rapid.inspektør.size)
        assertEquals(listOf("Kan ikke svare på behovet Avslagsdager. Noen av vurderingene finnes ikke for personen"), feil)
    }

    @Test
    fun `en vurdering for en annen person gir ingen løsning, og feilen logges`() {
        // Given
        val annenPerson = lagre(ikkeOppfylt, fødselsnummer = "01010112345")

        // When
        val feil = loggfeil { rapid.sendTestMessage(behov(annenPerson.id.value)) }

        // Then
        assertEquals(0, rapid.inspektør.size)
        assertEquals(listOf("Kan ikke svare på behovet Avslagsdager. Noen av vurderingene finnes ikke for personen"), feil)
    }

    @Test
    fun `behov som allerede har en løsning, ignoreres`() {
        // Given
        val vurdering = lagre(ikkeOppfylt)
        val medLøsning =
            behov(vurdering.id.value).replace(
                "\"@behov\"",
                "\"@løsning\": { \"Avslagsdager\": { \"avslagsdager\": [] } }, \"@behov\"",
            )

        // When
        rapid.sendTestMessage(medLøsning)

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `andre behov ignoreres`() {
        // Given
        val vurdering = lagre(ikkeOppfylt)

        // When
        rapid.sendTestMessage(behov(vurdering.id.value).replace("[\"Avslagsdager\"]", "[\"EtHeltAnnetBehov\"]"))

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `behov med en tom liste av vurderings-ID-er ignoreres`() {
        // When
        rapid.sendTestMessage(behov())

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `behov med en vurderings-ID som ikke er en UUID, ignoreres`() {
        // When
        rapid.sendTestMessage(behov().replace("\"vurderingIder\": []", "\"vurderingIder\": [\"ikke-en-uuid\"]"))

        // Then
        assertEquals(0, rapid.inspektør.size)
    }

    private fun lagre(
        svar: SvarPåOppholdskrav,
        dager: List<LocalDate> = listOf(5.jan),
        fødselsnummer: String = FØDSELSNUMMER,
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
                vurdertTidspunkt = Instant.parse("2026-01-10T12:00:00Z"),
            )
        val vurdering = (resultat as OppholdskravvurderingResultat.Vurdert).vurdering
        transaksjon { it.oppholdskravvurderinger.lagre(vurdering) }
        return vurdering
    }

    // Meldingene som går til nav-logs med nivå ERROR. Detaljene og MDC går bare til Team Logs.
    private fun loggfeil(block: () -> Unit): List<String> {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = context.getLogger(AvslagsdagerRiver::class.java)
        val events =
            ListAppender<ILoggingEvent>().apply {
                this.context = context
                start()
            }
        logger.addAppender(events)
        try {
            block()
        } finally {
            logger.detachAppender(events)
        }
        return events.list
            .filter { it.level == Level.ERROR && it.markerList.orEmpty().none { marker -> marker.name == "NOT_NAV_LOGS" } }
            .map { it.formattedMessage }
    }

    private data class Oppføring(
        val vurderingId: UUID,
        val dato: String,
        val vilkårskoder: List<String>,
    )

    private fun JsonNode.avslagsdagerNode() = path("@løsning").path("Avslagsdager").path("avslagsdager")

    private fun JsonNode.avslagsdager() =
        avslagsdagerNode().values().map { oppføring ->
            Oppføring(
                vurderingId = UUID.fromString(oppføring.path("vurderingId").asString()),
                dato = oppføring.path("dato").asString(),
                vilkårskoder = oppføring.path("vilkårskoder").values().map { it.asString() },
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

        fun behov(
            vararg vurderingIder: UUID,
            fødselsnummer: String = FØDSELSNUMMER,
        ): String {
            @Language("JSON")
            val behov = """
            {
              "@event_name": "behov",
              "@id": "${UUID.randomUUID()}",
              "@behov": ["Avslagsdager"],
              "fødselsnummer": "$fødselsnummer",
              "Avslagsdager": {
                "vurderingIder": [${vurderingIder.joinToString { "\"$it\"" }}]
              }
            }
            """
            return behov
        }
    }
}
